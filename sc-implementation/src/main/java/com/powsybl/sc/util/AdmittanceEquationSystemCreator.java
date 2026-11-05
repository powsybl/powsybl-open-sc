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
import com.powsybl.sc.extensions.FrequencyType;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.sc.util.extensions.ScGenerator;
import com.powsybl.sc.util.extensions.ScLoad;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;
import com.powsybl.shortcircuit.ShortCircuitParameters;
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
import static com.powsybl.sc.util.ShortCircuitHelper.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class AdmittanceEquationSystemCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdmittanceEquationSystemCreator.class);

    private final Network network;
    private final LfNetwork lfNetwork;
    private final List<Complex> initialVoltages;
    private final AdmittanceType admittanceType;
    private final ShortCircuitParameters shortCircuitParameters;
    private final OpenShortCircuitParameters openScParameters;

    public AdmittanceEquationSystemCreator(Network network,
                                           LfNetwork lfNetwork,
                                           List<Complex> initialVoltages,
                                           AdmittanceType admittanceType,
                                           ShortCircuitParameters shortCircuitParameters,
                                           OpenShortCircuitParameters openScParameters) {
        this.network = network;
        this.lfNetwork = lfNetwork;
        this.initialVoltages = initialVoltages;
        this.admittanceType = admittanceType;
        this.shortCircuitParameters = shortCircuitParameters;
        this.openScParameters = openScParameters;
    }

    //Equations are created based on the branches connections
    private void createImpedantBranch(VariableSet<VariableType> variableSet, EquationSystem<VariableType, EquationType> equationSystem,
                                             LfBranch branch, FrequencyType frequencyType) {
        LfBus bus1 = branch.getBus1();
        LfBus bus2 = branch.getBus2();
        if (bus1 != null && bus2 != null) {
            // Equation system Y*V = I (expressed in cartesian coordinates x,y)
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_YR)
                    .addTerm(new AdmittanceEquationTermX1(branch, variableSet, shortCircuitParameters, openScParameters, admittanceType, frequencyType));

            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_YI)
                    .addTerm(new AdmittanceEquationTermY1(branch, variableSet, shortCircuitParameters, openScParameters, admittanceType, frequencyType));

            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_YR)
                    .addTerm(new AdmittanceEquationTermX2(branch, variableSet, shortCircuitParameters, openScParameters, admittanceType, frequencyType));

            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_YI)
                    .addTerm(new AdmittanceEquationTermY2(branch, variableSet, shortCircuitParameters, openScParameters, admittanceType, frequencyType));
        }
    }

    private void createBranches(VariableSet<VariableType> variableSet, EquationSystem<VariableType, EquationType> equationSystem, FrequencyType frequencyType) {
        for (LfBranch branch : lfNetwork.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            PiModel piModel = branch.getPiModel();
            if (FastMath.abs(piModel.getX()) < LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE) {
                if (bus1 != null && bus2 != null) {
                    LOGGER.warn("Warning: Branch = {} : Non impedant lines not supported in the current version of the reduction method",
                            branch.getId());
                }
            } else {
                createImpedantBranch(variableSet, equationSystem, branch, frequencyType);
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

    private Complex getYtransfromRdXdAndUpdateFeederList(LfBus bus, Predicate<LfBus> innerZone, List<Feeder> feederList) {
        double vnomVl = bus.getNominalV();
        Complex tmpY = new Complex(0.);
        for (LfGenerator lfgen : bus.getGenerators()) { //compute R'd or R"d from generators at bus
            if (!isGeneratorSelected(lfgen, openScParameters, network, innerZone, shortCircuitParameters.getInitialVoltageProfileMode())) {
                continue;
            }
            ScGenerator scGen = (ScGenerator) lfgen.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            double kG = (Double) lfgen.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
            Complex z = scGen.getTransZd().add(scGen.getStepUpTfoZ()).multiply(kG);
            if (shortCircuitParameters.getStudyType() == StudyType.SUB_TRANSIENT) {
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

    private static Complex computeAdmForAdmShunt(ShortCircuitParameters parameters, LfBus bus) {
        Complex y = new Complex(0.);

        if (parameters.isWithShuntCompensators()) {
            y = new Complex(0., getBfromShunt(bus)); // Handling shunts that physically exist
        }

        return y;
    }

    private static Complex computeAdmForAdmAdmit(Complex v, ShortCircuitParameters parameters, boolean isBusPv, LfBus bus) {
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

    private Complex computeAdmForAdmThevenin(Complex v, Predicate<LfBus> innerZone, LfBus bus, FeedersAtNetwork feeders) {
        Complex y = Complex.ZERO;
        Complex yLoadEq = Complex.ZERO;

        List<Feeder> feederList = new ArrayList<>();

        if (shortCircuitParameters.isWithShuntCompensators()) {
            // Handling shunts that physically exist
            y = new Complex(0., getBfromShuntAndUpdateFeederList(bus, feederList)); // ! updates feederList
        }

        if (shortCircuitParameters.isWithLoads()) {
            ScLoad scLoad = (ScLoad) bus.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            yLoadEq = scLoad.ydEquivalent().divide(v.abs() * v.abs());

            if (yLoadEq.abs() > ADMITTANCE_EPSILON_PU) {
                Feeder loadFeeder = new LoadFeeder(yLoadEq, bus); // Currently only one feeder aggregating all the loads of the bus!
                feederList.add(loadFeeder);
            }
        }

        Complex yGenEq = getYtransfromRdXdAndUpdateFeederList(bus, innerZone, feederList); // ! updates feederList
        // TODO : check how to verify that the generators are operating

        FeedersAtBus shortCircuitEquationSystemBusFeeders = new FeedersAtBus(feederList, bus);
        feeders.busToFeeders.put(bus, shortCircuitEquationSystemBusFeeders);

        return y.add(yLoadEq).add(yGenEq);
    }

    private Complex computeAdmForAdmTheveninHomopolar(LfBus bus, Predicate<LfBus> innerZone, FeedersAtNetwork feeders) {
        List<Feeder> feederList = new ArrayList<>(); // not used yet in homopolar

        Complex y = getYtransfromRdXdAndUpdateFeederList(bus, innerZone, feederList); // ! updates feederList
        //TODO : check how to verify that the generators are operating

        FeedersAtBus shortCircuitEquationSystemBusFeeders = new FeedersAtBus(feederList, bus);
        feeders.busToFeeders.put(bus, shortCircuitEquationSystemBusFeeders);

        return y;
    }

    private void createShunts(VariableSet<VariableType> variableSet,
                                     EquationSystem<VariableType, EquationType> equationSystem,
                                     FeedersAtNetwork feeders,
                                     FrequencyType frequencyType) {
        Predicate<LfBus> innerZone = createInnerZonePredicate(network, openScParameters.getInnerZoneBusIds());

        for (LfBus bus : lfNetwork.getBuses()) {
            Complex v = initialVoltages.get(bus.getNum());
            boolean isBusPv = bus.isVoltageControlled();

            // Total shunt at bus to be integrated in the admittance matrix is calculated summing:
            // 1. shunts representing shunt components
            // 2. shunts representing loads
            // 3. shunts representing generating units

            Complex y = switch (admittanceType) {
                case ADM_SHUNT -> computeAdmForAdmShunt(shortCircuitParameters, bus);
                case ADM_ADMIT -> computeAdmForAdmAdmit(v, shortCircuitParameters, isBusPv, bus);
                case ADM_THEVENIN -> computeAdmForAdmThevenin(v, innerZone, bus, feeders);
                case ADM_THEVENIN_HOMOPOLAR -> computeAdmForAdmTheveninHomopolar(bus, innerZone, feeders);
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

    public EquationSystem<VariableType, EquationType> create(FeedersAtNetwork feeders, FrequencyType frequencyType) {
        VariableSet<VariableType> variableSet = new VariableSet<>();
        EquationSystem<VariableType, EquationType> equationSystem = new EquationSystem<>(EquationType.class, lfNetwork, variableSet);

        if (admittanceType == AdmittanceType.ADM_ADMIT) {
            AcLoadFlowParameters acLoadFlowParameters = getAcLfParametersFromOpenScParameters(openScParameters);
            try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acLoadFlowParameters)) {
                new AcloadFlowEngine(context)
                        .run();
            }
        }

        createBranches(variableSet, equationSystem, frequencyType);
        if (admittanceType != AdmittanceType.ADM_INJ) { //shunts created in the admittance matrix are only those that really exist in the network
            createShunts(variableSet, equationSystem, feeders, frequencyType);
        }

        return equationSystem;
    }
}
