/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
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
