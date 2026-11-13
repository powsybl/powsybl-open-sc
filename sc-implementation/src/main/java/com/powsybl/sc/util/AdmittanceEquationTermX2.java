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
import com.powsybl.sc.extensions.FrequencyType;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AdmittanceEquationTermX2 extends AbstractAdmittanceEquationTerm {

    public AdmittanceEquationTermX2(LfBranch branch, VariableSet<VariableType> variableSet,
                                    ShortCircuitParameters parameters,
                                    OpenShortCircuitParameters openScParameters,
                                    AdmittanceType admittanceType,
                                    FrequencyType frequencyType) {
        super(branch, variableSet, parameters, openScParameters, admittanceType, frequencyType);
    }

    @Override
    public double getCoefficient(Variable<VariableType> variable) {
        // I2x = -g21 * V1x + b21 * V1y + (g2 + g21)V2x - (b2 + b21)V2y
        if (variable.equals(v1rVar)) {
            return -branchAdmittance.getY21().getReal();
        } else if (variable.equals(v2rVar)) {
            return branchAdmittance.getY2y21sum().getReal();
        } else if (variable.equals(v1iVar)) {
            return branchAdmittance.getY21().getImaginary();
        } else if (variable.equals(v2iVar)) {
            return -branchAdmittance.getY2y21sum().getImaginary();
        } else {
            throw new IllegalArgumentException("Unknown variable " + variable);
        }
    }

    @Override
    protected String getName() {
        return "yr2";
    }
}
