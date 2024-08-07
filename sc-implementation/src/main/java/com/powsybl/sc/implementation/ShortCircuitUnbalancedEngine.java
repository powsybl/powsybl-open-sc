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
import com.powsybl.sc.util.*;
import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitUnbalancedEngine extends AbstractShortCircuitEngine {

    public ShortCircuitUnbalancedEngine(Network network, ShortCircuitEngineParameters parameters) {
        super(network, parameters);
    }

    @Override
    public void run() {
        LfNetwork lfNetwork = lfNetworks.get(0);

        if (parameters.getAnalysisType() == ShortCircuitEngineParameters.AnalysisType.SYSTEMATIC) {
            buildSystematicList(ShortCircuitFault.ShortCircuitType.MONOPHASED); // TODO : by default it is monophased, could be changed to choose type of systematic default
            // Biphased common support faults will not be supported yet in systematic
        }

        // We handle a pre-treatement of faults given in input:
        // - filtering of some inconsistencies on the bus identification
        // - addition of info in each fault to ease the identification in LfNetwork of iidm info
        Pair<List<CalculationLocation>, List<CalculationLocation>> faultLists = buildFaultListsFromInputs();

        solverFaultList = faultLists.getKey();
        solverBiphasedFaultList = faultLists.getValue();

        ImpedanceLinearResolutionParameters admittanceLinearResolutionParametersHomopolar = new ImpedanceLinearResolutionParameters(acLoadFlowParameters,
                parameters.getMatrixFactory(), solverFaultList, parameters.isVoltageUpdate(),
                getAdmittanceVoltageProfileTypeFromParam(), getAdmittancePeriodTypeFromParam(), AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN_HOMOPOLAR,
                parameters.isIgnoreShunts(), solverBiphasedFaultList);

        ImpedanceLinearResolutionParameters admittanceLinearResolutionParametersDirect = new ImpedanceLinearResolutionParameters(acLoadFlowParameters,
                parameters.getMatrixFactory(), solverFaultList, parameters.isVoltageUpdate(),
                getAdmittanceVoltageProfileTypeFromParam(), getAdmittancePeriodTypeFromParam(), AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN,
                parameters.isIgnoreShunts(), solverBiphasedFaultList);

        ImpedanceLinearResolution directResolution = new ImpedanceLinearResolution(lfNetwork, admittanceLinearResolutionParametersDirect);
        ImpedanceLinearResolution homopolarResolution = new ImpedanceLinearResolution(lfNetwork, admittanceLinearResolutionParametersHomopolar);

        directResolution.run();
        homopolarResolution.run();

        //Build the ShortCircuit results using the linear resolution computation results
        resultsPerFault.clear();
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.MONOPHASED);
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.BIPHASED);
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
        processAdmittanceLinearResolutionResults(lfNetwork, directResolution, homopolarResolution, ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT);
    }

    public void processAdmittanceLinearResolutionResults(LfNetwork lfNetwork, ImpedanceLinearResolution directResolution, ImpedanceLinearResolution homopolarResolution, ShortCircuitFault.ShortCircuitType shortCircuitType) {

        int numResult = 0;
        for (ImpedanceLinearResolution.ImpedanceLinearResolutionResult directResult : directResolution.results) {

            ImpedanceLinearResolution.ImpedanceLinearResolutionResult homopolarResult = homopolarResolution.results.get(numResult);
            numResult++;

            LfBus lfBus1 = directResult.getBus();

            List<ShortCircuitFault> matchingFaultsAtBus1 = new ArrayList<>(); //We build a list of all faults with bus1 matching with bus1 of ImpedanceLinearResolutionResult

            if (shortCircuitType == ShortCircuitFault.ShortCircuitType.MONOPHASED
                    || shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED
                    || shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND) {
                for (CalculationLocation calculationLocation : solverFaultList) {
                    ShortCircuitFault scfe = (ShortCircuitFault) calculationLocation;
                    if (lfBus1.getId().equals(scfe.getLfBusInfo()) && scfe.getType() == shortCircuitType) {
                        matchingFaultsAtBus1.add(scfe);
                    }
                }
            }

            if (shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT) {
                for (CalculationLocation calculationLocation : solverBiphasedFaultList) {
                    ShortCircuitFault scfe = (ShortCircuitFault) calculationLocation;
                    if (lfBus1.getId().equals(scfe.getLfBusInfo()) && scfe.getType() == shortCircuitType) {
                        matchingFaultsAtBus1.add(scfe);
                    }
                }
            }

            double v1dxInit = directResult.getEthr();
            double v1dyInit = directResult.getEthi();

            double rdf = directResult.getRthz11();
            double xdf = directResult.getXthz12();

            double rof = homopolarResult.getRthz11();
            double xof = homopolarResult.getXthz12();

            for (ShortCircuitFault scf : matchingFaultsAtBus1) {

                double rf = scf.getZfr();
                double xf = scf.getZfi();

                DenseMatrix mIo = new DenseMatrix(2, 1);
                DenseMatrix mId = new DenseMatrix(2, 1);
                DenseMatrix mIi = new DenseMatrix(2, 1);

                ShortCircuitResult res;

                if (shortCircuitType == ShortCircuitFault.ShortCircuitType.MONOPHASED
                        || shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED
                        || shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND) {
                    if (shortCircuitType == ShortCircuitFault.ShortCircuitType.MONOPHASED) {
                        MonophasedShortCircuitCalculator monophasedCalculator = new MonophasedShortCircuitCalculator(rdf, xdf, rof, xof, rf, xf, v1dxInit, v1dyInit);
                        monophasedCalculator.computeCurrents();

                        mIo = monophasedCalculator.getmIo();
                        mId = monophasedCalculator.getmId();
                        mIi = monophasedCalculator.getmIi();

                    } else if (shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED) {
                        BiphasedShortCircuitCalculator biphasedCalculator = new BiphasedShortCircuitCalculator(rdf, xdf, rof, xof, rf, xf, v1dxInit, v1dyInit);
                        biphasedCalculator.computeCurrents();

                        mIo = biphasedCalculator.getmIo();
                        mId = biphasedCalculator.getmId();
                        mIi = biphasedCalculator.getmIi();
                    } else if (shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND) {
                        BiphasedGroundShortCircuitCalculator biphasedGrCalculator = new BiphasedGroundShortCircuitCalculator(rdf, xdf, rof, xof, rf, xf, v1dxInit, v1dyInit);
                        biphasedGrCalculator.computeCurrents();

                        mIo = biphasedGrCalculator.getmIo();
                        mId = biphasedGrCalculator.getmId();
                        mIi = biphasedGrCalculator.getmIi();
                    }

                    res = buildUnbalancedResult(mId, mIo, mIi, rdf, xdf, rof, xof,
                            directResult, homopolarResult,
                            scf, lfBus1, v1dxInit, v1dyInit, lfNetwork);

                    res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made. TODO : see if update of homopolar feeders are to be updated
                    resultsPerFault.put(scf, res);

                } else if (shortCircuitType == ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT) {

                    int numBiphasedResult = 0;
                    ImpedanceLinearResolution.ImpedanceLinearResolutionResult.ImpedanceLinearResolutionResultBiphased biphasedHomopolarResult;
                    for (ImpedanceLinearResolution.ImpedanceLinearResolutionResult.ImpedanceLinearResolutionResultBiphased biphasedDirectResult : directResult.getBiphasedResultsAtBus()) {
                        biphasedHomopolarResult = homopolarResult.getBiphasedResultsAtBus().get(numBiphasedResult);
                        numBiphasedResult++;

                        LfBus lfBus2 = biphasedDirectResult.getBus2();
                        if (lfBus2.getId().equals(scf.getLfBus2Info())) {

                            double ro12 = biphasedHomopolarResult.getZ12txx();
                            double xo12 = -biphasedHomopolarResult.getZ12txy();
                            double ro22 = biphasedHomopolarResult.getZ22txx();
                            double xo22 = -biphasedHomopolarResult.getZ22txy();
                            double ro21 = biphasedHomopolarResult.getZ21txx();
                            double xo21 = -biphasedHomopolarResult.getZ21txy();

                            double rd12 = biphasedDirectResult.getZ12txx();
                            double xd12 = -biphasedDirectResult.getZ12txy();
                            double rd22 = biphasedDirectResult.getZ22txx();
                            double xd22 = -biphasedDirectResult.getZ22txy();
                            double rd21 = biphasedDirectResult.getZ21txx();
                            double xd21 = -biphasedDirectResult.getZ21txy();

                            BiphasedCommonSupportShortCircuitCalculator biphasedCommonCalculator;
                            if (scf.getBiphasedType() == ShortCircuitFault.ShortCircuitBiphasedType.C1_A2) {
                                biphasedCommonCalculator = new BiphasedC1A2Calculator(rdf, xdf, rof, xof, rf, xf, v1dxInit, v1dyInit,
                                        biphasedDirectResult.getV2x(), biphasedDirectResult.getV2y(),
                                        ro12, xo12, ro22, xo22, ro21, xo21,
                                        rd12, xd12, rd22, xd22, rd21, xd21);
                            } else if (scf.getBiphasedType() == ShortCircuitFault.ShortCircuitBiphasedType.C1_B2) {
                                biphasedCommonCalculator = new BiphasedC1B2Calculator(rdf, xdf, rof, xof, rf, xf, v1dxInit, v1dyInit,
                                        biphasedDirectResult.getV2x(), biphasedDirectResult.getV2y(),
                                        ro12, xo12, ro22, xo22, ro21, xo21,
                                        rd12, xd12, rd22, xd22, rd21, xd21);
                            } else if (scf.getBiphasedType() == ShortCircuitFault.ShortCircuitBiphasedType.C1_C2) {
                                biphasedCommonCalculator = new BiphasedC1C2Calculator(rdf, xdf, rof, xof, rf, xf, v1dxInit, v1dyInit,
                                        biphasedDirectResult.getV2x(), biphasedDirectResult.getV2y(),
                                        ro12, xo12, ro22, xo22, ro21, xo21,
                                        rd12, xd12, rd22, xd22, rd21, xd21);
                            } else {
                                throw new IllegalArgumentException(" short circuit fault of type : " + scf.getBiphasedType() + " not yet handled");
                            }

                            //biphasedCommonCalculator.computeCurrents();
                            mIo = biphasedCommonCalculator.getmIo();
                            mId = biphasedCommonCalculator.getmId();
                            mIi = biphasedCommonCalculator.getmIi();

                            DenseMatrix mI2o = biphasedCommonCalculator.getmI2o();
                            DenseMatrix mI2d = biphasedCommonCalculator.getmI2d();
                            DenseMatrix mI2i = biphasedCommonCalculator.getmI2i();

                            //biphasedCommonCalculator.computeVoltages();
                            DenseMatrix mdVo = biphasedCommonCalculator.getmVo(); // Contains variations of voltages, without Vinit
                            DenseMatrix mdVd = biphasedCommonCalculator.getmVd(); // each voltage vector contains [V1x; V1y; V2x; V2y]
                            DenseMatrix mdVi = biphasedCommonCalculator.getmVi();

                            //LfBus lfBus2 = biphasedDirectResult.getBus2();

                            double v2dxInit = biphasedDirectResult.getV2x();
                            double v2dyInit = biphasedDirectResult.getV2y();

                            res = buildUnbalancedCommunSuppportResult(mId, mIo, mIi, mI2d, mI2o, mI2i, mdVd, mdVo, mdVi, rdf, xdf, rof, xof,
                                    directResult, homopolarResult, scf,
                                    lfBus1, v1dxInit, v1dyInit, lfNetwork,
                                    lfBus2, v2dxInit, v2dyInit, biphasedDirectResult, biphasedHomopolarResult);

                            res.updateFeedersResult(); // feeders are updated only if voltageUpdate is made. TODO : see if update of homopolar feeders are to be updated
                            resultsPerFault.put(scf, res);

                        } else {
                            throw new IllegalArgumentException(" Post-processing of short circuit type = " + shortCircuitType + "not yet implemented");
                        }
                    }
                }
            }
        }
    }

    public ShortCircuitResult buildUnbalancedResult(DenseMatrix mId, DenseMatrix mIo, DenseMatrix mIi, double rdf, double xdf, double rof, double xof,
                                                    ImpedanceLinearResolution.ImpedanceLinearResolutionResult directResult,
                                                    ImpedanceLinearResolution.ImpedanceLinearResolutionResult homopolarResult,
                                                    ShortCircuitFault scf, LfBus lfBus1, double v1dxInit, double v1dyInit,
                                                    LfNetwork lfNetwork) {
        //get the voltage vectors
        // Vo :
        // [vox]      [ rof  -xof ]   [ iox ]
        // [voy] = -  [ xof   rof ] * [ ioy ]

        DenseMatrix zof = getZ(rof, xof);
        DenseMatrix zdf = getZ(rdf, xdf);

        DenseMatrix minusVo = zof.times(mIo).toDense();
        DenseMatrix minusVd = zdf.times(mId).toDense();
        DenseMatrix minusVi = zdf.times(mIi).toDense();

        //record the results
        FeedersAtNetwork equationSystemFeedersDirect = directResult.getEqSysFeeders();
        FeedersAtNetwork equationSystemFeedersHomopolar = homopolarResult.getEqSysFeeders();

        ShortCircuitResult res = new ShortCircuitResult(scf, lfBus1,
                mId.get(0, 0), mId.get(1, 0),
                mIo.get(0, 0), mIo.get(1, 0),
                mIi.get(0, 0), mIi.get(1, 0),
                rdf, xdf, rof, xof, rdf, xdf,
                v1dxInit, v1dyInit,
                -minusVd.get(0, 0), -minusVd.get(1, 0),
                -minusVo.get(0, 0), -minusVo.get(1, 0),
                -minusVi.get(0, 0), -minusVi.get(1, 0),
                equationSystemFeedersDirect, equationSystemFeedersHomopolar, parameters.getNorm());

        if (parameters.isVoltageUpdate()) {
            res.setLfNetwork(lfNetwork);
            res.setTrueVoltageProfileUpdate();
            // The post-fault voltage values for the network busses are computed as follow :
            // [ Vof ] = -inv(Yo) * M * [ Iof ]
            // [ Vdf ] = -inv(Yd) * M * [ Idf ] + [ V(init) ]
            // [ Vif ] = -inv(Yd) * M * [ Iif ]
            // dMo = inv(Yo) * M
            // dMd = inv(Yd) * M

            int nbBusses = lfNetwork.getBuses().size();
            res.createEmptyFortescueVoltageVector(nbBusses);

            for (Map.Entry<Integer, DenseMatrix> vd : directResult.getDv().entrySet()) {
                int busNum = vd.getKey();

                //direct
                double edVr = vd.getValue().get(0, 0);
                double edVi = vd.getValue().get(1, 0);

                double idr = -mId.get(0, 0);
                double idi = -mId.get(1, 0);

                double deltaVdr = -idr * edVr + idi * edVi;
                double deltaVdi = -idr * edVi - idi * edVr;

                //inverse
                double iir = -mIi.get(0, 0);
                double iii = -mIi.get(1, 0);

                double deltaVir = -iir * edVr + iii * edVi;
                double deltaVii = -iir * edVi - iii * edVr;

                //homopolar
                double eoVr = homopolarResult.getDv().get(busNum).get(0, 0);
                double eoVi = homopolarResult.getDv().get(busNum).get(1, 0);

                double ior = -mIo.get(0, 0);
                double ioi = -mIo.get(1, 0);

                double deltaVor = -ior * eoVr + ioi * eoVi;
                double deltaVoi = -ior * eoVi - ioi * eoVr;

                //System.out.println(" dVth(" + vdr.getKey() + ") = " + edVr + " + j(" + edVi + ")");

                res.fillVoltageInFortescueVector(busNum, deltaVdr, deltaVdi, deltaVor, deltaVoi, deltaVir, deltaVii);
            }
        }

        return res;
    }

    public ShortCircuitResult buildUnbalancedCommunSuppportResult(DenseMatrix mId, DenseMatrix mIo, DenseMatrix mIi, DenseMatrix mI2d, DenseMatrix mI2o, DenseMatrix mI2i, DenseMatrix mVd, DenseMatrix mVo, DenseMatrix mVi, double rdf, double xdf, double rof, double xof,
                                                                  ImpedanceLinearResolution.ImpedanceLinearResolutionResult directResult,
                                                                  ImpedanceLinearResolution.ImpedanceLinearResolutionResult homopolarResult, ShortCircuitFault scf,
                                                                  LfBus lfBus1, double v1dxInit, double v1dyInit, LfNetwork lfNetwork,
                                                                  LfBus lfBus2, double v2dxInit, double v2dyInit,
                                                                  ImpedanceLinearResolution.ImpedanceLinearResolutionResult.ImpedanceLinearResolutionResultBiphased biphasedDirectResult,
                                                                  ImpedanceLinearResolution.ImpedanceLinearResolutionResult.ImpedanceLinearResolutionResultBiphased biphasedHomopolarResult) {

        //record the results
        FeedersAtNetwork equationSystemFeedersDirect = directResult.getEqSysFeeders();
        FeedersAtNetwork equationSystemFeedersHomopolar = homopolarResult.getEqSysFeeders();

        ShortCircuitResult res = new ShortCircuitResult(scf, lfBus1,
                mId.toDense().get(0, 0), mId.toDense().get(1, 0),
                mIo.toDense().get(0, 0), mIo.toDense().get(1, 0),
                mIi.toDense().get(0, 0), mIi.toDense().get(1, 0),
                rdf, xdf, rof, xof, rdf, xdf,
                v1dxInit, v1dyInit,
                mVd.toDense().get(0, 0), mVd.toDense().get(1, 0),
                mVo.toDense().get(0, 0), mVo.toDense().get(1, 0),
                mVi.toDense().get(0, 0), mVi.toDense().get(1, 0),
                equationSystemFeedersDirect, equationSystemFeedersHomopolar, parameters.getNorm(),
                mI2d.toDense().get(0, 0), mI2d.toDense().get(1, 0),
                mI2o.toDense().get(0, 0), mI2o.toDense().get(1, 0),
                mI2i.toDense().get(0, 0), mI2i.toDense().get(1, 0),
                v2dxInit, v2dyInit,
                mVd.toDense().get(2, 0), mVd.toDense().get(3, 0),
                mVo.toDense().get(2, 0), mVo.toDense().get(3, 0),
                mVi.toDense().get(2, 0), mVi.toDense().get(3, 0),
                lfBus2);

        if (parameters.isVoltageUpdate()) {
            res.setLfNetwork(lfNetwork);
            res.setTrueVoltageProfileUpdate();
            // The post-fault voltage values for the network busses are computed as follow :
            // [ Vof ] = -inv(Yo) * M * [ Iof ]
            // [ Vdf ] = -inv(Yd) * M * [ Idf ] + [ V(init) ]
            // [ Vif ] = -inv(Yd) * M * [ Iif ]
            // dMo = inv(Yo) * M
            // dMd = inv(Yd) * M

            int nbBusses = lfNetwork.getBuses().size();
            res.createEmptyFortescueVoltageVector(nbBusses);

            for (Map.Entry<Integer, DenseMatrix> vd : directResult.getDv().entrySet()) {
                int busNum = vd.getKey();

                //direct
                double edVr = vd.getValue().get(0, 0);
                double edVi = vd.getValue().get(1, 0);
                double edV2r = biphasedDirectResult.getDv2().get(busNum).get(0, 0);
                double edV2i = biphasedDirectResult.getDv2().get(busNum).get(1, 0);

                double idr = -mId.toDense().get(0, 0);
                double idi = -mId.toDense().get(1, 0);
                double i2dr = -mI2d.toDense().get(0, 0);
                double i2di = -mI2d.toDense().get(1, 0);

                double deltaVdr = -idr * edVr + idi * edVi - i2dr * edV2r + i2di * edV2i;
                double deltaVdi = -idr * edVi - idi * edVr - i2dr * edV2i - i2di * edV2r;

                //inverse
                double iir = -mIi.toDense().get(0, 0);
                double iii = -mIi.toDense().get(1, 0);
                double i2ir = -mI2i.toDense().get(0, 0);
                double i2ii = -mI2i.toDense().get(1, 0);

                double deltaVir = -iir * edVr + iii * edVi - i2ir * edV2r - i2ii * edV2i;
                double deltaVii = -iir * edVi - iii * edVr - i2ir * edV2i - i2ii * edV2r;

                //homopolar
                double eoVr = homopolarResult.getDv().get(busNum).get(0, 0);
                double eoVi = homopolarResult.getDv().get(busNum).get(1, 0);
                double eoV2r = biphasedHomopolarResult.getDv2().get(busNum).get(0, 0);
                double eoV2i = biphasedHomopolarResult.getDv2().get(busNum).get(1, 0);

                double ior = -mIo.toDense().get(0, 0);
                double ioi = -mIo.toDense().get(1, 0);
                double i2or = -mI2o.toDense().get(0, 0);
                double i2oi = -mI2o.toDense().get(1, 0);

                double deltaVor = -ior * eoVr + ioi * eoVi - i2or * eoV2r + i2oi * eoV2i;
                double deltaVoi = -ior * eoVi - ioi * eoVr - i2or * eoV2i - i2oi * eoV2r;

                res.fillVoltageInFortescueVector(busNum, deltaVdr, deltaVdi, deltaVor, deltaVoi, deltaVir, deltaVii);
            }
        }

        return res;
    }

    public static DenseMatrix getZ(double r, double x) {
        DenseMatrix z = new DenseMatrix(2, 2);
        z.add(0, 0, r);
        z.add(0, 1, -x);
        z.add(1, 0, x);
        z.add(1, 1, r);

        return z;
    }
}
