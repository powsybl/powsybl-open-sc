/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Hadrien Godard <hadrien.godard at artelys.com>
 */
public class ShuntFeeder extends AbstractFeeder {

    private final LfBus lfBus;
    private final FeederType shuntType;

    // Note: strings used when LfShuntImpl/LfShunt ids are built in
    // https://github.com/powsybl/powsybl-open-loadflow/blob/main/src/main/java/com/powsybl/openloadflow/network/impl/LfShuntImpl.java
    private static final String SHUNT_ID_SUFFIX = "_shunt_compensators";
    private static final String CONTROLLER_SHUNT_ID_SUFFIX = "_controller_shunt_compensators";

    public ShuntFeeder(Complex zFeeder, String id, LfBus bus, FeederType shuntType) {
        super(zFeeder, id);
        this.lfBus = bus;
        this.shuntType = shuntType;
    }

    @Override
    public FeederType getFeederType() {
        return shuntType;
    }

    @Override
    public Complex getInitialCurrentContribution(Network network) {
        double v = lfBus.getV(); // pu
        double phi = lfBus.getAngle(); // Rad

        // TODO: Two current limitations:
        //  1. We use the LfNetwork to build the feeders
        //  2. LfShunt is not holding P and Q load flow results
        //  We should use the Network to create the feeders
        //  Reaching Bus through Feeder Id's is ugly and dangerous
        String busId = getBusId();
        Bus bus = network.getBusView().getBus(busId);

        double p = 0.;
        double q = 0.;
        for (ShuntCompensator shunt : bus.getShuntCompensators()) {
            p += shunt.getTerminal().getP();
            q += shunt.getTerminal().getQ();
        }

        return computeCurrent(v, phi, p, q);
    }

    private String getBusId() {
        String suffix = "";
        if (shuntType == FeederType.SHUNT) {
            suffix = SHUNT_ID_SUFFIX;
        } else if (shuntType == FeederType.CONTROLLER_SHUNT) {
            suffix = CONTROLLER_SHUNT_ID_SUFFIX;
        }

        return id.substring(0, id.length() - suffix.length());
    }
}
