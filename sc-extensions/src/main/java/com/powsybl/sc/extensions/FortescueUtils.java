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

import static com.powsybl.sc.extensions.FortescueConstants.FORTESCUE_A;
import static com.powsybl.sc.extensions.FortescueConstants.FORTESCUE_A2;

/**
 * <p>
 * With the rotation operator {@code a = exp(j * 2π/3)}:
 * <pre>
 * [A]   [1  1   1 ] [zero    ]
 * [B] = [1  a²  a ] [positive]
 * [C]   [1  a   a²] [negative]
 *
 * [zero    ]         [1  1   1 ] [A]
 * [positive] = 1/3 * [1  a   a²] [B]
 * [negative]         [1  a²  a ] [C]
 * </pre>
 *
 * @author Thoi-Nay Vo <thoi-nay.vo at artelys.com>
 */
public final class FortescueUtils {

    private FortescueUtils() {
    }

    public static ScThreePhaseValue scFortescueToThreePhaseValue(ScFortescueValue fortescueValue) {
        Objects.requireNonNull(fortescueValue);
        Complex pos = fortescueValue.getPositive();
        Complex neg = fortescueValue.getNegative();
        Complex zero = fortescueValue.getZero();
        return new ScThreePhaseValue(
                zero.add(pos).add(neg),
                zero.add(FORTESCUE_A2.multiply(pos)).add(FORTESCUE_A.multiply(neg)),
                zero.add(FORTESCUE_A.multiply(pos)).add(FORTESCUE_A2.multiply(neg)));
    }

    public static ScFortescueValue threePhaseToScFortescueValue(ScThreePhaseValue threePhaseValue) {
        Objects.requireNonNull(threePhaseValue);
        Complex a = threePhaseValue.getPhaseA();
        Complex b = threePhaseValue.getPhaseB();
        Complex c = threePhaseValue.getPhaseC();
        return new ScFortescueValue(
                a.add(FORTESCUE_A.multiply(b)).add(FORTESCUE_A2.multiply(c)).divide(3),
                a.add(FORTESCUE_A2.multiply(b)).add(FORTESCUE_A.multiply(c)).divide(3),
                a.add(b).add(c).divide(3));
    }
}
