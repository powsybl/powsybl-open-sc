/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

import com.powsybl.iidm.network.extensions.WindingConnectionType;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ScTransfo2W {

    private final WindingConnectionType leg1ConnectionType;
    private final WindingConnectionType leg2ConnectionType;

    private final double xo; // Xo : value of the homopolar admittance (in pu) expressed at the leg2 side
    private final double ro; // Ro : value of the homopolar resistance (in pu) expressed at the leg2 side

    private final boolean freeFluxes;

    private final double r1Ground;
    private final double x1Ground;
    private final double r2Ground;
    private final double x2Ground;

    /*ScTransfo2W(LegConnectionType leg1ConnectionType, LegConnectionType leg2ConnectionType, double coeffRo, double coeffXo, boolean freeFluxes) {
        this(leg1ConnectionType, leg2ConnectionType, coeffRo, coeffXo, freeFluxes, 1d, 0., 0., 0., 0.);
    }*/

    ScTransfo2W(WindingConnectionType leg1ConnectionType, WindingConnectionType leg2ConnectionType, double ro, double xo, boolean freeFluxes,
                double r1Ground, double x1Ground, double r2Ground, double x2Ground) {
        this.leg1ConnectionType = Objects.requireNonNull(leg1ConnectionType);
        this.leg2ConnectionType = Objects.requireNonNull(leg2ConnectionType);
        this.ro = ro;
        this.xo = xo;
        this.freeFluxes = freeFluxes;
        this.r1Ground = r1Ground;
        this.r2Ground = r2Ground;
        this.x1Ground = x1Ground;
        this.x2Ground = x2Ground;
    }

    public WindingConnectionType getLeg1ConnectionType() {
        return leg1ConnectionType;
    }

    public WindingConnectionType getLeg2ConnectionType() {
        return leg2ConnectionType;
    }

    public double getXo() {
        return xo;
    }

    public double getRo() {
        return ro;
    }

    public boolean isFreeFluxes() {
        return freeFluxes;
    }

    public double getR1Ground() {
        return r1Ground;
    }

    public double getX1Ground() {
        return x1Ground;
    }

    public double getX2Ground() {
        return x2Ground;
    }

    public double getR2Ground() {
        return r2Ground;
    }
}
