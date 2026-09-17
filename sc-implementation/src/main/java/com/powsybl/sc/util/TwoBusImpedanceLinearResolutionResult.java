package com.powsybl.sc.util;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

import java.util.Map;

public class TwoBusImpedanceLinearResolutionResult {

    private final LfBus bus2;

    private final int numBus2Fault; // stored to easily access the extraction vector at bus2 to get the full voltage export if required

    private final Complex v2;

    private final Complex z22; //additional impedance matrix terms to keep as they are needed for biphased common support faults
    private final Complex z21;
    private final Complex z12;

    private final Complex z22At20Hz;
    private final Complex z21At20Hz;
    private final Complex z12At20Hz;

    private Map<Integer, Complex> bus2ToZknf;

    TwoBusImpedanceLinearResolutionResult(LfBus bus2, Complex v2, Complex z22, Complex z21, Complex z12,
                                          Complex z22At20Hz, Complex z21At20Hz, Complex z12At20Hz, int numBus2Fault) {
        this.bus2 = bus2;

        this.numBus2Fault = numBus2Fault;

        this.v2 = v2;

        this.z22 = z22;
        this.z21 = z21;
        this.z12 = z12;

        this.z22At20Hz = z22At20Hz;
        this.z21At20Hz = z21At20Hz;
        this.z12At20Hz = z12At20Hz;
    }

    public void updateWithVoltagesdelta2(AdmittanceMatrix y, DenseMatrix dEn) {
        bus2ToZknf = y.getDeltaV(dEn, numBus2Fault);
        //eqSysFeeders = feeders; // TODO : check if feeder are necessary for v2 : contains necessary data to update the contribution of feeders for each shortcircuit
    }

    public LfBus getBus2() {
        return bus2;
    }

    public Complex getZ12() {
        return z12;
    }

    public Complex getZ21() {
        return z21;
    }

    public Complex getZ22() {
        return z22;
    }

    public Complex getZ22At20Hz() {
        return z22At20Hz;
    }

    public Complex getZ21At20Hz() {
        return z21At20Hz;
    }

    public Complex getZ12At20Hz() {
        return z12At20Hz;
    }

    public Complex getV2() {
        return v2;
    }

    public Map<Integer, Complex> getBus2ToZknf() {
        return bus2ToZknf;
    }
}
