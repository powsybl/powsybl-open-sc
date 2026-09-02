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
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import com.powsybl.shortcircuit.VoltageRange;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitEngineParameters {
    public enum VoltageProfileType {
        CALCULATED, // use the computed values at nodes to compute Zth and Eth
        CONFIGURED, // use configured values per voltage ranges to compute Zth and Eth
        NOMINAL // use the nominal voltage values at nodes to compute Zth and Eth
    }

    public enum PeriodType {
        SUB_TRANSIENT, //uses subTransient parameters x"d
        TRANSIENT,     //uses transient parameters x'd
        STEADY_STATE
    }

    public enum AnalysisType {
        SELECTIVE, // short circuit analysis for List<ShortCircuitFault> faults in input
        SYSTEMATIC // short circuit analysis for all busses of input grid
    }

    private final LoadFlowParameters loadFlowParameters;

    private List<ShortCircuitFault> shortCircuitFaults;

    private final MatrixFactory matrixFactory;

    private final VoltageProfileType vProfile;

    private final List<VoltageRange> vConfiguredRanges;

    private final boolean ignoreShunts;

    private final AnalysisType analysisType;

    private boolean voltageUpdate;

    private final double minVoltageDropPercent;

    private final PeriodType periodType;

    private final ShortCircuitNorm norm;

    private final boolean isWithNeutralPosition;

    public ShortCircuitEngineParameters(LoadFlowParameters loadFlowParameters, MatrixFactory matrixFactory, AnalysisType analysisType, List<ShortCircuitFault> faults, boolean isVoltageExport, VoltageProfileType vProfile, boolean ignoreShunts, PeriodType periodType, ShortCircuitNorm norm) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.shortCircuitFaults = Objects.requireNonNull(faults);
        this.voltageUpdate = isVoltageExport;
        this.minVoltageDropPercent = 0.0;
        this.ignoreShunts = ignoreShunts;
        this.vProfile = vProfile;
        this.vConfiguredRanges = Collections.emptyList();
        this.analysisType = analysisType;
        this.periodType = periodType;
        this.norm = norm;
        this.isWithNeutralPosition = false;
    }

    public ShortCircuitEngineParameters(LoadFlowParameters loadFlowParameters, MatrixFactory matrixFactory, AnalysisType analysisType, List<ShortCircuitFault> faults, ShortCircuitParameters scParameters, ShortCircuitNorm norm) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.shortCircuitFaults = Objects.requireNonNull(faults);
        this.voltageUpdate = scParameters.isWithVoltageResult();
        this.minVoltageDropPercent = scParameters.getMinVoltageDropProportionalThreshold();
        this.ignoreShunts = !scParameters.isWithShuntCompensators();
        this.vProfile = toVoltageProfileType(scParameters.getInitialVoltageProfileMode());
        this.vConfiguredRanges = scParameters.getVoltageRanges();
        this.analysisType = analysisType;
        this.periodType = toPeriodType(scParameters.getStudyType());
        this.norm = norm;
        this.isWithNeutralPosition = scParameters.isWithNeutralPosition();
    }

    public LoadFlowParameters getLoadFlowParameters() {
        return loadFlowParameters;
    }

    public List<ShortCircuitFault> getShortCircuitFaults() {
        return shortCircuitFaults;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public VoltageProfileType getVoltageProfileType() {
        return vProfile;
    }

    public List<VoltageRange> getConfiguredVoltageRanges() {
        return vConfiguredRanges;
    }

    public boolean isIgnoreShunts() {
        return ignoreShunts;
    }

    public AnalysisType getAnalysisType() {
        return analysisType;
    }

    public void setShortCircuitFaults(List<ShortCircuitFault> faults) {
        shortCircuitFaults = faults;
    }

    public PeriodType getPeriodType() {
        return periodType;
    }

    public ShortCircuitNorm getNorm() {
        return norm;
    }

    public boolean isVoltageUpdate() {
        return voltageUpdate;
    }

    public double getMinVoltageDropPercent() {
        return minVoltageDropPercent;
    }

    public void setVoltageUpdate(boolean bool) {
        voltageUpdate = bool;
    }

    private static VoltageProfileType toVoltageProfileType(InitialVoltageProfileMode vMode) {
        return switch (vMode) {
            case CONFIGURED -> VoltageProfileType.CONFIGURED;
            case NOMINAL -> VoltageProfileType.NOMINAL;
            case PREVIOUS_VALUE -> VoltageProfileType.CALCULATED;
        };
    }

    private static PeriodType toPeriodType(StudyType studyType) {
        return switch (studyType) {
            case STEADY_STATE -> PeriodType.STEADY_STATE;
            case SUB_TRANSIENT -> PeriodType.SUB_TRANSIENT;
            case TRANSIENT -> PeriodType.TRANSIENT;
        };
    }

    public boolean isWithNeutralPosition() {
        return isWithNeutralPosition;
    }
}
