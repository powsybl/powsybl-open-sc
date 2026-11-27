/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.shortcircuit.FortescueValue;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;

import static com.powsybl.sc.extensions.FortescueConstants.FORTESCUE_A;
import static com.powsybl.sc.extensions.FortescueConstants.FORTESCUE_A2;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Thoi-Nay Vo <thoi-nay.vo at artelys.com>
 */
class ScFortescueThreePhaseValueTest {

    private static final double EPS = 1e-12;

    private static final ScFortescueValue FORTESCUE = new ScFortescueValue(
            new Complex(1.0, 0.5), new Complex(-0.2, 0.3), new Complex(0.1, -0.4));
    private static final ScFortescueValue FORTESCUE_OTHER = new ScFortescueValue(
            new Complex(2, 1), new Complex(1, -1), new Complex(0.5, 0));

    private static final ScThreePhaseValue THREE_PHASE = new ScThreePhaseValue(
            new Complex(1, 2), new Complex(-3, 0.5), new Complex(0.7, -1));
    private static final ScThreePhaseValue THREE_PHASE_OTHER = new ScThreePhaseValue(
            new Complex(0.5, -1), new Complex(2, 2), new Complex(-1, 0.3));

    private static void assertComplexEquals(Complex expected, Complex actual) {
        assertEquals(expected.getReal(), actual.getReal(), EPS);
        assertEquals(expected.getImaginary(), actual.getImaginary(), EPS);
    }

    private static void assertFortescueEquals(ScFortescueValue expected, ScFortescueValue actual) {
        assertComplexEquals(expected.getPositive(), actual.getPositive());
        assertComplexEquals(expected.getNegative(), actual.getNegative());
        assertComplexEquals(expected.getZero(), actual.getZero());
    }

    private static void assertThreePhaseEquals(ScThreePhaseValue expected, ScThreePhaseValue actual) {
        assertComplexEquals(expected.getPhaseA(), actual.getPhaseA());
        assertComplexEquals(expected.getPhaseB(), actual.getPhaseB());
        assertComplexEquals(expected.getPhaseC(), actual.getPhaseC());
    }

    @Test
    void testFortescueGetters() {
        assertEquals(new Complex(1.0, 0.5), FORTESCUE.getPositive());
        assertEquals(new Complex(-0.2, 0.3), FORTESCUE.getNegative());
        assertEquals(new Complex(0.1, -0.4), FORTESCUE.getZero());
    }

    @Test
    void testFortescueOfPositiveAndZero() {
        ScFortescueValue balanced = ScFortescueValue.ofPositive(new Complex(1, 2));
        assertEquals(new Complex(1, 2), balanced.getPositive());
        assertEquals(Complex.ZERO, balanced.getNegative());
        assertEquals(Complex.ZERO, balanced.getZero());

        assertEquals(Complex.ZERO, ScFortescueValue.ZERO.getPositive());
        assertEquals(Complex.ZERO, ScFortescueValue.ZERO.getNegative());
        assertEquals(Complex.ZERO, ScFortescueValue.ZERO.getZero());
    }

    @Test
    void testFortescueArithmetic() {
        assertFortescueEquals(new ScFortescueValue(new Complex(3, 1.5), new Complex(0.8, -0.7), new Complex(0.6, -0.4)),
                FORTESCUE.add(FORTESCUE_OTHER));
        assertFortescueEquals(new ScFortescueValue(new Complex(-1, -0.5), new Complex(-1.2, 1.3), new Complex(-0.4, -0.4)),
                FORTESCUE.subtract(FORTESCUE_OTHER));
        // component-wise product: (1 + 0.5j)(2 + j) = 1.5 + 2j, (-0.2 + 0.3j)(1 - j) = 0.1 + 0.5j, (0.1 - 0.4j) * 0.5 = 0.05 - 0.2j
        assertFortescueEquals(new ScFortescueValue(new Complex(1.5, 2), new Complex(0.1, 0.5), new Complex(0.05, -0.2)),
                FORTESCUE.multiply(FORTESCUE_OTHER));
        assertFortescueEquals(FORTESCUE, FORTESCUE.multiply(FORTESCUE_OTHER).divide(FORTESCUE_OTHER));
        assertFortescueEquals(ScFortescueValue.ZERO, FORTESCUE.subtract(FORTESCUE));
    }

