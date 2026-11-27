/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

import static com.powsybl.openloadflow.util.PerUnit.SB;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */
public class ShuntFeeder extends AbstractFeeder {

    private final ShuntCompensator shuntCompensator;
    private final LfBus lfBus;

    public ShuntFeeder(Complex yFeeder, ShuntCompensator shuntCompensator, LfBus lfBus) {
        super(yFeeder, shuntCompensator.getId());
        this.shuntCompensator = shuntCompensator;
        this.lfBus = lfBus;
    }

    @Override
    public FeederType getFeederType() {
        return shuntCompensator.isRegulatingWithMode(RegulationMode.VOLTAGE) ? FeederType.CONTROLLER_SHUNT : FeederType.SHUNT;
    }

    @Override
    public Complex getInitialCurrentContribution(Network network) {
        double v = lfBus.getV(); // pu
        double phi = lfBus.getAngle(); // Rad

        double p = shuntCompensator.getTerminal().getP() / SB; // pu
        double q = shuntCompensator.getTerminal().getQ() / SB; // pu

        return computeCurrent(v, phi, p, q);
    }
}
