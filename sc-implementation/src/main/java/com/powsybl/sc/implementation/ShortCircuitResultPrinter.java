package com.powsybl.sc.implementation;

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
