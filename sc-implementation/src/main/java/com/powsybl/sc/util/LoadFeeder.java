package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

public class LoadFeeder extends AbstractFeeder {

    private final LfBus bus;

    public LoadFeeder(Complex zFeeder, String id, LfBus bus) {
        super(zFeeder, id);
        this.bus = bus;
    }

    @Override
    public FeederType getFeederType() {
        return FeederType.LOAD;
    }

    @Override
    public Complex getInitialCurrentContribution(Network network) {
        double v = bus.getV() * bus.getNominalV(); // kV
        double phi = bus.getAngle(); // Rad

        double p = bus.getLoadTargetP();
        double q = bus.getLoadTargetQ();

        return computeCurrent(v, phi, p, q);
    }
}
