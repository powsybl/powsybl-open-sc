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

    private final Complex positive;
    private final Complex negative;
    private final Complex zero;

    /**
     * @param positive positive (direct) sequence component
     * @param negative negative (inverse) sequence component
     * @param zero     zero (homopolar) sequence component
     */
    public ScFortescueValue(Complex positive, Complex negative, Complex zero) {
        this.positive = Objects.requireNonNull(positive);
        this.negative = Objects.requireNonNull(negative);
        this.zero = Objects.requireNonNull(zero);
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
        return new FortescueValue(positive.abs(), zero.abs(), negative.abs(),
                positive.getArgument(), zero.getArgument(), negative.getArgument());
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
        return positive;
    }

    public Complex getNegative() {
        return negative;
    }

    public Complex getZero() {
        return zero;
    }

    private ScFortescueValue combine(ScFortescueValue other, BinaryOperator<Complex> op) {
        Objects.requireNonNull(other);
        return new ScFortescueValue(
                op.apply(positive, other.positive),
                op.apply(negative, other.negative),
                op.apply(zero, other.zero));
    }

    private ScFortescueValue map(UnaryOperator<Complex> op) {
        return new ScFortescueValue(op.apply(positive), op.apply(negative), op.apply(zero));
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
        return positive.equals(other.positive) && negative.equals(other.negative) && zero.equals(other.zero);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positive, negative, zero);
    }

    @Override
    public String toString() {
        return "ScFortescueValue(positive=" + positive + ", negative=" + negative + ", zero=" + zero + ")";
    }
}