    @Test
    void testFortescueScalarOperations() {
        assertFortescueEquals(new ScFortescueValue(new Complex(-1.0, -0.5), new Complex(0.2, -0.3), new Complex(-0.1, 0.4)),
                FORTESCUE.negate());
        assertFortescueEquals(FORTESCUE.negate(), FORTESCUE.multiply(-1.));
        assertFortescueEquals(new ScFortescueValue(new Complex(2, 1), new Complex(-0.4, 0.6), new Complex(0.2, -0.8)),
                FORTESCUE.multiply(2.));
        // multiplying by j rotates every component by 90°
        assertFortescueEquals(new ScFortescueValue(new Complex(-0.5, 1), new Complex(-0.3, -0.2), new Complex(0.4, 0.1)),
                FORTESCUE.multiply(Complex.I));
        assertFortescueEquals(FORTESCUE, FORTESCUE.multiply(4.).divide(4.));
        assertFortescueEquals(FORTESCUE, FORTESCUE.multiply(new Complex(1, -2)).divide(new Complex(1, -2)));
    }

    @Test
    void testFortescueImmutability() {
        ScFortescueValue copy = new ScFortescueValue(FORTESCUE.getPositive(), FORTESCUE.getNegative(), FORTESCUE.getZero());
        FORTESCUE.add(FORTESCUE_OTHER);
        FORTESCUE.multiply(3.);
        FORTESCUE.negate();
        assertEquals(FORTESCUE, copy);
    }

    @Test
    void testFortescueEqualsHashCodeToString() {
        ScFortescueValue copy = new ScFortescueValue(new Complex(1.0, 0.5), new Complex(-0.2, 0.3), new Complex(0.1, -0.4));
        assertEquals(FORTESCUE, copy);
        assertEquals(FORTESCUE.hashCode(), copy.hashCode());
        assertNotEquals(FORTESCUE, FORTESCUE_OTHER);
        assertTrue(FORTESCUE.toString().contains("positive"));
    }

    @Test
    void testFortescueNullArguments() {
        assertThrows(NullPointerException.class, () -> new ScFortescueValue(null, Complex.ZERO, Complex.ZERO));
        assertThrows(NullPointerException.class, () -> new ScFortescueValue(Complex.ZERO, null, Complex.ZERO));
        assertThrows(NullPointerException.class, () -> new ScFortescueValue(Complex.ZERO, Complex.ZERO, null));
        assertThrows(NullPointerException.class, () -> FORTESCUE.add(null));
        assertThrows(NullPointerException.class, () -> FORTESCUE.multiply((Complex) null));
        assertThrows(NullPointerException.class, () -> ScFortescueValue.fromFortescueValue(null));
    }

    @Test
    void testFortescueToPowsyblFortescueValue() {
        FortescueValue core = FORTESCUE.toFortescueValue();
        assertEquals(FORTESCUE.getPositive().abs(), core.getPositiveMagnitude(), EPS);
        assertEquals(FORTESCUE.getNegative().abs(), core.getNegativeMagnitude(), EPS);
        assertEquals(FORTESCUE.getZero().abs(), core.getZeroMagnitude(), EPS);
        assertEquals(FORTESCUE.getPositive().getArgument(), core.getPositiveAngle(), EPS);
        assertEquals(FORTESCUE.getNegative().getArgument(), core.getNegativeAngle(), EPS);
        assertEquals(FORTESCUE.getZero().getArgument(), core.getZeroAngle(), EPS);

        assertFortescueEquals(FORTESCUE, ScFortescueValue.fromFortescueValue(core));
    }

