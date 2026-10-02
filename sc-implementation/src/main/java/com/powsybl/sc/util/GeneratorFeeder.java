package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import org.apache.commons.math3.complex.Complex;

public class GeneratorFeeder extends AbstractFeeder {

    private final LfGenerator generator;

    public GeneratorFeeder(Complex zFeeder, String id, LfGenerator generator) {
        super(zFeeder, id);
        this.generator = generator;
    }

    @Override
    public FeederType getFeederType() {
        return FeederType.GENERATOR;
    }

    @Override
    public Complex getInitialCurrentContribution(Network network) {
        LfBus bus = generator.getBus();
        double v = bus.getV() * bus.getNominalV(); // kV
        double phi = bus.getAngle(); // Rad

        // TODO: Two current limitations:
        //  1. We use the LfNetwork to build the feeders
        //  2. LfGenerator is not holding P and Q load flow results
        //  We should use the Network to create the feeders
        //  Reaching Generator through Feeder Id's is ugly and dangerous
        Terminal terminal = network.getGenerator(id).getTerminal();

        double p = terminal.getP();
        double q = terminal.getQ();

        return computeCurrent(v, phi, p, q);
    }
}
