package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import static com.powsybl.sc.extensions.ShortCircuitConstants.*;

public class OpenShortCircuitParameters extends AbstractExtension<ShortCircuitParameters> {
    public static final String NAME = "open-short-circuit-parameters";
    private LoadFlowParameters loadFlowParameters;
    private boolean withCapacities = DEFAULT_WITH_CAPACITIES;
    private boolean withResistances = DEFAULT_WITH_RESISTANCES;

    private Set<String> innerZoneBusIds = Collections.emptySet();
    private GeneratorSelectionMode innerZoneGeneratorSelectionMode = DEFAULT_INNER_ZONE_GENERATOR_SELECTION_MODE;
    private GeneratorSelectionMode outerZoneGeneratorSelectionMode = DEFAULT_OUTER_ZONE_GENERATOR_SELECTION_MODE;
    private double innerZoneRunningGeneratorThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;
    private double outerZoneRunningGeneratorThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;

    public OpenShortCircuitParameters() {
        this(new LoadFlowParameters());
    }

    public OpenShortCircuitParameters(LoadFlowParameters loadFlowParameters) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
    }

    public OpenShortCircuitParameters(LoadFlowParameters loadFlowParameters, boolean withCapacities, boolean withResistances) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        this.withCapacities = withCapacities;
        this.withResistances = withResistances;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public LoadFlowParameters getLoadFlowParameters() {
        return loadFlowParameters;
    }

    /**
     * Whether the shunt admittances at both ends of each branch should be taken into account
     * when building the admittance matrix.
     * If false, the shunt conductance and susceptances at both ends of each branch
     * (G1, B1, G2, B2) are considered to be set to 0.
     */
    public boolean isWithCapacities() {
        return withCapacities;
    }

    /**
     * Whether the series resistance of each branch should be taken into account
     * when building the admittance matrix.
     * If false, the series resistance R of each branch is considered to be set to 0,
     * so that only the series reactance X is used.
     */
    public boolean isWithResistances() {
        return withResistances;
    }

    public OpenShortCircuitParameters setWithCapacities(boolean withCapacities) {
        this.withCapacities = withCapacities;
        return this;
    }

    public OpenShortCircuitParameters setWithResistances(boolean withResistances) {
        this.withResistances = withResistances;
        return this;
    }

    public OpenShortCircuitParameters setLoadFlowParameters(LoadFlowParameters loadFlowParameters) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        return this;
    }

    public Set<String> getInnerZoneBusIds() {
        return innerZoneBusIds;
    }

    public OpenShortCircuitParameters setInnerZoneBusIds(Collection<String> innerZoneBusIds) {
        this.innerZoneBusIds = Set.copyOf(Objects.requireNonNull(innerZoneBusIds));
        return this;
    }

    public GeneratorSelectionMode getInnerZoneGeneratorSelectionMode() {
        return innerZoneGeneratorSelectionMode;
    }

    public OpenShortCircuitParameters setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode mode) {
        this.innerZoneGeneratorSelectionMode = Objects.requireNonNull(mode);
        return this;
    }

    public GeneratorSelectionMode getOuterZoneGeneratorSelectionMode() {
        return outerZoneGeneratorSelectionMode;
    }

    public OpenShortCircuitParameters setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode mode) {
        this.outerZoneGeneratorSelectionMode = Objects.requireNonNull(mode);
        return this;
    }

    public double getInnerZoneRunningGeneratorThreshold() {
        return innerZoneRunningGeneratorThreshold;
    }

    public OpenShortCircuitParameters setInnerZoneRunningGeneratorThreshold(double threshold) {
        this.innerZoneRunningGeneratorThreshold = checkThreshold(threshold);
        return this;
    }

    public double getOuterZoneRunningGeneratorThreshold() {
        return outerZoneRunningGeneratorThreshold;
    }

    public OpenShortCircuitParameters setOuterZoneRunningGeneratorThreshold(double threshold) {
        this.outerZoneRunningGeneratorThreshold = checkThreshold(threshold);
        return this;
    }

    private static double checkThreshold(double threshold) {
        if (Double.isNaN(threshold) || threshold < 0) {
            throw new IllegalArgumentException("Running generator threshold must be >= 0: " + threshold);
        }
        return threshold;
    }
}
