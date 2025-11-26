/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.sc.util.*;
import com.powsybl.sc.util.extensions.AdmittanceConstants;
import com.powsybl.shortcircuit.FortescueValue;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.apache.commons.math3.util.Pair;

import java.util.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitResult {

    public class CommonSupportResult {

        private LfBus lfBus2; // FIXME : might be wrongly overwritten in the "resultsPerFault" presentation

        private Complex eth2;

        private FortescueValue i2Fortescue; //fortescue vector of currents
        private FortescueValue v2Fortescue; //fortescue vector of voltages

        CommonSupportResult(LfBus lfBus2, Complex eth2,
                            Complex i2d, Complex i2o, Complex i2i,
                            Complex dv2d, Complex dv2o, Complex dv2i) {
            this.lfBus2 = lfBus2;
            this.eth2 = eth2;

            this.i2Fortescue = new FortescueValue(i2d.abs(), i2o.abs(), i2i.abs(), i2d.getArgument(), i2o.getArgument(), i2i.getArgument());

            //construction of the fortescue vector vFortescue = t[Vh, Vd, Vi]
            Complex vd = eth2.add(dv2d);
            this.v2Fortescue = new FortescueValue(vd.abs(), dv2o.abs(), dv2i.abs(), vd.getArgument(), dv2o.getArgument(), dv2i.getArgument());
        }
    }

    private LfBus lfBus; // FIXME : might be wrongly overwritten in the "resultsPerFault" presentation

    private LfNetwork lfNetwork;

    private ShortCircuitNorm norm;

    private Complex zd; // equivalent direct impedance
    private Complex zi; // equivalent inverse impedance
    private Complex zh; // equivalent homopolar impedance

    private Complex eth;

    private FortescueValue iFortescue; //fortescue vector of currents
    private FortescueValue vFortescue; //fortescue vector of voltages

    private boolean isVoltageProfileUpdated;
    private List<DenseMatrix> busNum2Dv;

    private FeedersAtNetwork eqSysFeedersDirect;

    private FeedersAtNetwork eqSysFeedersHomopolar;

    private Map<LfBus, FeedersAtBusResult> feedersAtBusResultsDirect;

    private Map<LfBus, FeedersAtBusResult> feedersAtBusResultsHomopolar;

    private ShortCircuitFault shortCircuitFault;

    private CommonSupportResult commonSupportResult; // used only for biphased with common support faults

    public ShortCircuitResult(ShortCircuitFault shortCircuitFault, LfBus lfBus,
                              Complex id, Complex zth, Complex eth, Complex dv,
                              FeedersAtNetwork eqSysFeeders, ShortCircuitNorm norm) {
        this.lfBus = lfBus;
        this.eqSysFeedersDirect = eqSysFeeders;
        this.shortCircuitFault = shortCircuitFault;
        this.norm = norm;

        this.zd = zth;
        this.zi = new Complex(0., 0.);
        this.zh = new Complex(0., 0.);

        this.eth = eth;

        this.iFortescue = new FortescueValue(id.abs(), id.getArgument());

        Complex vd = eth.add(dv);
        this.vFortescue = new FortescueValue(vd.abs(), vd.getArgument());

        isVoltageProfileUpdated = false;
    }

    public ShortCircuitResult(ShortCircuitFault shortCircuitFault, LfBus lfBus,
                              Complex id, Complex io, Complex ii,
                              Complex zd, Complex zo, Complex zi,
                              Complex vdInit, Complex dv, Complex dvo, Complex dvi,
                              FeedersAtNetwork eqSysFeedersDirect, FeedersAtNetwork eqSysFeedersHomopolar, ShortCircuitNorm norm) {
        this.lfBus = lfBus;
        this.eqSysFeedersDirect = eqSysFeedersDirect;
        this.eqSysFeedersHomopolar = eqSysFeedersHomopolar;
        this.shortCircuitFault = shortCircuitFault;
        this.norm = norm;

        this.zd = zd;
        this.zi = zi;
        this.zh = zo;

        this.eth = vdInit;

        this.iFortescue = new FortescueValue(id.abs(), io.abs(), ii.abs(), id.getArgument(), io.getArgument(), ii.getArgument());

        Complex vd = new Complex(eth.getReal() + dv.getReal(), eth.getImaginary() + dv.getImaginary());
        Complex vh = dvo;
        Complex vi = dvi;

        this.vFortescue = new FortescueValue(vd.abs(), vh.abs(), vi.abs(), vd.getArgument(), vh.getArgument(), vi.getArgument());

        isVoltageProfileUpdated = false;

    }

    public ShortCircuitResult(ShortCircuitFault shortCircuitFault, LfBus lfBus,
                              Complex id, Complex io, Complex ii,
                              Complex zd, Complex zo, Complex zi,
                              Complex vdInit, Complex dvd, Complex dvo, Complex dvi,
                              FeedersAtNetwork eqSysFeedersDirect, FeedersAtNetwork eqSysFeedersHomopolar, ShortCircuitNorm norm,
                              Complex i2d, Complex i2o, Complex i2i,
                              Complex v2dinit, Complex dv2d, Complex dv2o, Complex dv2i,
                              LfBus lfBus2) {
        this(shortCircuitFault, lfBus,
                id, io, ii,
                zd, zo, zi,
                vdInit, dvd, dvo, dvi,
                eqSysFeedersDirect, eqSysFeedersHomopolar, norm);

        this.commonSupportResult = new CommonSupportResult(lfBus2, v2dinit,
                i2d, i2o, i2i, dv2d, dv2o, dv2i);

    }

    public Complex getZd() {
        return zd;
    }

    public Complex getEth() {
        return eth;
    }

    public void updateFeedersResult() {
        //System.out.println(" VL name = " + shortCircuitVoltageLevelLocation);
        //System.out.println(" bus name = " + shortCircuitLfbusLocation);
        //System.out.println(" Icc = " + getIcc());
        //System.out.println(" Ih = " + iFortescue.get(0, 0) + " + j(" + iFortescue.get(1, 0) + ")");
        //System.out.println(" Id = " + iFortescue.get(2, 0) + " + j(" + iFortescue.get(3, 0) + ")");
        //System.out.println(" Ii = " + iFortescue.get(4, 0) + " + j(" + iFortescue.get(5, 0) + ")");
        //System.out.println(" Vh = " + vFortescue.get(0, 0) + " + j(" + vFortescue.get(1, 0) + ")");
        //System.out.println(" Vd = " + vFortescue.get(2, 0) + " + j(" + vFortescue.get(3, 0) + ")");
        //System.out.println(" Vi = " + vFortescue.get(4, 0) + " + j(" + vFortescue.get(5, 0) + ")");
        //System.out.println(" Eth = " + ethx + " + j(" + ethy + ")");

        if (isVoltageProfileUpdated) {

            /*for (Map.Entry<Integer, DenseMatrix> vd : bus2dv.entrySet()) {
                System.out.println(" dVd(" + vd.getKey() + ") = " + vd.getValue().get(2, 0) + " + j(" + vd.getValue().get(3, 0) + ")");
                System.out.println(" dVo(" + vd.getKey() + ") = " + vd.getValue().get(0, 0) + " + j(" + vd.getValue().get(1, 0) + ")");
                System.out.println(" dVi(" + vd.getKey() + ") = " + vd.getValue().get(4, 0) + " + j(" + vd.getValue().get(5, 0) + ")");
            }*/

            // Building the structure to support the feeders result
            feedersAtBusResultsDirect = new HashMap<>(); // TODO : homopolar
            for (LfBus bus : lfNetwork.getBuses()) {
                //int busNum = bus.getNum();
                //double dvx = busNum2Dv.get(busNum).get(2, 0);
                //double dvy = busNum2Dv.get(busNum).get(3, 0);
                //double vx = dvx + ethx;
                //double vy = dvy + ethy;

                //System.out.println(" dVd(" + bus.getId() + ") = " + dvx + " + j(" + dvy + ")  Module = " + bus.getNominalV() * Math.sqrt(vx * vx + vy * vy));
                //System.out.println(" dVo(" + bus.getId() + ") = " + bus2dv.get(busNum).get(0, 0) + " + j(" + bus2dv.get(busNum).get(1, 0) + ")");
                //System.out.println(" dVi(" + bus.getId() + ") = " + bus2dv.get(busNum).get(4, 0) + " + j(" + bus2dv.get(busNum).get(5, 0) + ")");

                // Init of feeder results
                FeedersAtBus busFeeders = eqSysFeedersDirect.busToFeeders.get(bus);
                FeedersAtBusResult feedersAtBusResult = new FeedersAtBusResult(busFeeders);
                feedersAtBusResultsDirect.put(bus, feedersAtBusResult);  // TODO : homopolar

            }

            // Building the sum of currents at busses from branches
            for (LfBranch branch : lfNetwork.getBranches()) {
                LfBus bus1 = branch.getBus1();
                LfBus bus2 = branch.getBus2();
                if (bus1 != null && bus2 != null) {
                    DenseMatrix yd12 = getAdmittanceMatrixBranch(branch, AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN);
                    int busNum1 = bus1.getNum();
                    double dvx1 = busNum2Dv.get(busNum1).get(2, 0);
                    double dvy1 = busNum2Dv.get(busNum1).get(3, 0);
                    int busNum2 = bus2.getNum();
                    double dvx2 = busNum2Dv.get(busNum2).get(2, 0);
                    double dvy2 = busNum2Dv.get(busNum2).get(3, 0);
                    DenseMatrix v12 = new DenseMatrix(4, 1);
                    v12.add(0, 0, dvx1 + 0.); //TODO : replace 1. by initial value
                    v12.add(1, 0, dvy1 + 0.); //TODO : replace 0. by initial value
                    v12.add(2, 0, dvx2 + 0.); //TODO : replace 1. by initial value
                    v12.add(3, 0, dvy2 + 0.); //TODO : replace 0. by initial value
                    DenseMatrix i12 = yd12.times(v12).toDense();
                    //System.out.println(" dI1d(" + branch.getId() + ") = " + i12.get(0, 0) + " + j(" + i12.get(1, 0) + ")  Module I1d = " + 1000. * 100. / bus1.getNominalV() * Math.sqrt((i12.get(0, 0) * i12.get(0, 0) + i12.get(1, 0) * i12.get(1, 0)) / 3));
                    //System.out.println(" dI2d(" + branch.getId() + ") = " + i12.get(2, 0) + " + j(" + i12.get(3, 0) + ")  Module I2d = " + 1000. * 100. / bus2.getNominalV() * Math.sqrt((i12.get(2, 0) * i12.get(2, 0) + i12.get(3, 0) * i12.get(3, 0)) / 3));

                    // Feeders :
                    // compute the sum of currents from branches at each bus
                    FeedersAtBusResult resultBus1Feeders = feedersAtBusResultsDirect.get(bus1); // TODO : homopolar
                    FeedersAtBusResult resultBus2Feeders = feedersAtBusResultsDirect.get(bus2); // TODO : homopolar

                    resultBus1Feeders.addIfeeders(i12.get(0, 0), i12.get(1, 0));
                    resultBus2Feeders.addIfeeders(i12.get(2, 0), i12.get(3, 0));

                }
            }

            // computing feeders contribution from the sum of currents at node and based on the admittance dispatch key of feeders
            for (LfBus bus : lfNetwork.getBuses()) {
                FeedersAtBusResult busFeeders = feedersAtBusResultsDirect.get(bus); // TODO : homopolar
                busFeeders.updateContributions();
            }
        }
    }

    public Complex getId() {
        return ComplexUtils.polar2Complex(iFortescue.getPositiveMagnitude(), iFortescue.getPositiveAngle());
    }

    public Complex getIo() {
        return ComplexUtils.polar2Complex(iFortescue.getZeroMagnitude(), iFortescue.getZeroAngle());
    }

    public Complex getVd() {
        return ComplexUtils.polar2Complex(vFortescue.getPositiveMagnitude(), vFortescue.getPositiveAngle());
    }

    public Map<LfBus, FeedersAtBusResult> getFeedersAtBusResultsDirect() {
        return feedersAtBusResultsDirect;
    }

    public Pair<Double, Double> getIcc() {
        // IccBase = sqrt(3) * Eth(pu) / Zth(pu) * SB(MVA) * 10e6 / (VB(kV) * 10e3)
        //double magnitudeIccBase = Math.sqrt((getIdx() * getIdx() + getIdy() * getIdy()) * 3.) * 1000. * 100. / lfBus.getNominalV();
        double magnitudeIccBase = Math.sqrt(3.) * iFortescue.getPositiveMagnitude() * 1000. * 100. / lfBus.getNominalV();
        //double angleIcc = Math.atan2(getIdy(), getIdx());
        double angleIcc = iFortescue.getPositiveAngle();

        double magnitudeIcc = magnitudeIccBase;

        // provided value will depend on the type of the fault
        ShortCircuitFault.ShortCircuitType shortCircuitType = shortCircuitFault.getType();

        if (shortCircuitType == ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND) {
            // Icc = 1/sqrt(3) * Eth(pu) / Zth(pu) * SB(MVA) * 10e6 / (VB(kV) * 10e3)
            //return Math.sqrt((getIdx() * getIdx() + getIdy() * getIdy()) / 3) * 1000. * 100. / lfBus.getNominalV();
            magnitudeIcc = magnitudeIcc / 3.;

        }

        return new Pair<>(magnitudeIcc, angleIcc);
    }

    public Pair<Double, Double> getIk() {
        // Ik = c * Un / (sqrt(3) * Zk) = c / sqrt(3) * Eth(pu) / Zth(pu) * Sb / Vb
        // Equivalent to Math.sqrt((getIdx() * getIdx() + getIdy() * getIdy()) / 3) * 100.  / lfBus.getNominalV() * norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Pair<Double, Double> icc = getIcc();
        return new Pair<>(icc.getKey() * norm.getCmaxVoltageFactor(lfBus.getNominalV()) / 1000., icc.getValue());
    }

    public double getPcc() {
        //Pcc = |Eth|*Icc*sqrt(3)
        return Math.sqrt(3) * getIcc().getKey() * lfBus.getV() * lfBus.getNominalV(); //TODO: check formula
    }

    public void setTrueVoltageProfileUpdate() {
        isVoltageProfileUpdated = true;
    }

    public void createEmptyFortescueVoltageVector(int nbBusses) {
        List<DenseMatrix> busNum2Dv = new ArrayList<>();
        for (int i = 0; i < nbBusses; i++) {
            DenseMatrix mdV = new DenseMatrix(6, 1);
            busNum2Dv.add(mdV);
        }
        this.busNum2Dv = busNum2Dv;
    }

    public void fillVoltageInFortescueVector(int busNum, Complex dV) {
        this.busNum2Dv.get(busNum).add(2, 0, dV.getReal());
        this.busNum2Dv.get(busNum).add(3, 0, dV.getImaginary());
    }

    public void fillVoltageInFortescueVector(int busNum, Complex dVd, Complex dVo, Complex dVi) {
        this.busNum2Dv.get(busNum).add(0, 0, dVo.getReal());
        this.busNum2Dv.get(busNum).add(1, 0, dVo.getImaginary());
        this.busNum2Dv.get(busNum).add(2, 0, dVd.getReal());
        this.busNum2Dv.get(busNum).add(3, 0, dVd.getImaginary());
        this.busNum2Dv.get(busNum).add(4, 0, dVi.getReal());
        this.busNum2Dv.get(busNum).add(5, 0, dVi.getImaginary());
    }

    public void setLfNetwork(LfNetwork lfNetwork) {
        this.lfNetwork = lfNetwork;
    }

    static DenseMatrix getAdmittanceMatrixBranch(LfBranch branch,
                                                 AdmittanceEquationSystem.AdmittanceType admittanceType) {

        // TODO : code duplicated with the admittance equation system, should be un-duplicated
        PiModel piModel = branch.getPiModel();
        if (piModel.getX() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has reactance equal to zero");
        }
        double rho = piModel.getR1();
        if (piModel.getZ() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has Z equal to zero");
        }
        double zInvSquare = 1 / (piModel.getZ() * piModel.getZ());
        double r = piModel.getR();
        double x = piModel.getX();
        double alpha = piModel.getA1();
        double cosA = Math.cos(Math.toRadians(alpha));
        double sinA = Math.sin(Math.toRadians(alpha));
        double gPi1 = piModel.getG1();
        double bPi1 = piModel.getB1();
        double gPi2 = piModel.getG2();
        double bPi2 = piModel.getB2();

        double g12 = rho * zInvSquare * (r * cosA + x * sinA);
        double b12 = -rho * zInvSquare * (x * cosA + r * sinA);
        double g1g12sum = rho * rho * (gPi1 + r * zInvSquare);
        double b1b12sum = rho * rho * (bPi1 - x * zInvSquare);
        if (admittanceType == AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
            g12 = g12 * AdmittanceConstants.COEF_XO_XD; // Xo = 3 * Xd as a first approximation : TODO : improve when more data available
            b12 = b12 * AdmittanceConstants.COEF_XO_XD;
            g1g12sum = g1g12sum * AdmittanceConstants.COEF_XO_XD;
            b1b12sum = b1b12sum * AdmittanceConstants.COEF_XO_XD;
        }

        double g21 = rho * zInvSquare * (r * cosA + x * sinA);
        double b21 = rho * zInvSquare * (r * sinA - x * cosA);
        double g2g21sum = r * zInvSquare + gPi2;
        double b2b21sum = -x * zInvSquare + bPi2;
        if (admittanceType == AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
            g21 = g21 * AdmittanceConstants.COEF_XO_XD; // Xo = 3 * Xd as a first approximation : TODO : improve when more data available
            b21 = b21 * AdmittanceConstants.COEF_XO_XD;
            g2g21sum = g2g21sum * AdmittanceConstants.COEF_XO_XD;
            b2b21sum = b2b21sum * AdmittanceConstants.COEF_XO_XD;
        }

        DenseMatrix mAdmittance = new DenseMatrix(4, 4);
        mAdmittance.add(0, 0, g1g12sum);
        mAdmittance.add(0, 1, -b1b12sum);
        mAdmittance.add(0, 2, -g12);
        mAdmittance.add(0, 3, b12);
        mAdmittance.add(1, 0, b1b12sum);
        mAdmittance.add(1, 1, g1g12sum);
        mAdmittance.add(1, 2, -b12);
        mAdmittance.add(1, 3, -g12);
        mAdmittance.add(2, 0, -g21);
        mAdmittance.add(2, 1, b21);
        mAdmittance.add(2, 2, g2g21sum);
        mAdmittance.add(2, 3, -b2b21sum);
        mAdmittance.add(3, 0, -b21);
        mAdmittance.add(3, 1, -g21);
        mAdmittance.add(3, 2, b2b21sum);
        mAdmittance.add(3, 3, g2g21sum);

        return mAdmittance.toDense();

    }

    // used for tests
    public double getIxFeeder(String busId, String feederId) {
        double ix = 0.;
        for (LfBus bus : lfNetwork.getBuses()) {
            if (bus.getId().equals(busId)) {
                FeedersAtBusResult resultFeeder = feedersAtBusResultsDirect.get(bus); // TODO : homopolar
                List<FeederResult> busFeedersResults = resultFeeder.getBusFeedersResult();
                for (FeederResult feederResult : busFeedersResults) {
                    if (feederResult.getFeeder().getId().equals(feederId)) {
                        ix = feederResult.getIxContribution();
                    }
                }
            }
        }
        return ix;
    }

}
