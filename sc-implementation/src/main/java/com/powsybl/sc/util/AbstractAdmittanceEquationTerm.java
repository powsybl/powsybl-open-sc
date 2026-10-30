/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.openloadflow.equations.AbstractElementEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.*;

import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public abstract class AbstractAdmittanceEquationTerm extends AbstractElementEquationTerm<LfBranch, VariableType, EquationType> implements LinearEquationTerm {

    protected final Variable<VariableType> v1rVar;

    protected final Variable<VariableType> v1iVar;

    protected final Variable<VariableType> v2rVar;

    protected final Variable<VariableType> v2iVar;

    protected final List<Variable<VariableType>> variables;

    protected final BranchAdmittance admittance;

    protected AbstractAdmittanceEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<VariableType> variableSet, ImpedanceLinearResolutionParameters parameters, AdmittanceEquationSystem.FrequencyType frequencyType) {
        super(branch);
        Objects.requireNonNull(bus1);
        Objects.requireNonNull(bus2);
        Objects.requireNonNull(variableSet);

        v1rVar = variableSet.getVariable(bus1.getNum(), VariableType.BUS_VR);
        v2rVar = variableSet.getVariable(bus2.getNum(), VariableType.BUS_VR);
        v1iVar = variableSet.getVariable(bus1.getNum(), VariableType.BUS_VI);
        v2iVar = variableSet.getVariable(bus2.getNum(), VariableType.BUS_VI);

        variables = List.of(v1rVar, v2rVar, v1iVar, v2iVar);

        admittance = BranchAdmittance.create(branch, parameters, frequencyType);
    }

    @Override
    public List<Variable<VariableType>> getVariables() {
        return variables;
    }

    @Override
    public ElementType getElementType() {
        return ElementType.BRANCH;
    }

    @Override
    public int getElementNum() {
        return element.getNum();
    }

    @Override
    public double eval() {
        throw new UnsupportedOperationException("Not needed");
    }

    @Override
    public double der(Variable<VariableType> variable) {
        throw new UnsupportedOperationException("Not needed");
    }

}
