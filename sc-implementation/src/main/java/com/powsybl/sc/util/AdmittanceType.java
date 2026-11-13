/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public enum AdmittanceType {
    ADM_INJ, // all external nodal injections that does not come from branches are considered as current injectors (including shunts elements)
    ADM_SHUNT, // all external  nodal injections that does not come from branches are considered as current injectors (but not shunt elements)
    ADM_ADMIT, // all external  nodal injections are transformed into passive shunt elements included in the Y matrix (then [Ie] should be [0])
    // used to compute the Zth Thevenin Equivalent: shunts remain shunts, synchronous machines are transformed into X" equivalent shunts,
    // remaining injections are transformed into passive shunt elements included in the Y matrix
    ADM_THEVENIN,
    ADM_THEVENIN_HOMOPOLAR // used to compute the homopolar admittance matrix for unbalanced short circuits
}