    @Test
    void testThreePhaseGetters() {
        assertEquals(new Complex(1, 2), THREE_PHASE.getPhaseA());
        assertEquals(new Complex(-3, 0.5), THREE_PHASE.getPhaseB());
        assertEquals(new Complex(0.7, -1), THREE_PHASE.getPhaseC());
    }

    @Test
    void testThreePhaseArithmetic() {
        assertThreePhaseEquals(new ScThreePhaseValue(new Complex(1.5, 1), new Complex(-1, 2.5), new Complex(-0.3, -0.7)),
                THREE_PHASE.add(THREE_PHASE_OTHER));
        assertThreePhaseEquals(new ScThreePhaseValue(new Complex(0.5, 3), new Complex(-5, -1.5), new Complex(1.7, -1.3)),
                THREE_PHASE.subtract(THREE_PHASE_OTHER));
        // phase-wise product: (1 + 2j)(0.5 - j) = 2.5, (-3 + 0.5j)(2 + 2j) = -7 - 5j, (0.7 - j)(-1 + 0.3j) = -0.4 + 1.21j
        assertThreePhaseEquals(new ScThreePhaseValue(new Complex(2.5, 0), new Complex(-7, -5), new Complex(-0.4, 1.21)),
                THREE_PHASE.multiply(THREE_PHASE_OTHER));
        assertThreePhaseEquals(THREE_PHASE, THREE_PHASE.multiply(THREE_PHASE_OTHER).divide(THREE_PHASE_OTHER));
        assertThreePhaseEquals(new ScThreePhaseValue(Complex.ZERO, Complex.ZERO, Complex.ZERO), THREE_PHASE.subtract(THREE_PHASE));
    }

    @Test
    void testThreePhaseScalarOperations() {
        assertThreePhaseEquals(new ScThreePhaseValue(new Complex(-1, -2), new Complex(3, -0.5), new Complex(-0.7, 1)),
                THREE_PHASE.negate());
        assertThreePhaseEquals(THREE_PHASE.negate(), THREE_PHASE.multiply(-1.));
        assertThreePhaseEquals(new ScThreePhaseValue(new Complex(2, 4), new Complex(-6, 1), new Complex(1.4, -2)),
                THREE_PHASE.multiply(2.));
        assertThreePhaseEquals(new ScThreePhaseValue(new Complex(-2, 1), new Complex(-0.5, -3), new Complex(1, 0.7)),
                THREE_PHASE.multiply(Complex.I));
        assertThreePhaseEquals(THREE_PHASE, THREE_PHASE.multiply(4.).divide(4.));
        assertThreePhaseEquals(THREE_PHASE, THREE_PHASE.multiply(new Complex(1, -2)).divide(new Complex(1, -2)));
    }

    @Test
    void testThreePhaseEqualsHashCodeToString() {
        ScThreePhaseValue copy = new ScThreePhaseValue(new Complex(1, 2), new Complex(-3, 0.5), new Complex(0.7, -1));
        assertEquals(THREE_PHASE, copy);
        assertEquals(THREE_PHASE.hashCode(), copy.hashCode());
        assertNotEquals(THREE_PHASE, THREE_PHASE_OTHER);
        assertNotEquals(THREE_PHASE, null);
        assertNotNull(THREE_PHASE.toString());
    }

    @Test
    void testThreePhaseNullArguments() {
        assertThrows(NullPointerException.class, () -> new ScThreePhaseValue(null, Complex.ZERO, Complex.ZERO));
        assertThrows(NullPointerException.class, () -> new ScThreePhaseValue(Complex.ZERO, null, Complex.ZERO));
        assertThrows(NullPointerException.class, () -> new ScThreePhaseValue(Complex.ZERO, Complex.ZERO, null));
        assertThrows(NullPointerException.class, () -> THREE_PHASE.add(null));
        assertThrows(NullPointerException.class, () -> THREE_PHASE.multiply((Complex) null));
    }

