/**
 * Copyright (c) 2025, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.sc.util.FeederResult;
import com.powsybl.sc.util.FeedersAtBusResult;
import com.powsybl.shortcircuit.FortescueValue;

import static com.powsybl.sc.extensions.LoadShortCircuit.EPSILON;

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

    public void printEquivalentDirectImpedance20hzPu() {
        System.out.println("Equivalent Direct Thevenin Impedance at 20 Hz (Pu) : Zd = " + shortCircuitResult.getZd20hz() + "  ");
    }

    public void printTheveninVoltagePu() {
        System.out.println("Thevenin Voltage : Eth  (Pu) = " + shortCircuitResult.getEth() + "  ");
    }

    public void printEquivalentHomopolarImpedancePu() {
        System.out.println("Equivalent Homopolar Thevenin Impedance (Pu) : Zo = " + shortCircuitResult.getZh() + "  ");
    }

    public void printEquivalentHomopolarImpedance20hzPu() {
        System.out.println("Equivalent Homopolar Thevenin Impedance at 20 Hz (Pu) : Zo = " + shortCircuitResult.getZh20hz() + "  ");
    }

    public void printIk() {
        System.out.println("Ik (kA) = " + shortCircuitResult.getIk() + "   => |Ik| (kA) = " + shortCircuitResult.getIk().abs());
    }

    public void printSk() {
        System.out.println("Sk (MVA) = " + shortCircuitResult.getSk() + "   => |Sk| (MVA) = " + shortCircuitResult.getSk().abs());
    }

    public void printIpeakc() {
        System.out.println("Ipeak(c) (kA) = " + shortCircuitResult.getIpeakc());
    }

    public void printIfortescuePu() {
        System.out.println("Short Circuit Current at Bus (Pu) = " + shortCircuitResult.getLfBus().getId() + " : I = " + getStringFortescueValue(shortCircuitResult.getiFortescue()) + "  ");
    }

    public void printVfortescuePu() {
        System.out.println("Short Circuit Voltage at Bus (Pu) = " + shortCircuitResult.getLfBus().getId() + " : V = " + getStringFortescueValue(shortCircuitResult.getvFortescue()) + "  ");
    }

    public void printDvAtBussesPu() {
        System.out.println("---Bus voltage deltas :  ");
        for (LfBus bus : shortCircuitResult.getLfNetwork().getBuses()) {
            int busNum = bus.getNum();
            System.out.println("  -> dV(" + bus.getId() + ") = " + getStringFortescueValue(shortCircuitResult.getBusNum2Dv().get(busNum)) + " (Pu) ");
        }
    }

    public void printDIAtBranchPu() {
        System.out.println("---Branch currents :  ");
        for (LfBranch branch : shortCircuitResult.getLfNetwork().getBranches()) {
            //int busNum = bus.getNum();
            System.out.println("  -> dI1(" + branch.getId() + ") = " + getStringFortescueValue(shortCircuitResult.getBranchDi1().get(branch)) + " (Pu) ");
            System.out.println("  -> dI2(" + branch.getId() + ") = " + getStringFortescueValue(shortCircuitResult.getBranchDi2().get(branch)) + " (Pu) ");
            System.out.println(" ");
        }
    }

    public void printFeedersPu() {
        System.out.println("---Feeders : ");
        for (LfBus bus : shortCircuitResult.getLfNetwork().getBuses()) {
            FeedersAtBusResult feedBus = shortCircuitResult.getFeedersResultDirect().get(bus);
            for (FeederResult fr : feedBus.getBusFeedersResult()) {
                if (fr.getIContribution().abs() > EPSILON) {
                    System.out.println("  -> Direct Feeder " + fr.getFeeder().getFeederType() + " : " + fr.getFeeder().getId() + " has I (Pu) contribution  =  " + fr.getIContribution());
                }
            }

            if (shortCircuitResult.getShortCircuitFault().getType() == ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND) {
                continue;
            }

            feedBus = shortCircuitResult.getFeedersResultsHomopolar().get(bus);
            //System.out.println("---Feeders Homopolar at bus :  " + bus.getId());
            for (FeederResult fr : feedBus.getBusFeedersResult()) {
                if (fr.getIContribution().abs() > EPSILON) {
                    System.out.println("  -> Homopolar Feeder " + fr.getFeeder().getFeederType() + " : " + fr.getFeeder().getId() + " has I (Pu) contribution  =  " + fr.getIContribution());
                }
            }

            feedBus = shortCircuitResult.getFeedersResultsInverse().get(bus);
            //System.out.println("---Feeders Inverse at bus :  " + bus.getId());
            for (FeederResult fr : feedBus.getBusFeedersResult()) {
                if (fr.getIContribution().abs() > EPSILON) {
                    System.out.println("  -> Inverse Feeder " + fr.getFeeder().getFeederType() + " : " + fr.getFeeder().getId() + " has I (Pu) contribution  =  " + fr.getIContribution());
                }
            }
            System.out.println("  ---- ");
        }
    }

    public void printShortCircuitResult() {
        System.out.println("--------------- " + shortCircuitResult.getShortCircuitFault().getFaultId() + " --------------- ");
        System.out.println("Short Circuit at Bus = " + shortCircuitResult.getLfBus().getId());
        printEquivalentDirectImpedancePu();
        printEquivalentHomopolarImpedancePu();
        printEquivalentDirectImpedance20hzPu();
        printEquivalentHomopolarImpedance20hzPu();
        printTheveninVoltagePu();
        printIfortescuePu();
        printVfortescuePu();
        printIk();
        printSk();
        printIpeakc();
        if (shortCircuitResult.isVoltageProfileUpdated()) {
            printDvAtBussesPu();
            printDIAtBranchPu();
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
