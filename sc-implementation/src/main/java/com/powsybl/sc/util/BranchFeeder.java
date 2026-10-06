/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class BranchFeeder extends AbstractFeeder {

    private final LfBranch branch;
    private final ThreeSides side;

    public BranchFeeder(Complex zFeeder, String id, LfBranch branch, ThreeSides side) {
        super(zFeeder, id);
        this.branch = branch;
        this.side = side;
    }

    @Override
    public ThreeSides getSide() {
        return side;
    }

    @Override
    public FeederType getFeederType() {
        return FeederType.BRANCH;
    }

    @Override
    public Complex getInitialCurrentContribution(Network network) {
        LfBus bus = null;
        double p = 0;
        double q = 0;
        if (side == ThreeSides.ONE) {
            bus = branch.getBus1();
            p = branch.getP1().eval();
            q = branch.getQ1().eval();
        }
        if (side == ThreeSides.TWO) {
            bus = branch.getBus2();
            p = branch.getP2().eval();
            q = branch.getQ2().eval();
        }
        assert bus != null;

        double v = bus.getV() * bus.getNominalV(); // kV
        double phi = bus.getAngle(); // Rad

        return computeCurrent(v, phi, p, q);
    }
}
