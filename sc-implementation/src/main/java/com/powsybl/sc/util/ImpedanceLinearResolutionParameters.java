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
import com.powsybl.sc.extensions.GeneratorSelectionMode;
import com.powsybl.sc.implementation.ShortCircuitEngineParameters;
import com.powsybl.shortcircuit.StudyType;
import org.apache.commons.math3.complex.Complex;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.powsybl.sc.extensions.ShortCircuitConstants.*;
import static com.powsybl.shortcircuit.ShortCircuitConstants.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class ImpedanceLinearResolutionParameters {

    public static final double XSUBTRANSIENT = 0.2; //default value if data not available

    private final AcLoadFlowParameters acLoadFlowParameters;
    private final MatrixFactory matrixFactory;
    private final List<CalculationLocation> calculationLocations; // calculation locations where only one bus is required in input
    private final AdmittanceEquationSystem.AdmittanceType admittanceType;
    private final List<Complex> initialVoltages;

    private List<CalculationLocation> biphasedCalculationLocations = Collections.emptyList(); // calculation locations where 2 buses are required in input
    private boolean voltageUpdate = DEFAULT_WITH_VOLTAGE_RESULT;
    private StudyType studyType = StudyType.SUB_TRANSIENT;
    private boolean withShuntCompensators = DEFAULT_WITH_SHUNT_COMPENSATORS;
    private boolean withLoads = DEFAULT_WITH_LOADS;
    private boolean withCapacities = DEFAULT_WITH_CAPACITIES;
    private boolean withResistances = DEFAULT_WITH_RESISTANCES;
    private boolean withNeutralPosition = DEFAULT_WITH_NEUTRAL_POSITION;
    private Set<String> innerZoneBusIds = Collections.emptySet();
    private GeneratorSelectionMode innerZoneGeneratorSelectionMode = DEFAULT_INNER_ZONE_GENERATOR_SELECTION_MODE;
    private GeneratorSelectionMode outerZoneGeneratorSelectionMode = DEFAULT_OUTER_ZONE_GENERATOR_SELECTION_MODE;
    private double innerZoneRunningGeneratorThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;
    private double outerZoneRunningGeneratorThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;

    public ImpedanceLinearResolutionParameters(AcLoadFlowParameters acLoadFlowParameters, MatrixFactory matrixFactory,
                                               List<CalculationLocation> calculationLocations,
                                               AdmittanceEquationSystem.AdmittanceType admittanceType,
                                               List<Complex> initialVoltages) {
        this.acLoadFlowParameters = Objects.requireNonNull(acLoadFlowParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.calculationLocations = Objects.requireNonNull(calculationLocations);
        this.admittanceType = Objects.requireNonNull(admittanceType);
        this.initialVoltages = initialVoltages;
    }

    public ImpedanceLinearResolutionParameters(AcLoadFlowParameters acLoadFlowParameters, MatrixFactory matrixFactory,
                                               List<CalculationLocation> calculationLocations,
                                               List<CalculationLocation> biphasedCalculationLocations,
                                               AdmittanceEquationSystem.AdmittanceType admittanceType,
                                               List<Complex> initialVoltages) {
        this(acLoadFlowParameters, matrixFactory, calculationLocations, admittanceType, initialVoltages);
        setBiphasedCalculationLocations(biphasedCalculationLocations);
    }

    public ImpedanceLinearResolutionParameters(AcLoadFlowParameters acLoadFlowParameters, MatrixFactory matrixFactory,
                                               List<CalculationLocation> calculationLocations, ShortCircuitEngineParameters scParameters,
                                               AdmittanceEquationSystem.AdmittanceType admittanceType, List<Complex> initialVoltages) {
        this(acLoadFlowParameters, matrixFactory, calculationLocations, admittanceType, initialVoltages);
        copyFrom(scParameters);
    }

    public ImpedanceLinearResolutionParameters(AcLoadFlowParameters acLoadFlowParameters, MatrixFactory matrixFactory,
                                               List<CalculationLocation> calculationLocations, ShortCircuitEngineParameters scParameters,
                                               AdmittanceEquationSystem.AdmittanceType admittanceType,
                                               List<CalculationLocation> biphasedCalculationLocations, List<Complex> initialVoltages) {
        this(acLoadFlowParameters, matrixFactory, calculationLocations, scParameters, admittanceType, initialVoltages);
        setBiphasedCalculationLocations(biphasedCalculationLocations);
    }

    public ImpedanceLinearResolutionParameters(AcLoadFlowParameters acLoadFlowParameters, MatrixFactory matrixFactory,
                                               List<CalculationLocation> calculationLocations, TheveninEquivalentParameters thParameters,
                                               AdmittanceEquationSystem.AdmittanceType admittanceType, List<Complex> initialVoltages) {
        this(acLoadFlowParameters, matrixFactory, calculationLocations, admittanceType, initialVoltages);
        copyFrom(thParameters);
    }

    private void copyFrom(ShortCircuitEngineParameters scParameters) {
        Objects.requireNonNull(scParameters);
        setVoltageUpdate(scParameters.isVoltageUpdate())
                .setStudyType(scParameters.getStudyType())
                .setWithShuntCompensators(scParameters.isWithShuntCompensators())
                .setWithLoads(scParameters.isWithLoads())
                .setWithCapacities(scParameters.isWithCapacities())
                .setWithResistances(scParameters.isWithResistances())
                .setWithNeutralPosition(scParameters.isWithNeutralPosition())
                .setInnerZoneBusIds(scParameters.getInnerZoneBusIds())
                .setInnerZoneGeneratorSelectionMode(scParameters.getInnerZoneGeneratorSelectionMode())
                .setOuterZoneGeneratorSelectionMode(scParameters.getOuterZoneGeneratorSelectionMode())
                .setInnerZoneRunningGeneratorThreshold(scParameters.getInnerZoneRunningGeneratorThreshold())
                .setOuterZoneRunningGeneratorThreshold(scParameters.getOuterZoneRunningGeneratorThreshold());
    }

    private void copyFrom(TheveninEquivalentParameters thParameters) {
        Objects.requireNonNull(thParameters);
        setVoltageUpdate(thParameters.isVoltageUpdate())
                .setStudyType(thParameters.getTheveninStudyType())
                .setWithShuntCompensators(thParameters.isWithShuntCompensators())
                .setWithLoads(thParameters.isWithLoads())
                .setWithCapacities(thParameters.isWithCapacities())
                .setWithResistances(thParameters.isWithResistances())
                .setWithNeutralPosition(thParameters.isWithNeutralPosition())
                .setInnerZoneBusIds(thParameters.getInnerZoneBusIds())
                .setInnerZoneGeneratorSelectionMode(thParameters.getInnerZoneGeneratorSelectionMode())
                .setOuterZoneGeneratorSelectionMode(thParameters.getOuterZoneGeneratorSelectionMode())
                .setInnerZoneRunningGeneratorThreshold(thParameters.getInnerZoneRunningGeneratorThreshold())
                .setOuterZoneRunningGeneratorThreshold(thParameters.getOuterZoneRunningGeneratorThreshold());
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

    public AdmittanceEquationSystem.AdmittanceType getAdmittanceType() {
        return admittanceType;
    }

    public Complex getInitialVoltage(int num) {
        return initialVoltages.get(num);
    }

    public List<CalculationLocation> getBiphasedCalculationLocations() {
        return biphasedCalculationLocations;
    }

    public ImpedanceLinearResolutionParameters setBiphasedCalculationLocations(List<CalculationLocation> biphasedCalculationLocations) {
        this.biphasedCalculationLocations = Objects.requireNonNull(biphasedCalculationLocations);
        return this;
    }

    public boolean isVoltageUpdate() {
        return voltageUpdate;
    }

    public ImpedanceLinearResolutionParameters setVoltageUpdate(boolean voltageUpdate) {
        this.voltageUpdate = voltageUpdate;
        return this;
    }

    public StudyType getStudyType() {
        return studyType;
    }

    public ImpedanceLinearResolutionParameters setStudyType(StudyType studyType) {
        this.studyType = Objects.requireNonNull(studyType);
        return this;
    }

    public boolean isWithShuntCompensators() {
        return withShuntCompensators;
    }

    public ImpedanceLinearResolutionParameters setWithShuntCompensators(boolean withShuntCompensators) {
        this.withShuntCompensators = withShuntCompensators;
        return this;
    }

    public boolean isWithLoads() {
        return withLoads;
    }

    public ImpedanceLinearResolutionParameters setWithLoads(boolean withLoads) {
        this.withLoads = withLoads;
        return this;
    }

    public boolean isWithCapacities() {
        return withCapacities;
    }

    public ImpedanceLinearResolutionParameters setWithCapacities(boolean withCapacities) {
        this.withCapacities = withCapacities;
        return this;
    }

    public boolean isWithResistances() {
        return withResistances;
    }

    public ImpedanceLinearResolutionParameters setWithResistances(boolean withResistances) {
        this.withResistances = withResistances;
        return this;
    }

    public boolean isWithNeutralPosition() {
        return withNeutralPosition;
    }

    public ImpedanceLinearResolutionParameters setWithNeutralPosition(boolean withNeutralPosition) {
        this.withNeutralPosition = withNeutralPosition;
        return this;
    }

    public Set<String> getInnerZoneBusIds() {
        return innerZoneBusIds;
    }

    public ImpedanceLinearResolutionParameters setInnerZoneBusIds(Collection<String> innerZoneBusIds) {
        this.innerZoneBusIds = Set.copyOf(Objects.requireNonNull(innerZoneBusIds));
        return this;
    }

    public GeneratorSelectionMode getInnerZoneGeneratorSelectionMode() {
        return innerZoneGeneratorSelectionMode;
    }

    public ImpedanceLinearResolutionParameters setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode mode) {
        this.innerZoneGeneratorSelectionMode = Objects.requireNonNull(mode);
        return this;
    }

    public GeneratorSelectionMode getOuterZoneGeneratorSelectionMode() {
        return outerZoneGeneratorSelectionMode;
    }

    public ImpedanceLinearResolutionParameters setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode mode) {
        this.outerZoneGeneratorSelectionMode = Objects.requireNonNull(mode);
        return this;
    }

    public double getInnerZoneRunningGeneratorThreshold() {
        return innerZoneRunningGeneratorThreshold;
    }

    public ImpedanceLinearResolutionParameters setInnerZoneRunningGeneratorThreshold(double threshold) {
        this.innerZoneRunningGeneratorThreshold = threshold;
        return this;
    }

    public double getOuterZoneRunningGeneratorThreshold() {
        return outerZoneRunningGeneratorThreshold;
    }

    public ImpedanceLinearResolutionParameters setOuterZoneRunningGeneratorThreshold(double threshold) {
        this.outerZoneRunningGeneratorThreshold = threshold;
        return this;
    }
}
