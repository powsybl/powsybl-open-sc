/**
 * Copyright (c) 2025, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitResultPrinter {

    private ShortCircuitResult shortCircuitResult;

    ShortCircuitResultPrinter(ShortCircuitResult scr) {
        shortCircuitResult = scr;
    }

    public void printEquivalentDirectImpedance() {
        double rd = shortCircuitResult.getZd().getReal();
        double xd = shortCircuitResult.getZd().getImaginary();
        //Pair<Double, Double> res = FortescueUtil.getPolarFromCartesian(rd, xd);
        System.out.println(" Zd = " + rd + " + j(" + xd + ")");
    }
}
