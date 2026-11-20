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
import com.powsybl.shortcircuit.ShortCircuitParameters;
import net.jafama.FastMath;
import org.apache.commons.math3.complex.Complex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;

import static com.powsybl.sc.extensions.ShortCircuitConstants.ADMITTANCE_EPSILON_PU;
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
    private final Predicate<LfBus> innerZone;

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
        this.innerZone = createInnerLfZonePredicate(network, openScParameters.getInnerZoneBusIds());
    }

    public EquationSystem<VariableType, EquationType> create(FrequencyType frequencyType) {
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
            createShunts(variableSet, equationSystem, frequencyType);
        }

        return equationSystem;
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

    private void createShunts(VariableSet<VariableType> variableSet,
                              EquationSystem<VariableType, EquationType> equationSystem,
                              FrequencyType frequencyType) {
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
                case ADM_THEVENIN -> computeAdmForAdmThevenin(v, bus);
                case ADM_THEVENIN_HOMOPOLAR -> computeAdmForGenerators(bus);
                default -> Complex.ZERO;
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

    private static Complex computeAdmForAdmShunt(ShortCircuitParameters parameters, LfBus bus) {
        Complex y = Complex.ZERO;

        if (parameters.isWithShuntCompensators()) {
            y = getAdmittanceFromShunts(bus); // Handling shunts that physically exist
        }

        return y;
    }

    private static Complex computeAdmForAdmAdmit(Complex v, ShortCircuitParameters parameters, boolean isBusPv, LfBus bus) {
        Complex y = Complex.ZERO;

        if (parameters.isWithShuntCompensators()) {
            y = getAdmittanceFromShunts(bus); // Handling shunts that physically exist
        }

        Complex yLoadEq = InjectionAdmittances.getLoadsAdmittance(bus, v);

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

    private Complex computeAdmForAdmThevenin(Complex v, LfBus bus) {
        Complex y = Complex.ZERO;

        if (shortCircuitParameters.isWithShuntCompensators()) {
            y = getAdmittanceFromShunts(bus); // Handling shunts that physically exist
        }

        if (shortCircuitParameters.isWithLoads()) {
            y = y.add(InjectionAdmittances.getLoadsAdmittance(bus, v));
        }

        return y.add(computeAdmForGenerators(bus));
    }

    private Complex computeAdmForGenerators(LfBus bus) {
        Complex y = Complex.ZERO;
        for (LfGenerator lfgen : bus.getGenerators()) {
            if (isGeneratorSelected(lfgen, openScParameters, network, innerZone)) {
                y = y.add(InjectionAdmittances.getGeneratorAdmittance(lfgen, admittanceType, shortCircuitParameters.getStudyType()));
            }
        }
        return y;
    }

    private static Complex getAdmittanceFromShunts(LfBus bus) {
        return InjectionAdmittances.getShuntsAdmittance(bus);
    }
}
