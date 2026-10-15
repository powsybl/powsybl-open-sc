/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.sc.extensions.GeneratorSelectionMode;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import com.powsybl.shortcircuit.VoltageRange;

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
public final class ShortCircuitEngineParameters {

    public enum AnalysisType {
        SELECTIVE, // short circuit analysis for List<ShortCircuitFault> faults in input
        SYSTEMATIC // short circuit analysis for all busses of input grid
    }

    private final LoadFlowParameters loadFlowParameters;
    private final MatrixFactory matrixFactory;
    private final AnalysisType analysisType;
    private final ShortCircuitNorm norm;
    private List<ShortCircuitFault> shortCircuitFaults;

    private boolean voltageUpdate = DEFAULT_WITH_VOLTAGE_RESULT;
    private double minVoltageDropPercent = DEFAULT_MIN_VOLTAGE_DROP_PROPORTIONAL_THRESHOLD;
    private InitialVoltageProfileMode vProfile = DEFAULT_INITIAL_VOLTAGE_PROFILE_MODE;
    private List<VoltageRange> vConfiguredRanges = Collections.emptyList();
    private StudyType studyType = DEFAULT_STUDY_TYPE;
    private boolean ignoreShunts = !DEFAULT_WITH_SHUNT_COMPENSATORS;
    private boolean ignoreLoads = !DEFAULT_WITH_LOADS;
    private boolean ignoreCapacities = !DEFAULT_WITH_CAPACITIES;
    private boolean ignoreResistances = !DEFAULT_WITH_RESISTANCES;
    private boolean isWithNeutralPosition = DEFAULT_WITH_NEUTRAL_POSITION;
    private Set<String> innerZoneBusIds = Collections.emptySet();
    private GeneratorSelectionMode innerZoneGeneratorSelectionMode = DEFAULT_INNER_ZONE_GENERATOR_SELECTION_MODE;
    private GeneratorSelectionMode outerZoneGeneratorSelectionMode = DEFAULT_OUTER_ZONE_GENERATOR_SELECTION_MODE;
    private double innerZoneRunningGeneratorThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;
    private double outerZoneRunningGeneratorThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;

