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
import com.powsybl.sc.extensions.ScFortescueValue;
import com.powsybl.sc.extensions.ShortCircuitAnalysisType;
import com.powsybl.sc.util.*;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.powsybl.sc.util.ShortCircuitHelper.fillInitialVoltages;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitUnbalancedEngine extends AbstractShortCircuitEngine {

    public ShortCircuitUnbalancedEngine(Network network, ShortCircuitParameters shortCircuitParameters,
                                        List<ShortCircuitFault> shortCircuitFaults, ShortCircuitNorm shortCircuitNorm) {
        super(network, shortCircuitParameters, shortCircuitFaults, shortCircuitNorm);
    }

    @Override
    public void run() {
        initialVoltages = fillInitialVoltages(lfNetwork, shortCircuitParameters);

        if (openShortCircuitParameters.getShortCircuitAnalysisType() == ShortCircuitAnalysisType.SYSTEMATIC) {
            buildSystematicList(ShortCircuitFault.ShortCircuitType.MONOPHASED); // TODO : by default it is monophased, could be changed to choose type of systematic default
            // Biphased common support faults will not be supported yet in systematic
        }

        // We handle a pre-treatement of faults given in input:
        // - filtering of some inconsistencies on the bus identification
        // - addition of info in each fault to ease the identification in LfNetwork of iidm info
        buildFaultListsFromInputs();
        List<CalculationLocation> solverLocationList = shortCircuitFaults.stream().map(ShortCircuitFault::getCalculationLocation).toList();

        ImpedanceLinearResolution directResolution = new ImpedanceLinearResolution(
                network,
                lfNetwork,
                solverLocationList,
                initialVoltages,
                AdmittanceType.ADM_THEVENIN,
                shortCircuitParameters);
        ImpedanceLinearResolution homopolarResolution = new ImpedanceLinearResolution(
                network,
                lfNetwork,
                solverLocationList,
                initialVoltages,
                AdmittanceType.ADM_THEVENIN_HOMOPOLAR,
                shortCircuitParameters);

        directResolution.run();
        homopolarResolution.run();

        //Build the ShortCircuit results using the linear resolution computation results
        resultsPerFault.clear();
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.BIPHASED);
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT);
    }

    public void processAdmittanceLinearResolutionResults(LfNetwork lfNetwork, ImpedanceLinearResolution directResolution,
                                                         ImpedanceLinearResolution homopolarResolution, ShortCircuitFault.ShortCircuitType shortCircuitType) {

        for (Map.Entry<LfBus, ImpedanceLinearResolution.ImpedanceLinearResolutionResult> entry
                : directResolution.results.entrySet()) {

            LfBus lfBus = entry.getKey();
            ImpedanceLinearResolution.ImpedanceLinearResolutionResult directResult = entry.getValue();

            ImpedanceLinearResolution.ImpedanceLinearResolutionResult homopolarResult =
                    homopolarResolution.results.get(lfBus);
            LfBus lfBus1 = directResult.getBus();

            List<ShortCircuitFault> matchingFaultsAtBus1 = new ArrayList<>(); //We build a list of all faults with bus1 matching with bus1 of ImpedanceLinearResolutionResult

            for (ShortCircuitFault scfe : shortCircuitFaults) {
                if (lfBus1.getId().equals(scfe.getCalculationLocation().getLfBusInfo()) && scfe.getType() == shortCircuitType) {
                    matchingFaultsAtBus1.add(scfe);
                }
            }

            Complex v1dInit = directResult.getEth();
            Complex zdf = directResult.getZthEq();
            Complex zof = homopolarResult.getZthEq();
            ScFortescueValue zFortescue = new ScFortescueValue(zdf, zdf, zof);

            for (ShortCircuitFault scf : matchingFaultsAtBus1) {

                ScFortescueValue iFortescue;

                ShortCircuitResult res;

                if (shortCircuitType == ShortCircuitFault.ShortCircuitType.MONOPHASED
                        || shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED
                        || shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND) {
                    if (shortCircuitType == ShortCircuitFault.ShortCircuitType.MONOPHASED) {
                        MonophasedShortCircuitCalculator monophasedCalculator = new MonophasedShortCircuitCalculator(zdf, zof, scf.getZf(), v1dInit);
                        monophasedCalculator.computeCurrents();

                        iFortescue = monophasedCalculator.getIFortescue();

                    } else if (shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED) {
                        BiphasedShortCircuitCalculator biphasedCalculator = new BiphasedShortCircuitCalculator(zdf, zof, scf.getZf(), v1dInit);
                        biphasedCalculator.computeCurrents();

                        iFortescue = biphasedCalculator.getIFortescue();
                    } else {
                        BiphasedGroundShortCircuitCalculator biphasedGrCalculator = new BiphasedGroundShortCircuitCalculator(zdf, zof, scf.getZf(), v1dInit);
                        biphasedGrCalculator.computeCurrents();

                        iFortescue = biphasedGrCalculator.getIFortescue();
                    }

                    res = buildUnbalancedResult(iFortescue, zFortescue,
                            directResult, homopolarResult,
                            scf, lfBus1, v1dInit, lfNetwork);
                    // feeders are updated only if voltageUpdate is made. TODO : see if update of homopolar feeders are to be updated
                    res.updateFeedersResult(shortCircuitParameters, openShortCircuitParameters);
                    resultsPerFault.put(scf, res);

                } else if (shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT) {

                    int numBiphasedResult = 0;
                    TwoBusImpedanceLinearResolutionResult biphasedHomopolarResult;
                    for (TwoBusImpedanceLinearResolutionResult biphasedDirectResult : directResult.getTwoBusResults()) {
                        biphasedHomopolarResult = homopolarResult.getTwoBusResults().get(numBiphasedResult);
                        numBiphasedResult++;

                        LfBus lfBus2 = biphasedDirectResult.getBus2();
                        if (lfBus2.getId().equals(scf.getCalculationLocation().getLfBus2Info())) {

                            Complex zo12 = biphasedHomopolarResult.getZ12();
                            Complex zo22 = biphasedHomopolarResult.getZ22();
                            Complex zo21 = biphasedHomopolarResult.getZ21();

                            Complex zd12 = biphasedDirectResult.getZ12();
                            Complex zd22 = biphasedDirectResult.getZ22();
                            Complex zd21 = biphasedDirectResult.getZ21();

                            BiphasedCommonSupportShortCircuitCalculator biCsSc;
                            if (scf.getBiphasedType() == ShortCircuitFault.ShortCircuitBiphasedType.C1_A2) {
                                biCsSc = new BiphasedC1A2Calculator(zdf, zof, scf.getZf(), v1dInit, biphasedDirectResult.getV2(),
                                        zo12, zo22, zo21,
                                        zd12, zd22, zd21);
                            } else if (scf.getBiphasedType() == ShortCircuitFault.ShortCircuitBiphasedType.C1_B2) {
                                biCsSc = new BiphasedC1B2Calculator(zdf, zof, scf.getZf(), v1dInit, biphasedDirectResult.getV2(),
                                        zo12, zo22, zo21,
                                        zd12, zd22, zd21);
                            } else if (scf.getBiphasedType() == ShortCircuitFault.ShortCircuitBiphasedType.C1_C2) {
                                biCsSc = new BiphasedC1C2Calculator(zdf, zof, scf.getZf(), v1dInit, biphasedDirectResult.getV2(),
                                        zo12, zo22, zo21,
                                        zd12, zd22, zd21);
                            } else {
                                throw new IllegalArgumentException(" short circuit fault of type : " + scf.getBiphasedType() + " not yet handled");
                            }

                            Complex v2dInit = biphasedDirectResult.getV2();

                            iFortescue = new ScFortescueValue(biCsSc.getId(), biCsSc.getIi(), biCsSc.getIo());
                            ScFortescueValue i2Fortescue = new ScFortescueValue(biCsSc.getI2d(), biCsSc.getI2i(), biCsSc.getI2o());
                            ScFortescueValue dvFortescue = new ScFortescueValue(biCsSc.getVd(), biCsSc.getVi(), biCsSc.getVo());
                            ScFortescueValue dv2Fortescue = new ScFortescueValue(biCsSc.getV2d(), biCsSc.getV2i(), biCsSc.getV2o());

                            res = buildUnbalancedCommunSuppportResult(iFortescue,
                                    i2Fortescue,
                                    dvFortescue,
                                    dv2Fortescue,
                                    zFortescue,
                                    directResult, homopolarResult, scf,
                                    lfBus1, v1dInit, lfNetwork,
                                    lfBus2, v2dInit, biphasedDirectResult, biphasedHomopolarResult);
                            // feeders are updated only if voltageUpdate is made. TODO : see if update of homopolar feeders are to be updated
                            res.updateFeedersResult(shortCircuitParameters, openShortCircuitParameters);
                            resultsPerFault.put(scf, res);

                        } else {
                            throw new IllegalArgumentException(" Post-processing of short circuit type = " + shortCircuitType + "not yet implemented");
                        }
                    }
                }
            }
        }
    }

    public ShortCircuitResult buildUnbalancedResult(ScFortescueValue iFortescue, ScFortescueValue zFortescue,
                                                    ImpedanceLinearResolution.ImpedanceLinearResolutionResult directResult,
                                                    ImpedanceLinearResolution.ImpedanceLinearResolutionResult homopolarResult,
                                                    ShortCircuitFault scf, LfBus lfBus1, Complex v1dInit,
                                                    LfNetwork lfNetwork) {
        //get the voltage vectors
        // Vo :
        // [vox]      [ rof  -xof ]   [ iox ]
        // [voy] = -  [ xof   rof ] * [ ioy ]

        ScFortescueValue dvFortescue = zFortescue.multiply(iFortescue).multiply(-1.);

        //record the results
        FeedersAtNetwork equationSystemFeedersDirect = directResult.getEqSysFeeders();
        FeedersAtNetwork equationSystemFeedersHomopolar = homopolarResult.getEqSysFeeders();

        ShortCircuitResult res = new ShortCircuitResult(scf, lfBus1,
                iFortescue,
                zFortescue,
                v1dInit, dvFortescue,
                equationSystemFeedersDirect, equationSystemFeedersHomopolar, shortCircuitNorm,
                directResult.getZthEq20Hz(), homopolarResult.getZthEq20Hz());

        if (shortCircuitParameters.isWithVoltageResult()) {
            res.setLfNetwork(lfNetwork);
            res.setTrueVoltageProfileUpdate();
            // The post-fault voltage values for the network buses are computed as follow :
            // [ Vof ] = -inv(Yo) * M * [ Iof ]
            // [ Vdf ] = -inv(Yd) * M * [ Idf ] + [ V(init) ]
            // [ Vif ] = -inv(Yd) * M * [ Iif ]
            // dMo = inv(Yo) * M
            // dMd = inv(Yd) * M

            int nbBuses = lfNetwork.getBuses().size();
            res.createEmptyFortescueVoltageVector(nbBuses);

            for (Map.Entry<Integer, Complex> zd : directResult.getBusToZknf().entrySet()) {
                int busNum = zd.getKey();

                Complex zdBus = zd.getValue();
                Complex zo = homopolarResult.getBusToZknf().get(busNum);
                //System.out.println(" dVth(" + vdr.getKey() + ") = " + edVr + " + j(" + edVi + ")");
                ScFortescueValue zBusFortescue = new ScFortescueValue(zdBus, zdBus, zo);
                ScFortescueValue deltaVFort = zBusFortescue.multiply(iFortescue).multiply(-1);

                res.fillVoltageInFortescueVector(busNum, deltaVFort);
            }
        }

        return res;
    }

    public ShortCircuitResult buildUnbalancedCommunSuppportResult(ScFortescueValue iFortescue, ScFortescueValue i2Fortescue,
                                                                  ScFortescueValue dvFortescue, ScFortescueValue dv2Fortescue, ScFortescueValue zFortescue,
                                                                  ImpedanceLinearResolution.ImpedanceLinearResolutionResult directResult,
                                                                  ImpedanceLinearResolution.ImpedanceLinearResolutionResult homopolarResult, ShortCircuitFault scf,
                                                                  LfBus lfBus1, Complex v1dInit, LfNetwork lfNetwork,
                                                                  LfBus lfBus2, Complex v2dInit,
                                                                  TwoBusImpedanceLinearResolutionResult biphasedDirectResult,
                                                                  TwoBusImpedanceLinearResolutionResult biphasedHomopolarResult) {

        //record the results
        FeedersAtNetwork equationSystemFeedersDirect = directResult.getEqSysFeeders();
        FeedersAtNetwork equationSystemFeedersHomopolar = homopolarResult.getEqSysFeeders();

        ShortCircuitResult res = new ShortCircuitResult(scf, lfBus1,
                iFortescue,
                zFortescue,
                v1dInit,
                dvFortescue,
                equationSystemFeedersDirect, equationSystemFeedersHomopolar, shortCircuitNorm,
                i2Fortescue,
                v2dInit,
                dv2Fortescue,
                lfBus2, directResult.getZthEq20Hz(), homopolarResult.getZthEq20Hz());

        if (shortCircuitParameters.isWithVoltageResult()) {
            res.setLfNetwork(lfNetwork);
            res.setTrueVoltageProfileUpdate();
            // The post-fault voltage values for the network buses are computed as follow :
            // [ Vof ] = -inv(Yo) * M * [ Iof ]
            // [ Vdf ] = -inv(Yd) * M * [ Idf ] + [ V(init) ]
            // [ Vif ] = -inv(Yd) * M * [ Iif ]
            // dMo = inv(Yo) * M
            // dMd = inv(Yd) * M

            int nbBuses = lfNetwork.getBuses().size();
            res.createEmptyFortescueVoltageVector(nbBuses);

            for (Map.Entry<Integer, Complex> zd : directResult.getBusToZknf().entrySet()) {
                int busNum = zd.getKey();

                //direct
                Complex zdBus2 = biphasedDirectResult.getBus2ToZknf().get(busNum);
                Complex zoBus = homopolarResult.getBusToZknf().get(busNum);
                Complex zoBus2 = biphasedHomopolarResult.getBus2ToZknf().get(busNum);

                ScFortescueValue zBusFortescue = new ScFortescueValue(zd.getValue(), zd.getValue(), zoBus);
                ScFortescueValue zBus2Fortescue = new ScFortescueValue(zdBus2, zdBus2, zoBus2);
                ScFortescueValue deltaVFort = iFortescue.multiply(zFortescue).add(i2Fortescue.multiply(zBus2Fortescue)).multiply(-1);

                res.fillVoltageInFortescueVector(busNum, deltaVFort);
            }
        }

        return res;
    }
}
