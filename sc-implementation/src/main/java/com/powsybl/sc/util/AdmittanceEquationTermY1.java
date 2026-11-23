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
public class AdmittanceEquationTermY1 extends AbstractAdmittanceEquationTerm {

    public AdmittanceEquationTermY1(LfBranch branch, VariableSet<VariableType> variableSet,
                                    ShortCircuitParameters parameters,
                                    OpenShortCircuitParameters openScParameters,
                                    AdmittanceType admittanceType,
                                    FrequencyType frequencyType) {
        super(branch, variableSet, parameters, openScParameters, admittanceType, frequencyType);
    }

    @Override
    public double getCoefficient(Variable<VariableType> variable) {
        // I1y = (b1 + b12)V1x + (g1 + g12)V1y - b12 * V2x - g12 * V2y
        if (variable.equals(v1rVar)) {
            return branchAdmittance.getY1y12sum().getImaginary();
        } else if (variable.equals(v2rVar)) {
            return -branchAdmittance.getY12().getImaginary();
        } else if (variable.equals(v1iVar)) {
            return branchAdmittance.getY1y12sum().getReal();
        } else if (variable.equals(v2iVar)) {
            return -branchAdmittance.getY12().getReal();
        } else {
            throw new IllegalArgumentException("Unknown variable " + variable);
        }
    }

    @Override
    protected String getName() {
        return "yi1";
    }
}
