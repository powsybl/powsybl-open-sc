/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

import static com.powsybl.openloadflow.util.PerUnit.SB;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */
public class BranchFeeder extends AbstractFeeder {

    private final LfBranch lfBranch;
    private final ThreeSides side;

    public BranchFeeder(Complex zFeeder, LfBranch branch, ThreeSides side) {
        super(zFeeder, branch.getId());
        this.lfBranch = branch;
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

        // TODO: Two current limitations:
        //  1. We use the LfNetwork to build the feeders
        //  2. LfBranch is not holding P and Q load flow results
        //  We should use the Network to create the feeders
        //  Reaching Branch through Feeder Id's is ugly and dangerous
        Branch<?> branch = network.getBranch(id);
        if (side == ThreeSides.ONE) {
            bus = lfBranch.getBus1();
            p = branch.getTerminal1().getP() / SB; // pu
            q = branch.getTerminal1().getQ() / SB; // pu
        }
        if (side == ThreeSides.TWO) {
            bus = lfBranch.getBus2();
            p = branch.getTerminal2().getP() / SB; // pu
            q = branch.getTerminal2().getQ() / SB; // pu
        }
        assert bus != null;

        double v = bus.getV(); // pu
        double phi = bus.getAngle(); // Rad

        return computeCurrent(v, phi, p, q);
    }
}
