/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.util.AdmittanceEquationSystem;
import com.powsybl.sc.util.CalculationLocation;
import com.powsybl.sc.util.ImpedanceLinearResolution;
import com.powsybl.sc.util.ImpedanceLinearResolutionParameters;
import org.apache.commons.math3.complex.Complex;

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
        LfNetwork lfNetwork = lfNetworks.get(0);

        // building a contingency list with all voltage levels
        if (parameters.getAnalysisType() == ShortCircuitEngineParameters.AnalysisType.SYSTEMATIC) {
            buildSystematicList(ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        }

        solverFaultList = buildFaultListsFromInputs().getKey();

        ImpedanceLinearResolutionParameters linearResolutionParameters = new ImpedanceLinearResolutionParameters(acLoadFlowParameters,
                parameters.getMatrixFactory(), solverFaultList, parameters.isVoltageUpdate(), getAdmittanceVoltageProfileTypeFromParam(),
                getAdmittancePeriodTypeFromParam(), AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN, parameters.isIgnoreShunts());

        ImpedanceLinearResolution directResolution = new ImpedanceLinearResolution(lfNetwork, linearResolutionParameters);

        directResolution.run();

        //Build the ShortCircuit results using the Thevenin computation results
        resultsPerFault.clear();
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution);

    }

    protected void processAdmittanceLinearResolutionResults(LfNetwork lfNetwork, ImpedanceLinearResolution directResolution) {

        for (ImpedanceLinearResolution.ImpedanceLinearResolutionResult linearResolutionResult : directResolution.results) {
            LfBus bus = linearResolutionResult.getBus();

            // For each contingency that matches the given bus of the linear resolution we compute:
            // If = Eth / (Zth + Zf) gives:

            // values that does not change for a given bus in input
            Complex vInit = linearResolutionResult.getEth();
            Complex zth = linearResolutionResult.getZthEq(); //new Complex(linearResolutionResult.getRthz11(), linearResolutionResult.getXthz12());

            for (CalculationLocation calculationLocation : solverFaultList) {
                ShortCircuitFault scfe = (ShortCircuitFault) calculationLocation;
                ShortCircuitFault scf = null;
                if (bus.getId().equals(scfe.getLfBusInfo())) {
                    scf = scfe;
                }

                if (scf == null) {
                    continue;
                }

                Complex zf = scf.getZf();
                Complex ztotal = zf.add(zth);

                Complex id = vInit.divide(ztotal);
                // The post-fault voltage values at faulted bus are computed as follow :
                // [Vk_r] = [Vk_r_init] - i_nf_r * [zknf_r] + i_nf_i * [zknf_i]
                // [Vk_i] = [Vk_i_init] - i_nf_r * [zknf_i] - i_nf_i * [zknf_Vr]
                Complex zknf = linearResolutionResult.getZknf();
                Complex dv = id.multiply(zknf).multiply(-1.);

                ShortCircuitResult res = new ShortCircuitResult(scf, bus, id, zth, vInit, dv, linearResolutionResult.getEqSysFeeders(), parameters.getNorm());
                if (parameters.isVoltageUpdate()) {
                    //we get the lfNetwork to process the results
                    res.setLfNetwork(lfNetwork);

                    res.setTrueVoltageProfileUpdate();

                    // we compute the delta values to be added to Vinit if we want the post-fault voltage :
                    // [dVk_r] = [Vk_r] - [Vk_r_init] = - i_nf_r * [zknf_r] + i_nf_i * [zknf_i]
                    // [dVk_i] = [Vk_i] - [Vk_i_init] = - i_nf_r * [zknf_i] - i_nf_i * [zknf_Vr]
                    int nbBusses = lfNetwork.getBuses().size();
                    res.createEmptyFortescueVoltageVector(nbBusses);

                    for (Map.Entry<Integer, Complex> zd : linearResolutionResult.getBusToZknf().entrySet()) {
                        int busNum = zd.getKey();
                        Complex zdBus = zd.getValue();
                        Complex deltaV = zdBus.multiply(id).multiply(-1.);
                        res.fillVoltageInFortescueVector(busNum, deltaV);
                    }
                }

                res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made
                resultsPerFault.put(scf, res);
            }
        }
    }
}
