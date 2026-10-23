/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public final class FortescueConstants {

    private FortescueConstants() {
    }

    public static final Complex FORTESCUE_A = new Complex(-0.5, FastMath.sqrt(3.) / 2); // Fortescue operator a = e^(j·2π/3)
    public static final Complex FORTESCUE_A2 = FORTESCUE_A.conjugate(); // a² = e^(j·4π/3) = conjugate of a

    public static final double DEFAULT_COEFF_RO = 1;
    public static final double DEFAULT_COEFF_XO = 1;

    public static final GeneratorFortescueType.GeneratorType DEFAULT_GENERATOR_FORTESCUE_TYPE = GeneratorFortescueType.GeneratorType.ROTATING_MACHINE;
}
