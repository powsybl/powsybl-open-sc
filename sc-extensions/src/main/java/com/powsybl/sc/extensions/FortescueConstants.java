/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
package com.powsybl.sc.extensions;

public final class FortescueConstants {

    private FortescueConstants() {
    }

    public static final boolean DEFAULT_TO_GROUND = false;
    public static final double DEFAULT_GROUNDING_R = 0.;
    public static final double DEFAULT_GROUNDING_X = 0.;
    public static final double DEFAULT_COEFF_RO = 1;
    public static final double DEFAULT_COEFF_XO = 1;
    public static final double DEFAULT_COEFF_RI = 1;
    public static final double DEFAULT_COEFF_XI = 1;
    public static final boolean DEFAULT_FREE_FLUXES = true;
    public static final LegConnectionType DEFAULT_LEG1_CONNECTION_TYPE = LegConnectionType.DELTA; // TODO : check if default connection acceptable
    public static final LegConnectionType DEFAULT_LEG2_CONNECTION_TYPE = LegConnectionType.Y_GROUNDED; // TODO : check if default connection acceptable
    public static final LegConnectionType DEFAULT_LEG3_CONNECTION_TYPE = LegConnectionType.DELTA; // TODO : check if default connection acceptable

    public static final GeneratorFortescue.GeneratorType DEFAULT_GENERATOR_FORTESCUE_TYPE = GeneratorFortescue.GeneratorType.ROTATING_MACHINE;
}
