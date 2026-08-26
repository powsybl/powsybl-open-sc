/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.util.AdmittanceEquationSystem;
import com.powsybl.sc.util.CalculationLocation;
import com.powsybl.sc.util.ImpedanceLinearResolution;
import com.powsybl.sc.util.ImpedanceLinearResolutionParameters;
import org.apache.commons.math3.complex.Complex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitBalancedEngine extends AbstractShortCircuitEngine {

    public ShortCircuitBalancedEngine(Network network, ShortCircuitEngineParameters parameters) {
        super(network, parameters);
    }

    @Override
    public void run() { //can handle both selective and systematic analysis with one single matrix inversion
        LfNetwork lfNetwork = lfNetworks.getFirst();

        // building a contingency list with all voltage levels
        if (parameters.getAnalysisType() == ShortCircuitEngineParameters.AnalysisType.SYSTEMATIC) {
            buildSystematicList(ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        }

        solverFaultList = buildFaultListsFromInputs().getKey();
        List<CalculationLocation> solverLocationList = solverFaultList.stream().map(ShortCircuitFault::getCalculationLocation).toList();

        ImpedanceLinearResolutionParameters linearResolutionParameters = new ImpedanceLinearResolutionParameters(acLoadFlowParameters,
                parameters.getMatrixFactory(), solverLocationList, parameters.isVoltageUpdate(), getAdmittanceVoltageProfileTypeFromParam(),
                getAdmittancePeriodTypeFromParam(), AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN, parameters.isIgnoreShunts());

        ImpedanceLinearResolution directResolution = new ImpedanceLinearResolution(lfNetwork, linearResolutionParameters);

        directResolution.run();

        //Build the ShortCircuit results using the Thevenin computation results
        resultsPerFault.clear();
        solverFaultList.forEach(fault -> {
            switch (fault.getShortCircuitFaultType()) {
                case BUS -> processBusShortCircuitFaults(fault, lfNetwork, directResolution);
                case BRANCH -> processBranchShortCircuitFaults(fault, lfNetwork, directResolution);
            }
        });

    }

    /**
     * Computes Id = Eth / (Zth + Zf) for a fault.
     */
    private static Complex computeFaultCurrent(Complex vInit, Complex zth, Complex zf) {
        return vInit.divide(zf.add(zth));
    }

    /**
     * Applies the post-fault voltage profile update on the result, if enabled in the parameters.
     * busToZknf maps each bus number to the Zknf contribution used to derive its delta-V:
     * deltaV(bus) = -Id * zknf(bus)
     */
    private void updateVoltageProfileIfNeeded(ShortCircuitResult res, LfNetwork lfNetwork, Complex id, Map<Integer, Complex> busToZknf) {
        if (!parameters.isVoltageUpdate()) {
            return;
        }
        res.setLfNetwork(lfNetwork);
        res.setTrueVoltageProfileUpdate();

        int nbBusses = lfNetwork.getBuses().size();
        res.createEmptyFortescueVoltageVector(nbBusses);

        for (Map.Entry<Integer, Complex> zd : busToZknf.entrySet()) {
            Complex deltaV = zd.getValue().multiply(id).multiply(-1.);
            res.fillVoltageInFortescueVector(zd.getKey(), deltaV);
        }
    }

    protected void processBusShortCircuitFaults(ShortCircuitFault shortCircuitFault, LfNetwork lfNetwork, ImpedanceLinearResolution directResolution) {
        String busId = shortCircuitFault.getCalculationLocation().getLfBusInfo();

        LfBus lfBus = lfNetwork.getBusById(busId);
        if (lfBus == null) {
            throw new IllegalStateException("Bus not found: " + busId);
        }

        ImpedanceLinearResolution.ImpedanceLinearResolutionResult linearResolutionResult = directResolution.results.get(lfBus);
        if (linearResolutionResult == null) {
            throw new IllegalStateException("No impedance resolution result found for bus: " + busId);
        }

        // For each contingency that matches the given bus of the linear resolution we compute:
        // If = Eth / (Zth + Zf) gives:

        // values that does not change for a given bus in input
        Complex vInit = linearResolutionResult.getEth();
        Complex zth = linearResolutionResult.getZthEq();

        Complex zfToGround = shortCircuitFault.getZf().getZg();
        Complex id = computeFaultCurrent(vInit, zth, zfToGround);

        // The post-fault voltage values at faulted bus are computed as follow :
        // [Vk_r] = [Vk_r_init] - i_nf_r * [zknf_r] + i_nf_i * [zknf_i]
        // [Vk_i] = [Vk_i_init] - i_nf_r * [zknf_i] - i_nf_i * [zknf_Vr]
        Complex zknf = linearResolutionResult.getZknf();
        Complex dv = id.multiply(zknf).multiply(-1.);
        Complex zth20hz = linearResolutionResult.getZthEq20Hz();

        ShortCircuitResult res = new ShortCircuitResult(shortCircuitFault, lfBus, id, zth, vInit, dv,
                linearResolutionResult.getEqSysFeeders(), parameters.getNorm(), zth20hz);

        updateVoltageProfileIfNeeded(res, lfNetwork, id, linearResolutionResult.getBusToZknf());

        res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made
        resultsPerFault.put(shortCircuitFault, res);
    }

    protected void processBranchShortCircuitFaults(ShortCircuitFault shortCircuitFault, LfNetwork lfNetwork, ImpedanceLinearResolution directResolution) {
        String bus1Id = shortCircuitFault.getCalculationLocation().getLfBusInfo();
        String bus2Id = shortCircuitFault.getCalculationLocation().getLfBus2Info();
        LfBranch lfLine = lfNetwork.getBranchById(shortCircuitFault.getElementId());

        LfBus lfBus1 = lfNetwork.getBusById(bus1Id);
        if (lfBus1 == null) {
            throw new IllegalStateException("Bus not found: " + bus1Id);
        }

        ImpedanceLinearResolution.ImpedanceLinearResolutionResult linearResolutionResult1 = directResolution.results.get(lfBus1);
        if (linearResolutionResult1 == null) {
            throw new IllegalStateException("No impedance resolution result found for bus: " + bus1Id);
        }

        LfBus lfBus2 = lfNetwork.getBusById(bus2Id);
        if (lfBus2 == null) {
            throw new IllegalStateException("Bus not found: " + bus2Id);
        }
        Integer busNum2 = lfBus2.getNum();
        ImpedanceLinearResolution.ImpedanceLinearResolutionResult linearResolutionResult2 = directResolution.results.get(lfBus2);
        if (linearResolutionResult2 == null) {
            throw new IllegalStateException("No impedance resolution result found for bus: " + bus2Id);
        }

        Complex vInit1 = linearResolutionResult1.getEth();
        Complex vInit2 = linearResolutionResult2.getEth();

        Complex zthBus1 = linearResolutionResult1.getZthEq();
        Complex zthBus2 = linearResolutionResult2.getZthEq();
        Complex zthBus1Bus2 = linearResolutionResult1.getBusToZknf().get(busNum2);
        Complex zLine = new Complex(lfLine.getPiModel().getR(), lfLine.getPiModel().getX());

        double r = shortCircuitFault.getCalculationLocation().getProportionalLocationOnLine() / 100.0;
        double s = 1 - r;

        // r: proportionFromBus1, s: proportionFromBus2
        // vInit = vInit1 * r + vInit2 * s
        Complex vInit = vInit1.multiply(r).add(vInit2.multiply(s));
        // Zth = Z11*s*s + Z22*r*r + 2*Z12*r*s + ZLine*r*s
        Complex zth = zthBus1.multiply(s * s)
                .add(zthBus2.multiply(r * r))
                .add(zthBus1Bus2.multiply(2 * r * s))
                .add(zLine.multiply(r * s));

        Complex zfToGround = shortCircuitFault.getZf().getZg();
        Complex id = computeFaultCurrent(vInit, zth, zfToGround);

        // The post-fault voltage values at faulted bus are computed as follow :
        // [Vk_r] = [Vk_r_init] - i_nf_r * [zknf_r] + i_nf_i * [zknf_i]
        // [Vk_i] = [Vk_i_init] - i_nf_r * [zknf_i] - i_nf_i * [zknf_Vr]
        Complex zknf1 = linearResolutionResult1.getZknf();
        Complex zknf2 = linearResolutionResult2.getZknf();
        // dv = -(s*Zq1 + r*Zq2)*Id
        Complex dv = zknf1.multiply(s)
                .add(zknf2.multiply(r))
                .multiply(id.negate());

        Complex zth20hzBus1 = linearResolutionResult1.getZthEq20Hz();
        Complex zth20hzBus2 = linearResolutionResult2.getZthEq20Hz();
        Complex zth20hzBus1Bus2 = linearResolutionResult1.getBusToZknf().get(busNum2); // Fixme: get zth20hzBus1Bus2?
        Complex zLine20hz = new Complex(lfLine.getPiModel().getR(), lfLine.getPiModel().getX()); // Fixme get zLine20hz?
        Complex zth20hz = zth20hzBus1.multiply(s * s)
                .add(zth20hzBus2.multiply(r * r))
                .add(zth20hzBus1Bus2.multiply(2 * r * s))
                .add(zLine20hz.multiply(r * s));

        ShortCircuitResult res = new ShortCircuitResult(shortCircuitFault, lfBus1, id, zth, vInit, dv,
                linearResolutionResult1.getEqSysFeeders(), parameters.getNorm(), zth20hz);

        Map<Integer, Complex> combinedBusToZknf = null;
        if (parameters.isVoltageUpdate()) {
            int nbBusses = lfNetwork.getBuses().size();
            combinedBusToZknf = new HashMap<>();
            for (int busNum = 0; busNum < nbBusses; busNum++) {
                Complex zdBus1 = linearResolutionResult1.getBusToZknf().get(busNum);
                Complex zdBus2 = linearResolutionResult2.getBusToZknf().get(busNum);
                combinedBusToZknf.put(busNum, zdBus1.multiply(s).add(zdBus2.multiply(r)));
            }
        }
        updateVoltageProfileIfNeeded(res, lfNetwork, id, combinedBusToZknf);

        res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made
        resultsPerFault.put(shortCircuitFault, res);
    }
}
