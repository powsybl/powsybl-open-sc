/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.util.AdmittanceEquationSystem;
import com.powsybl.sc.util.CalculationLocation;
import com.powsybl.sc.util.ImpedanceLinearResolution;
import com.powsybl.sc.util.ImpedanceLinearResolutionParameters;

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
            //
            //        ethi*(xf+xth) + ethr*(rf+rth) + j*[ethi*(rf+rth) - ethr*(xf+xth)]
            // If = --------------------------------------------------------------------
            //                          (rf+rth)² + (xf+xth)²
            //

            // values that does not change for a given bus in input
            double vxInit = linearResolutionResult.getEthr();
            double vyInit = linearResolutionResult.getEthi();

            double rth = linearResolutionResult.getRthz11();
            double xth = linearResolutionResult.getXthz12();

            for (CalculationLocation calculationLocation : solverFaultList) {
                ShortCircuitFault scfe = (ShortCircuitFault) calculationLocation;
                ShortCircuitFault scf = null;
                if (bus.getId().equals(scfe.getLfBusInfo())) {
                    scf = scfe;
                }

                if (scf == null) {
                    continue;
                }

                double rf = scf.getZfr();
                double xf = scf.getZfi();

                double denom = (rf + rth) * (rf + rth) + (xf + xth) * (xf + xth);
                double ifr = (vyInit * (xf + xth) + vxInit * (rf + rth)) / denom;
                double ifi = (vyInit * (rf + rth) - vxInit * (xf + xth)) / denom;
                // The post-fault voltage values at faulted bus are computed as follow :
                // [Vr] = [Vr_init] - ifr * [e_dVr] + ifi * [e_dVi]
                // [Vi] = [Vi_init] - ifr * [e_dVi] - ifi * [e_dVr]
                double dvr = -ifr * linearResolutionResult.getEnBus().get(0, 0) + ifi * linearResolutionResult.getEnBus().get(1, 0);
                double dvi = -ifr * linearResolutionResult.getEnBus().get(1, 0) - ifi * linearResolutionResult.getEnBus().get(0, 0);

                ShortCircuitResult res = new ShortCircuitResult(scf, bus, ifr, ifi, rth, xth, vxInit, vyInit, dvr, dvi, linearResolutionResult.getEqSysFeeders(), parameters.getNorm());
                if (parameters.isVoltageUpdate()) {
                    //we get the lfNetwork to process the results
                    res.setLfNetwork(lfNetwork);

                    res.setTrueVoltageProfileUpdate();
                    // The post-fault voltage values are computed as follow :
                    // [Vr] = [Vr_init] - ifr * [e_dVr] + ifi * [e_dVi]
                    // [Vi] = [Vi_init] - ifr * [e_dVi] - ifi * [e_dVr]
                    // we compute the delta values to be added to Vinit if we want the post-fault voltage :
                    int nbBusses = lfNetwork.getBuses().size();
                    res.createEmptyFortescueVoltageVector(nbBusses);

                    for (Map.Entry<Integer, DenseMatrix> vd : linearResolutionResult.getDv().entrySet()) {
                        int busNum = vd.getKey();
                        double edVr = vd.getValue().get(0, 0);
                        double edVi = vd.getValue().get(1, 0);

                        double deltaVr = -ifr * edVr + ifi * edVi;
                        double deltaVi = -ifr * edVi - ifi * edVr;

                        res.fillVoltageInFortescueVector(busNum, deltaVr, deltaVi);
                    }
                }

                res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made
                resultsPerFault.put(scf, res);
            }
        }
    }
}
