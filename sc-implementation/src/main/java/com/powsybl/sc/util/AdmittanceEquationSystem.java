/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.*;
import com.powsybl.sc.util.extensions.ScGenerator;
import com.powsybl.sc.util.extensions.ScLoad;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;
import com.powsybl.shortcircuit.StudyType;
import net.jafama.FastMath;
import org.apache.commons.math3.complex.Complex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.powsybl.openloadflow.util.PerUnit.SB;
import static com.powsybl.sc.extensions.ShortCircuitConstants.ADMITTANCE_EPSILON_PU;
import static com.powsybl.sc.extensions.ShortCircuitConstants.IMPEDANCE_EPSILON_PU;
import static com.powsybl.sc.util.ShortCircuitHelper.createInnerZonePredicate;
import static com.powsybl.sc.util.ShortCircuitHelper.isGeneratorSelected;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class AdmittanceEquationSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdmittanceEquationSystem.class);

    private AdmittanceEquationSystem() {
    }

    //Equations are created based on the branches connections
    private static void createImpedantBranch(VariableSet<VariableType> variableSet, EquationSystem<VariableType, EquationType> equationSystem,
                                             LfBranch branch, LfBus bus1, LfBus bus2, ImpedanceLinearResolutionParameters parameters, FrequencyType frequencyType) {
        if (bus1 != null && bus2 != null) {
            // Equation system Y*V = I (expressed in cartesian coordinates x,y)
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_YR)
                    .addTerm(new AdmittanceEquationTermX1(branch, bus1, bus2, variableSet, parameters, frequencyType));

            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_YI)
                    .addTerm(new AdmittanceEquationTermY1(branch, bus1, bus2, variableSet, parameters, frequencyType));

            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_YR)
                    .addTerm(new AdmittanceEquationTermX2(branch, bus1, bus2, variableSet, parameters, frequencyType));

            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_YI)
                    .addTerm(new AdmittanceEquationTermY2(branch, bus1, bus2, variableSet, parameters, frequencyType));
        }
    }

    public enum AdmittanceType {
        ADM_INJ, // all external nodal injections that does not come from branches are considered as current injectors (including shunts elements)
        ADM_SHUNT, // all external  nodal injections that does not come from branches are considered as current injectors (but not shunt elements)
        ADM_ADMIT, // all external  nodal injections are transformed into passive shunt elements included in the Y matrix (then [Ie] should be [0])
        ADM_THEVENIN, // used to compute the Zth Thevenin Equivalent: shunts remain shunts, synchronous machines are transformed into X" equivalent shunts, remaining injections are transformed into passive shunt elements included in the Y matrix
        ADM_THEVENIN_HOMOPOLAR // used to compute the homopolar admittance matrix for unbalanced short circuits
    }

    public enum FrequencyType {
        FREQ_20_HZ,
        FREQ_50_HZ
    }

    private static void createBranches(LfNetwork network, VariableSet<VariableType> variableSet, EquationSystem<VariableType, EquationType> equationSystem, ImpedanceLinearResolutionParameters parameters, FrequencyType frequencyType) {
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            PiModel piModel = branch.getPiModel();
            if (FastMath.abs(piModel.getX()) < LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE) {
                if (bus1 != null && bus2 != null) {
                    LOGGER.warn("Warning: Branch = {} : Non impedant lines not supported in the current version of the reduction method",
                            branch.getId());
                }
            } else {
                createImpedantBranch(variableSet, equationSystem, branch, bus1, bus2, parameters, frequencyType);
            }
        }
    }

    private static double getBfromShunt(LfBus bus) {
        List<Feeder> feederList = new ArrayList<>();
        return getBfromShuntAndUpdateFeederList(bus, feederList);
    }

    private static double getBfromShuntAndUpdateFeederList(LfBus bus, List<Feeder> feederList) {
        LfShunt shunt = bus.getShunt().orElse(null);
        double tmpB = 0.;
        if (shunt != null) {
            tmpB += shunt.getB();
            Feeder shuntFeeder = new ShuntFeeder(new Complex(0., shunt.getB()), shunt.getId(), bus, Feeder.FeederType.SHUNT);
            feederList.add(shuntFeeder);
            //check if g will be implemented
        }
        LfShunt controllerShunt = bus.getControllerShunt().orElse(null);
        if (controllerShunt != null) {
            tmpB += controllerShunt.getB();
            Feeder shuntFeeder = new ShuntFeeder(new Complex(0., controllerShunt.getB()), controllerShunt.getId(), bus, Feeder.FeederType.CONTROLLER_SHUNT);
            feederList.add(shuntFeeder);
            //check if g will be implemented
        }

        return tmpB;
    }

    private static Complex getYtransfromRdXdAndUpdateFeederList(Network network, LfBus bus, ImpedanceLinearResolutionParameters parameters, Predicate<LfBus> innerZone,
                                                                StudyType admittanceStudyType, List<Feeder> feederList, AdmittanceType admittanceType) {
        double vnomVl = bus.getNominalV();
        Complex tmpY = new Complex(0.);
        for (LfGenerator lfgen : bus.getGenerators()) { //compute R'd or R"d from generators at bus
            if (!isGeneratorSelected(lfgen, parameters, network, innerZone)) {
                continue;
            }
            ScGenerator scGen = (ScGenerator) lfgen.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            double kG = (Double) lfgen.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
            Complex z = scGen.getTransZd().add(scGen.getStepUpTfoZ()).multiply(kG);
            if (admittanceStudyType == StudyType.SUB_TRANSIENT) {
                z = scGen.getSubTransZd().add(scGen.getStepUpTfoZ()).multiply(kG);
            }

            if (admittanceType == AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
                // For now, xo and ro are fixed independently of x'd and x"d:
                // further improvement might be needed if xo and ro are different for transient and subTransient short circuit analysis
                z = new Complex(0.);
                if (scGen.isGrounded()) {
                    z = scGen.getZo();
                }
            }

            if (z.abs() > IMPEDANCE_EPSILON_PU) {
                Complex yGen = z.reciprocal().multiply(vnomVl * vnomVl / SB);
                tmpY = tmpY.add(yGen);
                Feeder genFeeder = new GeneratorFeeder(yGen, lfgen);
                feederList.add(genFeeder);
            }
        }

        return tmpY;
    }

    private static Complex computeAdmForAdmShunt(ImpedanceLinearResolutionParameters parameters, LfBus bus) {
        Complex y = new Complex(0.);

        if (parameters.isWithShuntCompensators()) {
            y = new Complex(0., getBfromShunt(bus)); // Handling shunts that physically exist
        }

        return y;
    }

    private static Complex computeAdmForAdmAdmit(Complex v, ImpedanceLinearResolutionParameters parameters, boolean isBusPv, LfBus bus) {
        Complex y = new Complex(0.);

        if (parameters.isWithShuntCompensators()) {
            y = new Complex(0., getBfromShunt(bus)); // Handling shunts that physically exist
        }

        ScLoad scLoad = (ScLoad) bus.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
        Complex yLoadEq = scLoad.ydEquivalent().divide(v.abs() * v.abs());

        // Handling transformation of generators into equivalent shunts
        // Warning !!! : evaluation of power injections mandatory
        double gGenEq = -bus.getP().eval() / (v.abs() * v.abs()) - yLoadEq.getReal(); // full nodal P injection without the load

        Complex yGenEq;
        if (isBusPv) {
            // full nodal Q injection without the load
            yGenEq = new Complex(gGenEq, bus.getQ().eval() / (v.abs() * v.abs()) + yLoadEq.getImaginary());
        } else {
            yGenEq = new Complex(gGenEq, bus.getGenerationTargetQ() / (v.abs() * v.abs()));
        }

        return y.add(yLoadEq).add(yGenEq);
    }

    private static Complex computeAdmForAdmThevenin(Network network, Complex v, ImpedanceLinearResolutionParameters parameters, Predicate<LfBus> innerZone,
                                                    LfBus bus, FeedersAtNetwork feeders, StudyType admittanceStudyType, AdmittanceType admittanceType) {
        Complex y = Complex.ZERO;
        Complex yLoadEq = Complex.ZERO;

        List<Feeder> feederList = new ArrayList<>();

        if (parameters.isWithShuntCompensators()) {
            // Handling shunts that physically exist
            y = new Complex(0., getBfromShuntAndUpdateFeederList(bus, feederList)); // ! updates feederList
        }

        if (parameters.isWithLoads()) {
            ScLoad scLoad = (ScLoad) bus.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            yLoadEq = scLoad.ydEquivalent().divide(v.abs() * v.abs());

            if (yLoadEq.abs() > ADMITTANCE_EPSILON_PU) {
                Feeder loadFeeder = new LoadFeeder(yLoadEq, bus); // Currently only one feeder aggregating all the loads of the bus!
                feederList.add(loadFeeder);
            }
        }

        Complex yGenEq = getYtransfromRdXdAndUpdateFeederList(network, bus, parameters, innerZone, admittanceStudyType, feederList, admittanceType); // ! updates feederList
        // TODO : check how to verify that the generators are operating

        FeedersAtBus shortCircuitEquationSystemBusFeeders = new FeedersAtBus(feederList, bus);
        feeders.busToFeeders.put(bus, shortCircuitEquationSystemBusFeeders);

        return y.add(yLoadEq).add(yGenEq);
    }

    private static Complex computeAdmForAdmTheveninHomopolar(Network network, LfBus bus, ImpedanceLinearResolutionParameters parameters, Predicate<LfBus> innerZone,
                                                             FeedersAtNetwork feeders, StudyType admittanceStudyType, AdmittanceType admittanceType) {
        List<Feeder> feederList = new ArrayList<>(); // not used yet in homopolar

        Complex y = getYtransfromRdXdAndUpdateFeederList(network, bus, parameters, innerZone, admittanceStudyType, feederList, admittanceType); // ! updates feederList
        //TODO : check how to verify that the generators are operating

        FeedersAtBus shortCircuitEquationSystemBusFeeders = new FeedersAtBus(feederList, bus);
        feeders.busToFeeders.put(bus, shortCircuitEquationSystemBusFeeders);

        return y;
    }

    private static void createShunts(Network network, LfNetwork lfNetwork, VariableSet<VariableType> variableSet, EquationSystem<VariableType, EquationType> equationSystem,
                                     ImpedanceLinearResolutionParameters parameters, FeedersAtNetwork feeders, FrequencyType frequencyType) {
        AdmittanceType admittanceType = parameters.getAdmittanceType();
        StudyType admittanceStudyType = parameters.getStudyType();
        Predicate<LfBus> innerZone = createInnerZonePredicate(network, parameters.getInnerZoneBusIds());

        for (LfBus bus : lfNetwork.getBuses()) {
            Complex v = parameters.getInitialVoltage(bus.getNum());
            boolean isBusPv = bus.isVoltageControlled();

            // Total shunt at bus to be integrated in the admittance matrix is calculated summing:
            // 1. shunts representing shunt components
            // 2. shunts representing loads
            // 3. shunts representing generating units

            Complex y = switch (admittanceType) {
                case ADM_SHUNT -> computeAdmForAdmShunt(parameters, bus);
                case ADM_ADMIT -> computeAdmForAdmAdmit(v, parameters, isBusPv, bus);
                case ADM_THEVENIN -> computeAdmForAdmThevenin(network, v, parameters, innerZone, bus, feeders, admittanceStudyType, admittanceType);
                case ADM_THEVENIN_HOMOPOLAR -> computeAdmForAdmTheveninHomopolar(network, bus, parameters, innerZone, feeders, admittanceStudyType, admittanceType);
                default -> new Complex(0.);
            };

            // coef to adapt reactance if freq is not 50Hz
            double freqCoef = 1.0;
            if (frequencyType == FrequencyType.FREQ_20_HZ) {
                freqCoef = 20. / 50.;
            }

            Complex z = y.reciprocal();
            z = new Complex(z.getReal(), z.getImaginary() * freqCoef);
            y = z.reciprocal();

            if (y.abs() > ADMITTANCE_EPSILON_PU) {
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_YR)
                        .addTerm(new AdmittanceEquationTermShunt(y.getReal(), y.getImaginary(), bus, variableSet, true));
                equationSystem.createEquation(bus.getNum(), EquationType.BUS_YI)
                        .addTerm(new AdmittanceEquationTermShunt(y.getReal(), y.getImaginary(), bus, variableSet, false));
            }
        }
    }

    public static EquationSystem<VariableType, EquationType> create(Network network, LfNetwork lfNetwork, VariableSet<VariableType> variableSet,
                                                                    ImpedanceLinearResolutionParameters parameters, FeedersAtNetwork feeders, FrequencyType frequencyType) {
        AdmittanceType admittanceType = parameters.getAdmittanceType();
        AcLoadFlowParameters acLoadFlowParameters = parameters.getAcLoadFlowParameters();
        EquationSystem<VariableType, EquationType> equationSystem = new EquationSystem<>(EquationType.class, lfNetwork, variableSet);

        if (admittanceType == AdmittanceType.ADM_ADMIT) {
            try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acLoadFlowParameters)) {
                new AcloadFlowEngine(context)
                        .run();
            }
        }

        createBranches(lfNetwork, variableSet, equationSystem, parameters, frequencyType);
        if (admittanceType != AdmittanceType.ADM_INJ) { //shunts created in the admittance matrix are only those that really exist in the network
            createShunts(network, lfNetwork, variableSet, equationSystem, parameters, feeders, frequencyType);
        }

        return equationSystem;
    }
}
