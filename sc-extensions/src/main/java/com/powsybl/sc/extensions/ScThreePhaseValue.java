/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import org.apache.commons.math3.complex.Complex;

import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * @author Thoi-Nay Vo <thoi-nay.vo at artelys.com>
 */
public record ScThreePhaseValue(Complex phaseA, Complex phaseB, Complex phaseC) {

    public ScThreePhaseValue {
        Objects.requireNonNull(phaseA);
        Objects.requireNonNull(phaseB);
        Objects.requireNonNull(phaseC);
    }

    public ScFortescueValue toScFortescueValue() {
        return FortescueUtils.threePhaseToScFortescueValue(this);
    }

    public ScThreePhaseValue add(ScThreePhaseValue other) {
        return combine(other, Complex::add);
    }

    public ScThreePhaseValue subtract(ScThreePhaseValue other) {
        return combine(other, Complex::subtract);
    }

    public ScThreePhaseValue multiply(ScThreePhaseValue other) {
        return combine(other, Complex::multiply);
    }

    public ScThreePhaseValue divide(ScThreePhaseValue other) {
        return combine(other, Complex::divide);
    }

    public ScThreePhaseValue multiply(Complex factor) {
        Objects.requireNonNull(factor);
        return map(c -> c.multiply(factor));
    }

    public ScThreePhaseValue multiply(double factor) {
        return map(c -> c.multiply(factor));
    }

    public ScThreePhaseValue divide(Complex divisor) {
        Objects.requireNonNull(divisor);
        return map(c -> c.divide(divisor));
    }

    public ScThreePhaseValue divide(double divisor) {
        return map(c -> c.divide(divisor));
    }

    public ScThreePhaseValue negate() {
        return map(Complex::negate);
    }

    private ScThreePhaseValue combine(ScThreePhaseValue other, BinaryOperator<Complex> op) {
        Objects.requireNonNull(other);
        return new ScThreePhaseValue(
                op.apply(phaseA, other.phaseA),
                op.apply(phaseB, other.phaseB),
                op.apply(phaseC, other.phaseC));
    }

    private ScThreePhaseValue map(UnaryOperator<Complex> op) {
        return new ScThreePhaseValue(op.apply(phaseA), op.apply(phaseB), op.apply(phaseC));
    }
}