    public ShortCircuitEngineParameters(LoadFlowParameters loadFlowParameters, MatrixFactory matrixFactory,
                                        AnalysisType analysisType, List<ShortCircuitFault> faults, ShortCircuitNorm norm) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.analysisType = Objects.requireNonNull(analysisType);
        this.shortCircuitFaults = Objects.requireNonNull(faults);
        this.norm = Objects.requireNonNull(norm);
    }

    public ShortCircuitEngineParameters(MatrixFactory matrixFactory, AnalysisType analysisType, List<ShortCircuitFault> faults,
                                        ShortCircuitParameters scParameters, ShortCircuitNorm norm) {
        this(getOrDefaultOpenScParameters(scParameters).getLoadFlowParameters(), matrixFactory, analysisType, faults, norm);
        OpenShortCircuitParameters openScParameters = getOrDefaultOpenScParameters(scParameters);
        setVoltageUpdate(scParameters.isWithVoltageResult())
                .setMinVoltageDropPercent(scParameters.getMinVoltageDropProportionalThreshold())
                .setVoltageProfileType(scParameters.getInitialVoltageProfileMode())
                .setConfiguredVoltageRanges(scParameters.getVoltageRanges())
                .setStudyType(scParameters.getStudyType())
                .setIgnoreShunts(!scParameters.isWithShuntCompensators())
                .setIgnoreLoads(!scParameters.isWithLoads())
                .setIgnoreCapacities(!openScParameters.isWithCapacities())
                .setIgnoreResistances(!openScParameters.isWithResistances())
                .setWithNeutralPosition(scParameters.isWithNeutralPosition())
                .setInnerZoneBusIds(openScParameters.getInnerZoneBusIds())
                .setInnerZoneGeneratorSelectionMode(openScParameters.getInnerZoneGeneratorSelectionMode())
                .setOuterZoneGeneratorSelectionMode(openScParameters.getOuterZoneGeneratorSelectionMode())
                .setInnerZoneRunningGeneratorThreshold(openScParameters.getInnerZoneRunningGeneratorThreshold())
                .setOuterZoneRunningGeneratorThreshold(openScParameters.getOuterZoneRunningGeneratorThreshold());
    }

    private static OpenShortCircuitParameters getOrDefaultOpenScParameters(ShortCircuitParameters scParameters) {
        OpenShortCircuitParameters openScParameters = Objects.requireNonNull(scParameters)
                .getExtension(OpenShortCircuitParameters.class);
        return openScParameters != null ? openScParameters : new OpenShortCircuitParameters();
    }

    public LoadFlowParameters getLoadFlowParameters() {
        return loadFlowParameters;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public AnalysisType getAnalysisType() {
        return analysisType;
    }

    public ShortCircuitNorm getNorm() {
        return norm;
    }

    public List<ShortCircuitFault> getShortCircuitFaults() {
        return shortCircuitFaults;
    }

    public ShortCircuitEngineParameters setShortCircuitFaults(List<ShortCircuitFault> faults) {
        this.shortCircuitFaults = Objects.requireNonNull(faults);
        return this;
    }

    public boolean isVoltageUpdate() {
        return voltageUpdate;
    }

    public ShortCircuitEngineParameters setVoltageUpdate(boolean voltageUpdate) {
        this.voltageUpdate = voltageUpdate;
        return this;
    }

    public double getMinVoltageDropPercent() {
        return minVoltageDropPercent;
    }

    public ShortCircuitEngineParameters setMinVoltageDropPercent(double minVoltageDropPercent) {
        this.minVoltageDropPercent = minVoltageDropPercent;
        return this;
    }

    public InitialVoltageProfileMode getVoltageProfileType() {
        return vProfile;
    }

    public ShortCircuitEngineParameters setVoltageProfileType(InitialVoltageProfileMode vProfile) {
        this.vProfile = Objects.requireNonNull(vProfile);
        return this;
    }

    public List<VoltageRange> getConfiguredVoltageRanges() {
        return vConfiguredRanges;
    }

    public ShortCircuitEngineParameters setConfiguredVoltageRanges(List<VoltageRange> voltageRanges) {
        this.vConfiguredRanges = voltageRanges != null ? List.copyOf(voltageRanges) : Collections.emptyList();
        return this;
    }

    public StudyType getStudyType() {
        return studyType;
    }

    public ShortCircuitEngineParameters setStudyType(StudyType studyType) {
        this.studyType = Objects.requireNonNull(studyType);
        return this;
    }

    public boolean isIgnoreShunts() {
        return ignoreShunts;
    }

    public ShortCircuitEngineParameters setIgnoreShunts(boolean ignoreShunts) {
        this.ignoreShunts = ignoreShunts;
        return this;
    }

    public boolean isIgnoreLoads() {
        return ignoreLoads;
    }

    public ShortCircuitEngineParameters setIgnoreLoads(boolean ignoreLoads) {
        this.ignoreLoads = ignoreLoads;
        return this;
    }

    public boolean isIgnoreCapacities() {
        return ignoreCapacities;
    }

    public ShortCircuitEngineParameters setIgnoreCapacities(boolean ignoreCapacities) {
        this.ignoreCapacities = ignoreCapacities;
        return this;
    }

    public boolean isIgnoreResistances() {
        return ignoreResistances;
    }

    public ShortCircuitEngineParameters setIgnoreResistances(boolean ignoreResistances) {
        this.ignoreResistances = ignoreResistances;
        return this;
    }

    public boolean isWithNeutralPosition() {
        return isWithNeutralPosition;
    }

    public ShortCircuitEngineParameters setWithNeutralPosition(boolean withNeutralPosition) {
        this.isWithNeutralPosition = withNeutralPosition;
        return this;
    }

    public Set<String> getInnerZoneBusIds() {
        return innerZoneBusIds;
    }

    public ShortCircuitEngineParameters setInnerZoneBusIds(Collection<String> innerZoneBusIds) {
        this.innerZoneBusIds = Set.copyOf(Objects.requireNonNull(innerZoneBusIds));
        return this;
    }

    public GeneratorSelectionMode getInnerZoneGeneratorSelectionMode() {
        return innerZoneGeneratorSelectionMode;
    }

    public ShortCircuitEngineParameters setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode mode) {
        this.innerZoneGeneratorSelectionMode = Objects.requireNonNull(mode);
        return this;
    }

    public GeneratorSelectionMode getOuterZoneGeneratorSelectionMode() {
        return outerZoneGeneratorSelectionMode;
    }

    public ShortCircuitEngineParameters setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode mode) {
        this.outerZoneGeneratorSelectionMode = Objects.requireNonNull(mode);
        return this;
    }

    public double getInnerZoneRunningGeneratorThreshold() {
        return innerZoneRunningGeneratorThreshold;
    }

    public ShortCircuitEngineParameters setInnerZoneRunningGeneratorThreshold(double threshold) {
        this.innerZoneRunningGeneratorThreshold = threshold;
        return this;
    }

    public double getOuterZoneRunningGeneratorThreshold() {
        return outerZoneRunningGeneratorThreshold;
    }

    public ShortCircuitEngineParameters setOuterZoneRunningGeneratorThreshold(double threshold) {
        this.outerZoneRunningGeneratorThreshold = threshold;
        return this;
    }
}
