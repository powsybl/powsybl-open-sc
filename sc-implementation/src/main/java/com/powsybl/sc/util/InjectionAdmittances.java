/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.sc.util.extensions.ScGenerator;
import com.powsybl.sc.util.extensions.ScLoad;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;
import com.powsybl.sc.util.extensions.ShortCircuitNormExtensions;
import com.powsybl.shortcircuit.StudyType;
import org.apache.commons.math3.complex.Complex;

import static com.powsybl.openloadflow.util.PerUnit.SB;
import static com.powsybl.sc.extensions.ShortCircuitConstants.IMPEDANCE_EPSILON_PU;

/**
 * @author Thoi-Nay Vo <thoi-nay.vo at artelys.com>
 */
final class InjectionAdmittances {

    private InjectionAdmittances() {
    }

    static Complex getShuntsAdmittance(LfBus bus) {
        //check if g will be implemented
        double susceptance = bus.getShunt().map(LfShunt::getB).orElse(0.)
                + bus.getControllerShunt().map(LfShunt::getB).orElse(0.);
        double conductance = bus.getShunt().map(LfShunt::getG).orElse(0.)
                + bus.getControllerShunt().map(LfShunt::getG).orElse(0.);
        return new Complex(conductance, susceptance);
    }

    static Complex getShuntAdmittance(ShuntCompensator shunt) {
        double vNom = shunt.getTerminal().getVoltageLevel().getNominalV();
        return new Complex(shunt.getG(), shunt.getB()).multiply(vNom * vNom / SB);
    }

    static Complex getLoadsAdmittance(LfBus bus, Complex v) {
        ScLoad scLoad = (ScLoad) bus.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
        return scLoad.ydEquivalent().divide(v.abs() * v.abs());
    }

    static Complex getLoadAdmittance(Load load, Complex v) {
        Complex zdPu = ShortCircuitExtensions.computeLoadZdPu(load);
        if (zdPu.abs() <= IMPEDANCE_EPSILON_PU) {
            return Complex.ZERO;
        }
        return zdPu.reciprocal().divide(v.abs() * v.abs());
    }

    static Complex getGeneratorAdmittance(LfGenerator lfGenerator, AdmittanceType admittanceType, StudyType studyType) {
        ScGenerator scGen = (ScGenerator) lfGenerator.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
        double kG = (Double) lfGenerator.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
        return getGeneratorAdmittance(scGen, kG, lfGenerator.getBus().getNominalV(), admittanceType, studyType);
    }

    static Complex getGeneratorAdmittance(Generator generator, ShortCircuitNormExtensions normExtensions,
                                          AdmittanceType admittanceType, StudyType studyType) {
        ScGenerator scGen = ShortCircuitExtensions.createScGenerator(generator);
        double kG = ShortCircuitExtensions.getGeneratorKg(generator, normExtensions);
        double vNom = generator.getTerminal().getVoltageLevel().getNominalV();
        return getGeneratorAdmittance(scGen, kG, vNom, admittanceType, studyType);
    }

    private static Complex getGeneratorAdmittance(ScGenerator scGen, double kG, double vNom,
                                                  AdmittanceType admittanceType, StudyType studyType) {
        Complex z;
        if (admittanceType == AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
            // For now, xo and ro are fixed independently of x'd and x"d:
            // further improvement might be needed if xo and ro are different for transient and subTransient short circuit analysis
            z = scGen.isGrounded() ? scGen.getZo() : Complex.ZERO;
        } else {
            Complex zd = studyType == StudyType.SUB_TRANSIENT ? scGen.getSubTransZd() : scGen.getTransZd();
            z = zd.add(scGen.getStepUpTfoZ()).multiply(kG);
        }
        if (z.abs() <= IMPEDANCE_EPSILON_PU) {
            return Complex.ZERO;
        }
        return z.reciprocal().multiply(vNom * vNom / SB);
    }
}
