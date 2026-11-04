/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public enum ShortCircuitAnalysisType {
    SELECTIVE, // short circuit analysis for List<ShortCircuitFault> faults in input
    SYSTEMATIC // short circuit analysis for all buses of input grid
}