    @Test
    void testBalancedPositiveSequenceToThreePhase() {
        // a balanced direct system: A = 1, B = a², C = a
        ScThreePhaseValue phases = ScFortescueValue.ofPositive(Complex.ONE).toThreePhaseValue();
        assertComplexEquals(Complex.ONE, phases.getPhaseA());
        assertComplexEquals(FORTESCUE_A2, phases.getPhaseB());
        assertComplexEquals(FORTESCUE_A, phases.getPhaseC());
    }

    @Test
    void testNegativeAndZeroSequenceToThreePhase() {
        // negative sequence: A = 1, B = a, C = a²
        ScThreePhaseValue negative = new ScFortescueValue(Complex.ZERO, Complex.ONE, Complex.ZERO).toThreePhaseValue();
        assertThreePhaseEquals(new ScThreePhaseValue(Complex.ONE, FORTESCUE_A, FORTESCUE_A2), negative);

        // zero sequence: same value on the three phases
        ScThreePhaseValue zero = new ScFortescueValue(Complex.ZERO, Complex.ZERO, Complex.ONE).toThreePhaseValue();
        assertThreePhaseEquals(new ScThreePhaseValue(Complex.ONE, Complex.ONE, Complex.ONE), zero);
    }

    @Test
    void testThreePhaseToFortescue() {
        ScFortescueValue zeroOnly = new ScThreePhaseValue(new Complex(2, 1), new Complex(2, 1), new Complex(2, 1)).toScFortescueValue();
        assertFortescueEquals(new ScFortescueValue(Complex.ZERO, Complex.ZERO, new Complex(2, 1)), zeroOnly);

        // single phase-to-ground fault on phase A (Ib = Ic = 0): I+ = I- = I0 = Ia / 3
        Complex ia = new Complex(3, -6);
        ScFortescueValue singlePhase = new ScThreePhaseValue(ia, Complex.ZERO, Complex.ZERO).toScFortescueValue();
        Complex third = ia.divide(3);
        assertFortescueEquals(new ScFortescueValue(third, third, third), singlePhase);
    }

    @Test
    void testRoundTrips() {
        assertFortescueEquals(FORTESCUE, FORTESCUE.toThreePhaseValue().toScFortescueValue());
        assertThreePhaseEquals(THREE_PHASE, THREE_PHASE.toScFortescueValue().toThreePhaseValue());

        assertThreePhaseEquals(FortescueUtils.scFortescueToThreePhaseValue(FORTESCUE), FORTESCUE.toThreePhaseValue());
        assertFortescueEquals(FortescueUtils.threePhaseToScFortescueValue(THREE_PHASE), THREE_PHASE.toScFortescueValue());
    }

    @Test
    void testConversionIsLinear() {
        // T(x + y) = T(x) + T(y) and T(k.x) = k.T(x)
        assertThreePhaseEquals(FORTESCUE.toThreePhaseValue().add(FORTESCUE_OTHER.toThreePhaseValue()),
                FORTESCUE.add(FORTESCUE_OTHER).toThreePhaseValue());
        Complex k = new Complex(0.3, -1.7);
        assertThreePhaseEquals(FORTESCUE.toThreePhaseValue().multiply(k), FORTESCUE.multiply(k).toThreePhaseValue());
    }

    @Test
    void testThreePhaseConsistentWithPowsyblCore() {
        FortescueValue.ThreePhaseValue core = FORTESCUE.toFortescueValue().toThreePhaseValue();
        ScThreePhaseValue phases = FORTESCUE.toThreePhaseValue();
        double sqrt3 = Math.sqrt(3);
        assertEquals(phases.getPhaseA().abs(), core.getMagnitudeA() * sqrt3, 1e-9);
        assertEquals(phases.getPhaseB().abs(), core.getMagnitudeB() * sqrt3, 1e-9);
        assertEquals(phases.getPhaseC().abs(), core.getMagnitudeC() * sqrt3, 1e-9);
        assertEquals(phases.getPhaseA().getArgument(), core.getAngleA(), 1e-9);
        assertEquals(phases.getPhaseB().getArgument(), core.getAngleB(), 1e-9);
        assertEquals(phases.getPhaseC().getArgument(), core.getAngleC(), 1e-9);
    }
}
