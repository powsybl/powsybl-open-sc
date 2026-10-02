package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfShunt;
import org.apache.commons.math3.complex.Complex;

public class ShuntFeeder extends AbstractFeeder {

    private final LfShunt shunt;
    private final LfBus bus;
    private final FeederType shuntType;

    public ShuntFeeder(Complex zFeeder, String id, LfShunt shunt, LfBus bus, FeederType shuntType) {
        super(zFeeder, id);
        this.shunt = shunt;
        this.bus = bus;
        this.shuntType = shuntType;
    }

    @Override
    public FeederType getFeederType() {
        return shuntType;
    }

    @Override
    public Complex getInitialCurrentContribution(Network network) {
        double v = bus.getV() * bus.getNominalV(); // kV
        double phi = bus.getAngle(); // Rad

        double p = shunt.getP().eval();
        double q = shunt.getQ().eval();

        return computeCurrent(v, phi, p, q);
    }
}
