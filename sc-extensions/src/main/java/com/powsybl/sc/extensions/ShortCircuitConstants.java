/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class ShortCircuitConstants {

    private ShortCircuitConstants() {
    }

    public static final double POWER_EPSILON_MVA = 1e-6;
    public static final double CURRENT_EPSILON_PU = 1e-6;
    public static final double IMPEDANCE_EPSILON_PU = 1e-6;
    public static final double ADMITTANCE_EPSILON_PU = 1e-8;

    public static final double IMPEDANCE_CONSISTENCY_TOLERANCE_PU = 1e-5;

    public static final double DEFAULT_TRANS_RD = 0;
    public static final double DEFAULT_SUB_TRANS_RD = 0;
    public static final double DEFAULT_COEFF_K = 1;
    public static final double DEFAULT_COS_PHI = 0.85;
    public static final double DEFAULT_RATED_U = 100.;

    public static final double DEFAULT_TRANS_XD = 20.; //supposed in ohms // TODO : check most standard value
    public static final double DEFAULT_SUB_TRANS_XD = 20.; //supposed in ohms // TODO : check most standard value
    public static final double DEFAULT_STEP_UP_XD = 0.; //supposed in ohms // TODO : check most standard value

    public static final double DEFAULT_CQ = 1.1;
    public static final double DEFAULT_R1_X1_RATIO = 1.;
    public static final double DEFAULT_IKQ = 10;

    public static final MatrixFactory DEFAULT_MATRIX_FACTORY = new DenseMatrixFactory();
    public static final ShortCircuitAnalysisType DEFAULT_ANALYSIS_TYPE = ShortCircuitAnalysisType.SELECTIVE;
    public static final boolean DEFAULT_WITH_CAPACITIES = true;
    public static final boolean DEFAULT_WITH_RESISTANCES = true;

    public static final GeneratorSelectionMode DEFAULT_INNER_ZONE_GENERATOR_SELECTION_MODE = GeneratorSelectionMode.ALL;
    public static final GeneratorSelectionMode DEFAULT_OUTER_ZONE_GENERATOR_SELECTION_MODE = GeneratorSelectionMode.ABOVE_POWER_THRESHOLD;
    public static final double DEFAULT_RUNNING_GENERATOR_THRESHOLD = 0.0;
}
