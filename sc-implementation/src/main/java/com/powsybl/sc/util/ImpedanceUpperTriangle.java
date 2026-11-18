/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import org.apache.commons.math3.complex.Complex;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */
public record ImpedanceUpperTriangle(Complex z12, Complex z21, Complex z22) {
}
