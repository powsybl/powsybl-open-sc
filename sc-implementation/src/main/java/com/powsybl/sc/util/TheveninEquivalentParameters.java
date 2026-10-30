/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.sc.extensions.GeneratorSelectionMode;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.StudyType;

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
public class TheveninEquivalentParameters {

    public static final double XSUBTRANSIENT = 0.2; //default value if data not available

    private final LoadFlowParameters loadFlowParameters;
    private final MatrixFactory matrixFactory;
    private final List<CalculationLocation> theveninCalculationLocation;

    private boolean voltageUpdate = DEFAULT_WITH_VOLTAGE_RESULT;
    private InitialVoltageProfileMode theveninVoltageProfileType = DEFAULT_INITIAL_VOLTAGE_PROFILE_MODE;
    private StudyType theveninStudyType = DEFAULT_STUDY_TYPE;
    private boolean withShuntCompensators = DEFAULT_WITH_SHUNT_COMPENSATORS;
    private boolean withLoads = DEFAULT_WITH_LOADS;
    private boolean withCapacities = DEFAULT_WITH_CAPACITIES;
    private boolean withResistances = DEFAULT_WITH_RESISTANCES;
    private boolean withNeutralPosition = DEFAULT_WITH_NEUTRAL_POSITION;
    private Set<String> innerZoneBusIds = Collections.emptySet();
    private GeneratorSelectionMode innerZoneGeneratorSelectionMode = DEFAULT_INNER_ZONE_GENERATOR_SELECTION_MODE;
    private GeneratorSelectionMode outerZoneGeneratorSelectionMode = DEFAULT_OUTER_ZONE_GENERATOR_SELECTION_MODE;
    private double innerZoneGeneratorPowerThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;
    private double outerZoneGeneratorPowerThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;

    public TheveninEquivalentParameters(LoadFlowParameters loadFlowParameters, MatrixFactory matrixFactory,
                                        List<CalculationLocation> locations) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.theveninCalculationLocation = Objects.requireNonNull(locations);
    }

    public LoadFlowParameters getLoadFlowParameters() {
        return loadFlowParameters;
    }

    public AcLoadFlowParameters getAcLoadFlowParameters() {
        return getAcLoadFlowParametersFromParam(loadFlowParameters, matrixFactory);
    }

    private AcLoadFlowParameters getAcLoadFlowParametersFromParam(LoadFlowParameters loadFlowParameters, MatrixFactory matrixFactory) {
        OpenLoadFlowParameters loadflowParametersExt = OpenLoadFlowParameters.get(loadFlowParameters);
        return OpenLoadFlowParameters.createAcParameters(loadFlowParameters, loadflowParametersExt, matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public List<CalculationLocation> getLocations() {
        return theveninCalculationLocation;
    }

    public boolean isVoltageUpdate() {
        return voltageUpdate;
    }

    public TheveninEquivalentParameters setVoltageUpdate(boolean voltageUpdate) {
        this.voltageUpdate = voltageUpdate;
        return this;
    }

    public InitialVoltageProfileMode getTheveninVoltageProfileType() {
        return theveninVoltageProfileType;
    }

    public TheveninEquivalentParameters setTheveninVoltageProfileType(InitialVoltageProfileMode theveninVoltageProfileType) {
        this.theveninVoltageProfileType = Objects.requireNonNull(theveninVoltageProfileType);
        return this;
    }

    public StudyType getTheveninStudyType() {
        return theveninStudyType;
    }

    public TheveninEquivalentParameters setTheveninStudyType(StudyType theveninStudyType) {
        this.theveninStudyType = Objects.requireNonNull(theveninStudyType);
        return this;
    }

    public boolean isWithShuntCompensators() {
        return withShuntCompensators;
    }

    public TheveninEquivalentParameters setWithShuntCompensators(boolean withShuntCompensators) {
        this.withShuntCompensators = withShuntCompensators;
        return this;
    }

    public boolean isWithLoads() {
        return withLoads;
    }

    public TheveninEquivalentParameters setWithLoads(boolean withLoads) {
        this.withLoads = withLoads;
        return this;
    }

    public boolean isWithCapacities() {
        return withCapacities;
    }

    public TheveninEquivalentParameters setWithCapacities(boolean withCapacities) {
        this.withCapacities = withCapacities;
        return this;
    }

    public boolean isWithResistances() {
        return withResistances;
    }

    public TheveninEquivalentParameters setWithResistances(boolean withResistances) {
        this.withResistances = withResistances;
        return this;
    }

    public boolean isWithNeutralPosition() {
        return withNeutralPosition;
    }

    public TheveninEquivalentParameters setWithNeutralPosition(boolean withNeutralPosition) {
        this.withNeutralPosition = withNeutralPosition;
        return this;
    }

    public Set<String> getInnerZoneBusIds() {
        return innerZoneBusIds;
    }

    public TheveninEquivalentParameters setInnerZoneBusIds(Collection<String> innerZoneBusIds) {
        this.innerZoneBusIds = Set.copyOf(Objects.requireNonNull(innerZoneBusIds));
        return this;
    }

    public GeneratorSelectionMode getInnerZoneGeneratorSelectionMode() {
        return innerZoneGeneratorSelectionMode;
    }

    public TheveninEquivalentParameters setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode mode) {
        this.innerZoneGeneratorSelectionMode = Objects.requireNonNull(mode);
        return this;
    }

    public GeneratorSelectionMode getOuterZoneGeneratorSelectionMode() {
        return outerZoneGeneratorSelectionMode;
    }

    public TheveninEquivalentParameters setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode mode) {
        this.outerZoneGeneratorSelectionMode = Objects.requireNonNull(mode);
        return this;
    }

    public double getInnerZoneGeneratorPowerThreshold() {
        return innerZoneGeneratorPowerThreshold;
    }

    public TheveninEquivalentParameters setInnerZoneGeneratorPowerThreshold(double threshold) {
        this.innerZoneGeneratorPowerThreshold = threshold;
        return this;
    }

    public double getOuterZoneGeneratorPowerThreshold() {
        return outerZoneGeneratorPowerThreshold;
    }

    public TheveninEquivalentParameters setOuterZoneGeneratorPowerThreshold(double threshold) {
        this.outerZoneGeneratorPowerThreshold = threshold;
        return this;
    }
}
