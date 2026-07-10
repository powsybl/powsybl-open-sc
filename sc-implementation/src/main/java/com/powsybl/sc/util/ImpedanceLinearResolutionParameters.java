/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.sc.implementation.ShortCircuitEngineParameters;
import org.apache.commons.math3.complex.Complex;

import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ImpedanceLinearResolutionParameters {

    public static final double XSUBTRANSIENT = 0.2; //default value if data not available

    private final boolean voltageUpdate;

    private final AcLoadFlowParameters acLoadFlowParameters;

    private final MatrixFactory matrixFactory;

    private final List<CalculationLocation> calculationLocations; // stores all calculation locations where only one bus is required in input

    private List<CalculationLocation> biphasedCalculationLocations; // stores all calculation locations where 2 busses are required in input

    private final boolean ignoreShunts;

    private final AdmittanceEquationSystem.AdmittancePeriodType periodType;

    private final AdmittanceEquationSystem.AdmittanceType admittanceType;

    private final List<Complex> initialVoltages;

    private final boolean isWithNeutralPosition;

    public ImpedanceLinearResolutionParameters(AcLoadFlowParameters acLoadFlowParameters, MatrixFactory matrixFactory, List<CalculationLocation> calculationLocations, ShortCircuitEngineParameters scParameters,
                                               AdmittanceEquationSystem.AdmittanceType admittanceType, List<Complex> initialVoltages) {
        this.acLoadFlowParameters = Objects.requireNonNull(acLoadFlowParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.calculationLocations = Objects.requireNonNull(calculationLocations);
        this.voltageUpdate = scParameters.isVoltageUpdate();
        this.ignoreShunts = scParameters.isIgnoreShunts();
        this.periodType = getAdmittancePeriodTypeFromParam(scParameters);
        this.admittanceType = admittanceType;
        this.initialVoltages = initialVoltages;
        this.isWithNeutralPosition = scParameters.isWithNeutralPosition();
    }

    public ImpedanceLinearResolutionParameters(AcLoadFlowParameters acLoadFlowParameters, MatrixFactory matrixFactory, List<CalculationLocation> calculationLocations, ShortCircuitEngineParameters scParameters,
                                               AdmittanceEquationSystem.AdmittanceType admittanceType, List<CalculationLocation> biphasedVoltageLevelLocation, List<Complex> initialVoltages) {
        this(acLoadFlowParameters, matrixFactory, calculationLocations, scParameters, admittanceType, initialVoltages);
        this.biphasedCalculationLocations = biphasedVoltageLevelLocation;
    }

    public ImpedanceLinearResolutionParameters(AcLoadFlowParameters acLoadFlowParameters, MatrixFactory matrixFactory, List<CalculationLocation> calculationLocations, TheveninEquivalentParameters thParameters,
                                               AdmittanceEquationSystem.AdmittanceType admittanceType, List<Complex> initialVoltages) {
        this.acLoadFlowParameters = Objects.requireNonNull(acLoadFlowParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.calculationLocations = Objects.requireNonNull(calculationLocations);
        this.voltageUpdate = thParameters.isVoltageUpdate();
        this.ignoreShunts = thParameters.isTheveninIgnoreShunts();
        this.periodType = getAdmittancePeriodTypeFromParamThevenin(thParameters);
        this.admittanceType = admittanceType;
        this.initialVoltages = initialVoltages;
        this.isWithNeutralPosition = false;
    }

    public AcLoadFlowParameters getAcLoadFlowParameters() {
        return acLoadFlowParameters;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public List<CalculationLocation> getCalculationLocations() {
        return calculationLocations;
    }

    public boolean isVoltageUpdate() {
        return voltageUpdate;
    }

    public boolean isTheveninIgnoreShunts() {
        return ignoreShunts;
    }

    public List<CalculationLocation> getBiphasedCalculationLocations() {
        return biphasedCalculationLocations;
    }

    public AdmittanceEquationSystem.AdmittancePeriodType getTheveninPeriodType() {
        return periodType;
    }

    public AdmittanceEquationSystem.AdmittanceType getAdmittanceType() {
        return admittanceType;
    }

    public Complex getInitialVoltage(int num) {
        return initialVoltages.get(num);
    }

    public boolean isWithNeutralPosition() {
        return isWithNeutralPosition;
    }

    private AdmittanceEquationSystem.AdmittancePeriodType getAdmittancePeriodTypeFromParam(ShortCircuitEngineParameters scParameters) {
        return switch (scParameters.getPeriodType()) {
            case STEADY_STATE -> AdmittanceEquationSystem.AdmittancePeriodType.ADM_STEADY_STATE;
            case SUB_TRANSIENT -> AdmittanceEquationSystem.AdmittancePeriodType.ADM_SUB_TRANSIENT;
            case TRANSIENT -> AdmittanceEquationSystem.AdmittancePeriodType.ADM_TRANSIENT;
        };
    }

    private AdmittanceEquationSystem.AdmittancePeriodType getAdmittancePeriodTypeFromParamThevenin(TheveninEquivalentParameters theParameters) {
        return switch (theParameters.getTheveninPeriodType()) {
            case THEVENIN_STEADY_STATE -> AdmittanceEquationSystem.AdmittancePeriodType.ADM_STEADY_STATE;
            case THEVENIN_SUB_TRANSIENT -> AdmittanceEquationSystem.AdmittancePeriodType.ADM_SUB_TRANSIENT;
            case THEVENIN_TRANSIENT -> AdmittanceEquationSystem.AdmittancePeriodType.ADM_TRANSIENT;
        };
    }
}
