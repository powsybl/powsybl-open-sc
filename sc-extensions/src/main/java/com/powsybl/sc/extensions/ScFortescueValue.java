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
import org.apache.commons.math3.complex.ComplexUtils;

import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * @author Thoi-Nay Vo <thoi-nay.vo at artelys.com>
 */
public final class ScFortescueValue {

    public static final ScFortescueValue ZERO = new ScFortescueValue(Complex.ZERO, Complex.ZERO, Complex.ZERO);

    private final Complex positiveSequence;
    private final Complex negativeSequence;
    private final Complex zeroSequence;

    /**
     * @param positiveSequence positive (direct) sequence component
     * @param negativeSequence negative (inverse) sequence component
     * @param zeroSequence     zero (homopolar) sequence component
     */
    public ScFortescueValue(Complex positiveSequence, Complex negativeSequence, Complex zeroSequence) {
        this.positiveSequence = Objects.requireNonNull(positiveSequence);
        this.negativeSequence = Objects.requireNonNull(negativeSequence);
        this.zeroSequence = Objects.requireNonNull(zeroSequence);
    }

    public static ScFortescueValue ofPositive(Complex positive) {
        return new ScFortescueValue(positive, Complex.ZERO, Complex.ZERO);
    }

    public static ScFortescueValue fromFortescueValue(FortescueValue value) {
        Objects.requireNonNull(value);
        return new ScFortescueValue(
                polarToComplex(value.getPositiveMagnitude(), value.getPositiveAngle()),
                polarToComplex(value.getNegativeMagnitude(), value.getNegativeAngle()),
                polarToComplex(value.getZeroMagnitude(), value.getZeroAngle()));
    }

    /**
     * Converts to the PowSyBl Core representation.
     */
    public FortescueValue toFortescueValue() {
        return new FortescueValue(positiveSequence.abs(), zeroSequence.abs(), negativeSequence.abs(),
                positiveSequence.getArgument(), zeroSequence.getArgument(), negativeSequence.getArgument());
    }

    public ScThreePhaseValue toThreePhaseValue() {
        return FortescueUtils.scFortescueToThreePhaseValue(this);
    }

    public ScFortescueValue add(ScFortescueValue other) {
        return combine(other, Complex::add);
    }

    public ScFortescueValue subtract(ScFortescueValue other) {
        return combine(other, Complex::subtract);
    }

    public ScFortescueValue multiply(ScFortescueValue other) {
        return combine(other, Complex::multiply);
    }

    public ScFortescueValue divide(ScFortescueValue other) {
        return combine(other, Complex::divide);
    }

    public ScFortescueValue multiply(Complex factor) {
        Objects.requireNonNull(factor);
        return map(c -> c.multiply(factor));
    }

    public ScFortescueValue multiply(double factor) {
        return map(c -> c.multiply(factor));
    }

    public ScFortescueValue divide(Complex divisor) {
        Objects.requireNonNull(divisor);
        return map(c -> c.divide(divisor));
    }

    public ScFortescueValue divide(double divisor) {
        return map(c -> c.divide(divisor));
    }

    public ScFortescueValue negate() {
        return map(Complex::negate);
    }

    public Complex getPositive() {
        return positiveSequence;
    }

    public Complex getNegative() {
        return negativeSequence;
    }

    public Complex getZero() {
        return zeroSequence;
    }

    private ScFortescueValue combine(ScFortescueValue other, BinaryOperator<Complex> op) {
        Objects.requireNonNull(other);
        return new ScFortescueValue(
                op.apply(positiveSequence, other.positiveSequence),
                op.apply(negativeSequence, other.negativeSequence),
                op.apply(zeroSequence, other.zeroSequence));
    }

    private ScFortescueValue map(UnaryOperator<Complex> op) {
        return new ScFortescueValue(op.apply(positiveSequence), op.apply(negativeSequence), op.apply(zeroSequence));
    }

    private static Complex polarToComplex(double magnitude, double angle) {
        return Double.isNaN(magnitude) ? Complex.NaN : ComplexUtils.polar2Complex(magnitude, Double.isNaN(angle) ? 0 : angle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScFortescueValue other)) {
            return false;
        }
        return positiveSequence.equals(other.positiveSequence) && negativeSequence.equals(other.negativeSequence) && zeroSequence.equals(other.zeroSequence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positiveSequence, negativeSequence, zeroSequence);
    }

    @Override
    public String toString() {
        return "ScFortescueValue(positive=" + positiveSequence + ", negative=" + negativeSequence + ", zero=" + zeroSequence + ")";
    }
}
