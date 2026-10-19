/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import org.apache.commons.math3.complex.Complex;

import static com.powsybl.openloadflow.util.PerUnit.SB;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */
public class GeneratorFeeder extends AbstractFeeder {

    private final LfGenerator generator;

    public GeneratorFeeder(Complex zFeeder, LfGenerator generator) {
        super(zFeeder, generator.getId());
        this.generator = generator;
    }

    @Override
    public FeederType getFeederType() {
        return FeederType.GENERATOR;
    }

    @Override
    public Complex getInitialCurrentContribution(Network network) {
        LfBus bus = generator.getBus();
        double v = bus.getV(); // pu
        double phi = bus.getAngle(); // Rad

        // TODO: Two current limitations:
        //  1. We use the LfNetwork to build the feeders
        //  2. LfGenerator is not holding P and Q load flow results
        //  We should use the Network to create the feeders
        //  Reaching Generator through Feeder Id's is ugly and dangerous
        Terminal terminal = network.getGenerator(id).getTerminal();

        double p = terminal.getP() / SB; // pu
        double q = terminal.getQ() / SB; // pu

        return computeCurrent(v, phi, p, q);
    }
}
