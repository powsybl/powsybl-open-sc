/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.math.matrix.ComplexMatrix;
import com.powsybl.openloadflow.network.*;
import com.powsybl.sc.extensions.FrequencyType;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.sc.extensions.ScFortescueValue;
import com.powsybl.sc.util.*;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;

import java.util.*;

import static com.powsybl.openloadflow.util.PerUnit.SB;
import static com.powsybl.sc.extensions.ShortCircuitConstants.CURRENT_EPSILON_PU;
import static com.powsybl.sc.extensions.ShortCircuitConstants.IMPEDANCE_EPSILON_PU;
import static com.powsybl.sc.implementation.ShortCircuitFault.ShortCircuitType.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitResult {

    public enum FortescueType {
        DIRECT,
        HOMOPOLAR,
        INVERSE
    }

    private final LfBus lfBus; // FIXME : might be wrongly overwritten in the "resultsPerFault" presentation

    private LfNetwork lfNetwork;

    private final ShortCircuitNorm norm;

    private final Complex zd20hz;
    private final Complex zh20hz;

    private final Complex eth;

    private final ScFortescueValue zFortescue;
    private final ScFortescueValue iFortescue; //fortescue vector of currents
    private final ScFortescueValue vFortescue; //fortescue vector of voltages

    private boolean isVoltageProfileUpdated;
    private List<ScFortescueValue> busNum2Dv;
    private Map<LfBranch, ScFortescueValue> branchDi1;
    private Map<LfBranch, ScFortescueValue> branchDi2;

    // eqSysFeedersDirect contains the equivalent admittance of feeders's injectors,
    // they are built when the AdmittanceEquationSystem is built and put in input of the sc result
    private FeedersAtNetwork eqSysFeedersDirect;
    private FeedersAtNetwork eqSysFeedersHomopolar;

    private Map<LfBus, FeedersAtBusResult> feedersResultDirect; // For each feeder in eqSysFeedersDirect, this map contains the results
    private Map<LfBus, FeedersAtBusResult> feedersResultsHomopolar;
    private Map<LfBus, FeedersAtBusResult> feedersResultsInverse;

    private final ShortCircuitFault shortCircuitFault;

    public ShortCircuitResult(ShortCircuitFault shortCircuitFault, LfBus lfBus,
                              Complex id, Complex zth, Complex eth, Complex dv,
                              FeedersAtNetwork eqSysFeeders, ShortCircuitNorm norm, Complex zth20hz) {
        // Called for a balanced short circuit calculation
        this.lfBus = lfBus;
        this.eqSysFeedersDirect = eqSysFeeders;
        this.shortCircuitFault = shortCircuitFault;
        this.norm = norm;

        this.zFortescue = ScFortescueValue.ofPositive(zth);
        this.zd20hz = zth20hz;
        this.zh20hz = new Complex(0., 0.);

        this.eth = eth;

        this.iFortescue = ScFortescueValue.ofPositive(id);

        Complex vd = eth.add(dv);
        this.vFortescue = ScFortescueValue.ofPositive(vd);

        isVoltageProfileUpdated = false;
    }

    public ShortCircuitResult(ShortCircuitFault shortCircuitFault, LfBus lfBus,
                              ScFortescueValue iFortescue,
                              ScFortescueValue zFortescue,
                              Complex vdInit, ScFortescueValue dvFortescue,
                              FeedersAtNetwork eqSysFeedersDirect, FeedersAtNetwork eqSysFeedersHomopolar, ShortCircuitNorm norm, Complex zth20hz, Complex zoth20hz) {
        // Called for an unbalanced short circuit calculation with no common support
        this.lfBus = lfBus;
        this.eqSysFeedersDirect = eqSysFeedersDirect;
        this.eqSysFeedersHomopolar = eqSysFeedersHomopolar;
        this.shortCircuitFault = shortCircuitFault;
        this.norm = norm;

        this.zFortescue = zFortescue;
        this.zd20hz = zth20hz;
        this.zh20hz = zoth20hz;

        this.eth = vdInit;

        this.iFortescue = iFortescue;

        Complex vd = vdInit.add(dvFortescue.positiveComponent());
        Complex vh = dvFortescue.zeroComponent();
        Complex vi = dvFortescue.negativeComponent();

        this.vFortescue = new ScFortescueValue(vd, vi, vh);

        isVoltageProfileUpdated = false;

    }

    public ShortCircuitResult(ShortCircuitFault shortCircuitFault, LfBus lfBus,
                              ScFortescueValue iFortescue,
                              ScFortescueValue zFortescue,
                              Complex vdInit, ScFortescueValue dvFortescue,
                              FeedersAtNetwork eqSysFeedersDirect, FeedersAtNetwork eqSysFeedersHomopolar, ShortCircuitNorm norm,
                              ScFortescueValue i2Fortescue,
                              Complex v2dinit, ScFortescueValue dv2Fortescue,
                              LfBus lfBus2, Complex zth20hz, Complex zoth20hz) {
        // Called for an unbalanced short circuit calculation with a common support
        this(shortCircuitFault, lfBus,
                iFortescue,
                zFortescue,
                vdInit, dvFortescue,
                eqSysFeedersDirect, eqSysFeedersHomopolar, norm, zth20hz, zoth20hz);
    }

    public Complex getZd() {
        return zFortescue.positiveComponent();
    }

    public Complex getEth() {
        return eth;
    }

    public Complex getZh() {
        return zFortescue.zeroComponent();
    }

    public Complex getZd20hz() {
        return zd20hz;
    }

    public Complex getZh20hz() {
        return zh20hz;
    }

    public ComplexMatrix getDiFromDv(LfBranch branch, ScFortescueValue dv1Fort, ScFortescueValue dv2Fort, FortescueType fType,
                                     ShortCircuitParameters parameters,
                                     OpenShortCircuitParameters openScParameters) {
        AdmittanceType admType = AdmittanceType.ADM_THEVENIN;
        if (fType == FortescueType.HOMOPOLAR) {
            admType = AdmittanceType.ADM_THEVENIN_HOMOPOLAR;
        }

        ComplexMatrix yBranch = BranchAdmittance.create(branch, admType, parameters.isWithNeutralPosition(),
                        openScParameters.isWithResistances(), openScParameters.isWithCapacities(),
                        FrequencyType.FREQ_50_HZ)
                .toComplexMatrix();
        Complex y11 = yBranch.get(0, 0);
        Complex y12 = yBranch.get(0, 1);
        Complex y21 = yBranch.get(1, 0);
        Complex y22 = yBranch.get(1, 1);

        Complex dv1 = dv1Fort.positiveComponent();
        Complex dv2 = dv2Fort.positiveComponent();
        if (fType == FortescueType.HOMOPOLAR) {
            dv1 = dv1Fort.zeroComponent();
            dv2 = dv2Fort.zeroComponent();
        } else if (fType == FortescueType.INVERSE) {
            dv1 = dv1Fort.negativeComponent();
            dv2 = dv2Fort.negativeComponent();
        }
        Complex di1 = y11.multiply(dv1).add(y12.multiply(dv2));
        Complex di2 = y21.multiply(dv1).add(y22.multiply(dv2));

        ComplexMatrix di = new ComplexMatrix(2, 1);
        di.set(0, 0, di1);
        di.set(1, 0, di2);

        return di;
    }

    public void calculateCurrentPerBus(ShortCircuitParameters parameters, OpenShortCircuitParameters openScParameters) {

        // For each branch, we build the sum of currents at buses from branches
        // 1- Input is the voltage delta at each end of the branch
        // 2- Then we compute dI = [Ybranch].dV
        // 3- Then dI is added to the sum of current of bus
        // 4- The resulting sum of current at each bus is the current coming from branches,
        // which is equal to the current at bus injectors (Kirchhoff's law)

        // Start with the fault bus contribution
        feedersResultDirect.get(lfBus).addItofeedersSum(iFortescue.positiveComponent());

        branchDi1 = new HashMap<>();
        branchDi2 = new HashMap<>();

        for (LfBranch branch : lfNetwork.getBranches()) {

            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                int busNum1 = bus1.getNum();
                int busNum2 = bus2.getNum();
                ScFortescueValue dv1Fort = busNum2Dv.get(busNum1);
                ScFortescueValue dv2Fort = busNum2Dv.get(busNum2);

                // Direct
                ComplexMatrix di = getDiFromDv(branch, dv1Fort, dv2Fort, FortescueType.DIRECT, parameters, openScParameters);
                Complex di1 = di.get(0, 0);
                Complex di2 = di.get(1, 0);

                FeedersAtBusResult resultDirectBus1Feeders = feedersResultDirect.get(bus1); // TODO : homopolar
                FeedersAtBusResult resultDirectBus2Feeders = feedersResultDirect.get(bus2); // TODO : homopolar
                // Feeders : compute the sum of currents from branches at each bus
                // dI coming from branch to the bus are added to the actual sum of current at bus
                resultDirectBus1Feeders.addItofeedersSum(di1);
                resultDirectBus2Feeders.addItofeedersSum(di2);

                if (shortCircuitFault.getType() == TRIPHASED_GROUND) {
                    branchDi1.put(branch, ScFortescueValue.ofPositive(di1));
                    branchDi2.put(branch, ScFortescueValue.ofPositive(di2));

                    PiModel piModel = branch.getPiModel();
                    Complex zBranch;
                    if (parameters.isWithNeutralPosition()
                            && piModel instanceof PiModelArray piModelArray) {
                        zBranch = new Complex(piModelArray.getModel(0).getR(), piModelArray.getModel(0).getX());
                    } else {
                        zBranch = new Complex(branch.getPiModel().getR(), branch.getPiModel().getX());
                    }

                    resultDirectBus1Feeders.getBusFeedersResult().add(new FeederResult(new BranchFeeder(zBranch, branch, ThreeSides.ONE), di1));
                    resultDirectBus2Feeders.getBusFeedersResult().add(new FeederResult(new BranchFeeder(zBranch, branch, ThreeSides.TWO), di2));
                    continue;
                }

                // Homopolar
                ComplexMatrix dio = getDiFromDv(branch, dv1Fort, dv2Fort, FortescueType.HOMOPOLAR, parameters, openScParameters);
                Complex dio1 = dio.get(0, 0);
                Complex dio2 = dio.get(1, 0);

                FeedersAtBusResult resultHomopolarBus1Feeders = feedersResultsHomopolar.get(bus1); // TODO : homopolar
                FeedersAtBusResult resultHomopolarBus2Feeders = feedersResultsHomopolar.get(bus2); // TODO : homopolar
                // Feeders : compute the sum of currents from branches at each bus
                // dI coming from branch to the bus are added to the actual sum of current at bus
                resultHomopolarBus1Feeders.addItofeedersSum(dio1);
                resultHomopolarBus2Feeders.addItofeedersSum(dio2);

                // Inverse
                ComplexMatrix dii = getDiFromDv(branch, dv1Fort, dv2Fort, FortescueType.INVERSE, parameters, openScParameters);
                Complex dii1 = dii.get(0, 0);
                Complex dii2 = dii.get(1, 0);

                FeedersAtBusResult resultInverseBus1Feeders = feedersResultsInverse.get(bus1); // TODO : homopolar
                FeedersAtBusResult resultInverseBus2Feeders = feedersResultsInverse.get(bus2); // TODO : homopolar
                // Feeders : compute the sum of currents from branches at each bus
                // dI coming from branch to the bus are added to the actual sum of current at bus
                resultInverseBus1Feeders.addItofeedersSum(dii1);
                resultInverseBus2Feeders.addItofeedersSum(dii2);

                branchDi1.put(branch, new ScFortescueValue(di1, dii1, dio1));
                branchDi2.put(branch, new ScFortescueValue(di2, dii2, dio2));
            }
        }
    }

    public void updateFeedersResult(ShortCircuitParameters parameters, OpenShortCircuitParameters openScParameters) {
        if (!isVoltageProfileUpdated) {
            return;
        }
        // Building the structure to support the feeders result, a FeederResult is built from each Feeder in input
        feedersResultDirect = new HashMap<>(); // TODO : homopolar
        feedersResultsHomopolar = new HashMap<>();
        feedersResultsInverse = new HashMap<>();
        for (LfBus bus : lfNetwork.getBuses()) {
            // Init of feeder results
            FeedersAtBus busFeedersDirect = eqSysFeedersDirect.busToFeeders.get(bus);
            FeedersAtBusResult feedersAtBusResultDirect = new FeedersAtBusResult(busFeedersDirect);
            feedersResultDirect.put(bus, feedersAtBusResultDirect);

            if (shortCircuitFault.getType() == TRIPHASED_GROUND) {
                continue;
            }

            // Homopolar
            FeedersAtBus busFeedersHomopolar = eqSysFeedersHomopolar.busToFeeders.get(bus);
            FeedersAtBusResult feedersAtBusResultHomopolar = new FeedersAtBusResult(busFeedersHomopolar);
            feedersResultsHomopolar.put(bus, feedersAtBusResultHomopolar);

            // Inverse
            FeedersAtBus busFeedersInverse = eqSysFeedersDirect.busToFeeders.get(bus); // for now we use direct feeder impedance to compute inverse current
            FeedersAtBusResult feedersAtBusResultInverse = new FeedersAtBusResult(busFeedersInverse);
            feedersResultsInverse.put(bus, feedersAtBusResultInverse);
        }

        calculateCurrentPerBus(parameters, openScParameters);

        // Given the admittance of each feeder at bus, computed during building of AdmittanceEquationSystem,
        // we can then deduce the current contribution of each feeder, which is stored in feeder result
        // computing feeders contribution of each feeder at bus from the sum of currents at bus
        // and based on the admittance dispatch key of feeders
        for (LfBus bus : lfNetwork.getBuses()) {
            FeedersAtBusResult busFeedersDirect = feedersResultDirect.get(bus);
            busFeedersDirect.updateContributions();

            if (shortCircuitFault.getType() == TRIPHASED_GROUND) {
                continue;
            }

            FeedersAtBusResult busFeedersHomopolar = feedersResultsHomopolar.get(bus);
            busFeedersHomopolar.updateContributions();

            FeedersAtBusResult busFeedersInverse = feedersResultsInverse.get(bus);
            busFeedersInverse.updateContributions();
        }
    }

    public Complex getId() {
        return iFortescue.positiveComponent();
    }

    public Complex getIo() {
        return iFortescue.zeroComponent();
    }

    public Complex getVd() {
        return vFortescue.positiveComponent();
    }

    public ScFortescueValue getiFortescue() {
        return iFortescue;
    }

    public ScFortescueValue getvFortescue() {
        return vFortescue;
    }

    public LfBus getLfBus() {
        return lfBus;
    }

    public LfNetwork getLfNetwork() {
        return lfNetwork;
    }

    public Map<LfBus, FeedersAtBusResult> getFeedersResultDirect() {
        return feedersResultDirect;
    }

    public Map<LfBus, FeedersAtBusResult> getFeedersResultsHomopolar() {
        return feedersResultsHomopolar;
    }

    public Map<LfBus, FeedersAtBusResult> getFeedersResultsInverse() {
        return feedersResultsInverse;
    }

    public List<ScFortescueValue> getBusNum2Dv() {
        return busNum2Dv;
    }

    public Map<LfBranch, ScFortescueValue> getBranchDi1() {
        return branchDi1;
    }

    public Map<LfBranch, ScFortescueValue> getBranchDi2() {
        return branchDi2;
    }

    public boolean isVoltageProfileUpdated() {
        return isVoltageProfileUpdated;
    }

    public ShortCircuitFault getShortCircuitFault() {
        return shortCircuitFault;
    }

    public double getIbase() {
        // I(A) = I(pu) * I(base)
        // I(base) = SB(MVA) * 10e6 / (VB(kV) * 10e3)
        return 1000. * SB / lfBus.getNominalV();
    }

    public double getPeakCoef(double r, double x) {
        if (x == 0.) {
            throw new IllegalArgumentException("Peak current could not be computed because Xth is zero = ");
        }
        return getPeakCoef(r / x);
    }

    public double getPeakCoef(double rOverx) {
        return 1.02 + 0.98 * Math.exp(-3. * rOverx);
    }

    public double getPeakCoefb() {
        double r = getZd().getReal();
        double x = getZd().getImaginary();
        if (x == 0.) {
            throw new IllegalArgumentException("Peak current could not be computed because Xth is zero = ");
        }
        double factor = 1.15;
        if (r / x < 0.3) {
            factor = 1.;
        }
        double coef = getPeakCoef(r, x) * factor;
        if (lfBus.getNominalV() <= 20.) {
            coef = Math.min(coef, 1.8);
        } else {
            coef = Math.min(coef, 2.0);
        }
        return coef;
    }

    public double getPeakCoefcDirect() {
        // this is the simplified formula only using direct 20Hz Thevenin impedance
        double rc = zd20hz.getReal();
        double xc = zd20hz.getImaginary();

        if (xc == 0.) {
            throw new IllegalArgumentException("Peak current could not be computed because Xth is zero = ");
        }
        return getPeakCoef(rc / xc * 20. / 50.);
    }

    public double getPeakCoefc() {
        double rc = zd20hz.getReal();
        double xc = zd20hz.getImaginary();
        double roc = zh20hz.getReal();
        double xoc = zh20hz.getImaginary();

        if (xc == 0.) {
            throw new IllegalArgumentException("Peak current could not be computed because Xth is zero = ");
        }
        return getPeakCoef((2. * rc + roc) / (2. * xc + xoc) * 20. / 50.);
    }

    public double getIpeakc() {
        return getIk().abs() * getPeakCoefc() * Math.sqrt(2.);
    }

    public Complex getIk() {
        ShortCircuitFault.ShortCircuitType faultType = shortCircuitFault.getType();
        return switch (faultType) {
            case MONOPHASED -> getIk1pp();
            case TRIPHASED_GROUND -> getIkpp();
            case BIPHASED -> getIk2pp();
            case BIPHASED_GROUND -> getIke2epp();
            default -> throw new UnsupportedOperationException("Ik not available for fault type " + faultType);
        };
    }

    public Complex getIkpp() {
        // for a Triphased fault, by definition Ik"(A) = c * Un / (sqrt(3) * Zk)
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex id = iFortescue.positiveComponent(); // id(pu) = Eth(pu) / Zk(pu) // TODO: check if radians
        // Ik"(kA) = c * id(pu) * I(base) / (sqrt(3) * 1000) : the 1000 factor is to move from A to kA
        return id.multiply(c).divide(Math.sqrt(3.) * 1000.).multiply(getIbase());
    }

    public Complex getIk1pp() {
        // for a Monphased fault, by definition Ik"1(A) = c * Un * sqrt(3) / Zk
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex id = iFortescue.positiveComponent(); // id(pu) = Eth(pu) / Zk(pu) // TODO: check if radians
        // Ik"1(kA) = c * id(pu) * sqrt(3) * I(base) / 1000 : the 1000 factor is to move from A to kA
        return id.multiply(c).divide(1000.).multiply(Math.sqrt(3.) * getIbase());
    }

    public Complex getIk2pp() {
        // for a biphased fault (no ground), by definition Ik2" = c * Un / abs(Zd + Zi + Zf)
        // given that Ib = j * sqrt(3) * tM * [Vinit] / (Zdf + Zif +Zf)  and that Id = Ib * j / sqrt(3) we have Ik2" = c * Id
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex id = iFortescue.positiveComponent();
        return id.multiply(c).divide(1000.).multiply(getIbase());
    }

    public Complex getIk2el2pp() {
        // for a biphased ground fault , by definition Ik2EL2" = c * Ib / sqrt(3)
        // given that Ib = Io + a².Id + a.Ii
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex ib = iFortescue.toThreePhaseValue().phaseB();
        return ib.multiply(c).divide(1000. * Math.sqrt(3.)).multiply(getIbase());
    }

    public Complex getIk2el3pp() {
        // for a biphased ground fault , by definition Ik2EL3" = c * Ic / sqrt(3)
        // given that Ic = Io + a.Id + a².Ii
        double c = norm.getCmaxVoltageFactor(lfBus.getNominalV());
        Complex ic = iFortescue.toThreePhaseValue().phaseC();
        return ic.multiply(c).divide(1000. * Math.sqrt(3.)).multiply(getIbase());
    }

    public Complex getIke2epp() {
        // by definition IkE2E" = Ik2EL3" + Ik2EL2"
        return getIk2el2pp().add(getIk2el3pp());
    }

    public Complex getDefaultIk() {
        // Ik is a default value used for testing
        // IccBase = sqrt(3) * Eth(pu) / Zth(pu) * SB(MVA) * 10e6 / (VB(kV) * 10e3)
        double magnitudeIccBase = Math.sqrt(3.) * iFortescue.positiveComponent().abs() * 1000. * SB / lfBus.getNominalV();
        double angleIcc = iFortescue.positiveComponent().getArgument();
        Complex val = ComplexUtils.polar2Complex(magnitudeIccBase, angleIcc);

        // Ik = c * Un / (sqrt(3) * Zk) = c / sqrt(3) * Eth(pu) / Zth(pu) * Sb / Vb
        return val.multiply(norm.getCmaxVoltageFactor(lfBus.getNominalV()) / 1000.);
    }

    public Complex getSk() {
        //Sk" = |Unom|*Ik"*sqrt(3)
        return getIk().multiply(Math.sqrt(3) * lfBus.getV() * lfBus.getNominalV());
    }

    public double getMaxROverX() {
        double maxRatio = 0.0;
        Map<LfBranch, ScFortescueValue> branchDeltaI1 = getBranchDi1();
        Map<LfBranch, ScFortescueValue> branchDeltaI2 = getBranchDi2();
        for (LfBranch branch : getLfNetwork().getBranches()) {
            PiModel piModel = branch.getPiModel();
            // we only look at ratio for branches with significant short circuit currents
            if (piModel.getX() >= IMPEDANCE_EPSILON_PU
                    && (branchDeltaI1.get(branch).positiveComponent().abs() > CURRENT_EPSILON_PU
                    || branchDeltaI2.get(branch).positiveComponent().abs() > CURRENT_EPSILON_PU)) {
                maxRatio = Math.max(maxRatio, piModel.getR() / piModel.getX());
            }
        }
        return maxRatio;
    }

    public void setTrueVoltageProfileUpdate() {
        isVoltageProfileUpdated = true;
    }

    public void createEmptyFortescueVoltageVector(int nbBuses) {
        List<ScFortescueValue> busNum2Dv = new ArrayList<>();
        for (int i = 0; i < nbBuses; i++) {
            ScFortescueValue mdV = ScFortescueValue.ZERO;
            busNum2Dv.add(mdV);
        }
        this.busNum2Dv = busNum2Dv;
    }

    public void fillVoltageInFortescueVector(int busNum, Complex dV) {
        this.busNum2Dv.set(busNum, ScFortescueValue.ofPositive(dV));
    }

    public void fillVoltageInFortescueVector(int busNum, ScFortescueValue dVFort) {
        this.busNum2Dv.set(busNum, dVFort);
    }

    public void setLfNetwork(LfNetwork lfNetwork) {
        this.lfNetwork = lfNetwork;
    }
}
