/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AdmittanceEquationTermY1 extends AbstractAdmittanceEquationTerm {

    public AdmittanceEquationTermY1(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<VariableType> variableSet,
                                    ImpedanceLinearResolutionParameters parameters, AdmittanceEquationSystem.FrequencyType frequencyType) {
        super(branch, bus1, bus2, variableSet, parameters, frequencyType);
    }

    @Override
    public double getCoefficient(Variable<VariableType> variable) {
        // I1y = (b1 + b12)V1x + (g1 + g12)V1y - b12 * V2x - g12 * V2y
        if (variable.equals(v1rVar)) {
            return admittance.getB1b12sum();
        } else if (variable.equals(v2rVar)) {
            return -admittance.getB12();
        } else if (variable.equals(v1iVar)) {
            return admittance.getG1g12sum();
        } else if (variable.equals(v2iVar)) {
            return -admittance.getG12();
        } else {
            throw new IllegalArgumentException("Unknown variable " + variable);
        }
    }

    @Override
    protected String getName() {
        return "yi1";
    }
}
