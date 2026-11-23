/**
 * Copyright (c) 2026, Jean-Baptiste Heyberger
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import org.apache.commons.math3.complex.Complex;

/**
 * @param zg Fault impedance to the ground
 * @param zb Fault impedance linked to the second phase
 * @param zc Fault impedance linked to the third phase
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public record ShortCircuitFaultImpedance(Complex zg, Complex zb, Complex zc) {

    public ShortCircuitFaultImpedance(Complex zg) {
        this(zg, new Complex(0.), new Complex(0.));
    }
}
