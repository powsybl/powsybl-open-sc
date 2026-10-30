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
public class AdmittanceEquationTermX2 extends AbstractAdmittanceEquationTerm {

    public AdmittanceEquationTermX2(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<VariableType> variableSet,
                                    ImpedanceLinearResolutionParameters parameters, AdmittanceEquationSystem.FrequencyType frequencyType) {
        super(branch, bus1, bus2, variableSet, parameters, frequencyType);
    }

    @Override
    public double getCoefficient(Variable<VariableType> variable) {
        // I2x = -g21 * V1x + b21 * V1y + (g2 + g21)V2x - (b2 + b21)V2y
        if (variable.equals(v1rVar)) {
            return -admittance.getG21();
        } else if (variable.equals(v2rVar)) {
            return admittance.getG2g21sum();
        } else if (variable.equals(v1iVar)) {
            return admittance.getB21();
        } else if (variable.equals(v2iVar)) {
            return -admittance.getB2b21sum();
        } else {
            throw new IllegalArgumentException("Unknown variable " + variable);
        }
    }

    @Override
    protected String getName() {
        return "yr2";
    }
}
