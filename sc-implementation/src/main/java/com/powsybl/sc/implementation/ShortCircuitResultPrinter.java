/**
 * Copyright (c) 2025, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.sc.util.FeederResult;
import com.powsybl.sc.util.FeedersAtBusResult;
import com.powsybl.shortcircuit.FortescueValue;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitResultPrinter {

    private ShortCircuitResult shortCircuitResult;

    ShortCircuitResultPrinter(ShortCircuitResult scr) {
        shortCircuitResult = scr;
    }

    public void printEquivalentDirectImpedancePu() {
        System.out.println("Equivalent Direct Thevenin Impedance (Pu) : Zd = " + shortCircuitResult.getZd() + "  ");
    }

    public void printTheveninVoltagePu() {
        System.out.println("Thevenin Voltage : Eth  (Pu) = " + shortCircuitResult.getZd() + "  ");
    }

    public void printIfortescuePu() {
        System.out.println("Short Circuit Current at Bus (Pu) = " + shortCircuitResult.getLfBus().getId() + " : I = " + getStringFortescueValue(shortCircuitResult.getiFortescue()) + "  ");
    }

    public void printVfortescuePu() {
        System.out.println("Short Circuit Voltage at Bus (Pu) = " + shortCircuitResult.getLfBus().getId() + " : V = " + getStringFortescueValue(shortCircuitResult.getvFortescue()) + "  ");
    }

    public void printDvAtBussesPu() {
        for (LfBus bus : shortCircuitResult.getLfNetwork().getBuses()) {
            int busNum = bus.getNum();
            System.out.println("  -> dV(" + bus.getId() + ") = " + getStringFortescueValue(shortCircuitResult.getBusNum2Dv().get(busNum)) + " (Pu) ");
        }
    }

    public void printFeedersPu() {
        for (LfBus bus : shortCircuitResult.getLfNetwork().getBuses()) {
            FeedersAtBusResult feedBus = shortCircuitResult.getFeedersResultDirect().get(bus);
            System.out.println("---Feeders at bus :  " + bus.getId());
            for (FeederResult fr : feedBus.getBusFeedersResult()) {
                System.out.println("  -> Feeder " + fr.getFeeder().getFeederType() + " : " + fr.getFeeder().getId() + " has I (Pu) contribution  =  " + fr.getIContribution());
            }
        }
    }

    public void printShortCircuitResult() {
        System.out.println("-------------------------- ");
        System.out.println("Short Circuit at Bus = " + shortCircuitResult.getLfBus().getId());
        printEquivalentDirectImpedancePu();
        printTheveninVoltagePu();
        printIfortescuePu();
        printVfortescuePu();
        if (shortCircuitResult.isVoltageProfileUpdated()) {
            printDvAtBussesPu();
            printFeedersPu();
        }
    }

    public String getStringFortescueValue(FortescueValue fv) {
        String s = "[ " + fv.getZeroMagnitude() + " < " + fv.getZeroAngle() + " ; "
                        + fv.getPositiveMagnitude() + " < " + fv.getPositiveAngle() + " ; "
                        + fv.getNegativeMagnitude() + " < " + fv.getNegativeAngle() + " ] ";
        return s;
    }
}
