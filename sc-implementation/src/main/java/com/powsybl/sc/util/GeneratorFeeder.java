/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

import static com.powsybl.openloadflow.util.PerUnit.SB;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */
public class GeneratorFeeder extends AbstractFeeder {

    private final Generator generator;
    private final LfBus lfBus;

    public GeneratorFeeder(Complex yFeeder, Generator generator, LfBus lfBus) {
        super(yFeeder, generator.getId());
        this.generator = generator;
        this.lfBus = lfBus;
    }

    @Override
    public FeederType getFeederType() {
        return FeederType.GENERATOR;
    }

    @Override
    public Complex getInitialCurrentContribution(Network network) {
        double v = lfBus.getV(); // pu
        double phi = lfBus.getAngle(); // Rad

        double p = generator.getTerminal().getP() / SB; // pu
        double q = generator.getTerminal().getQ() / SB; // pu

        return computeCurrent(v, phi, p, q);
    }
}
