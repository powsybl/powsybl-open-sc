/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.ThreeSides;
import org.apache.commons.math3.complex.Complex;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class Feeder {

    //Feeder class is used to post process the results of a short circuit computation to get the feeder contribution in short-circuit current
    public Feeder(Complex zFeeder, String id, Feeder.FeederType feederType, ThreeSides side) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(feederType, "feederType");
        if (feederType == FeederType.BRANCH) {
            if (side == null) {
                throw new IllegalArgumentException("side is required for a BRANCH feeder (id=" + id + ")");
            }
        } else if (side != null) {
            throw new IllegalArgumentException("side must be null for a " + feederType + " feeder (id=" + id
                    + "), got " + side);
        }
        this.z = zFeeder;
        this.id = id;
        this.feederType = feederType;
        this.side = side;
    }

    public Feeder(Complex zFeeder, String id, Feeder.FeederType feederType) {
        this(zFeeder, id, feederType, null);
    }

    public enum FeederType {
        GENERATOR,
        SHUNT,
        CONTROLLED_SHUNT,
        LOAD,
        BRANCH
    }

    private final Complex z;

    private final String id; // id in LfNetwork

    private final Feeder.FeederType feederType;

    private final ThreeSides side;

    public ThreeSides getSide() {
        return side;
    }

    public Complex getZ() {
        return z;
    }

    public String getId() {
        return id;
    }

    public Feeder.FeederType getFeederType() {
        return feederType;
    }

}
