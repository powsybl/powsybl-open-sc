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
    private double innerZoneGeneratorPowerThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;
    private double outerZoneGeneratorPowerThreshold = DEFAULT_RUNNING_GENERATOR_THRESHOLD;

    @Override
    public String getName() {
        return NAME;
    }

    public LoadFlowParameters getLoadFlowParameters() {
        if (loadFlowParameters == null) {
            loadFlowParameters = new LoadFlowParameters();
        }
        return loadFlowParameters;
    }

    // Whether the shunt admittances at both ends of each branch should be taken into account
    public boolean isWithCapacities() {
        return withCapacities;
    }

     // Whether the series resistance of each branch should be taken into account
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

    public double getInnerZoneGeneratorPowerThreshold() {
        return innerZoneGeneratorPowerThreshold;
    }

    public OpenShortCircuitParameters setInnerZoneGeneratorPowerThreshold(double threshold) {
        this.innerZoneGeneratorPowerThreshold = checkThreshold(threshold);
        return this;
    }

    public double getOuterZoneGeneratorPowerThreshold() {
        return outerZoneGeneratorPowerThreshold;
    }

    public OpenShortCircuitParameters setOuterZoneGeneratorPowerThreshold(double threshold) {
        this.outerZoneGeneratorPowerThreshold = checkThreshold(threshold);
        return this;
    }

    private static double checkThreshold(double threshold) {
        if (Double.isNaN(threshold) || threshold < 0) {
            throw new IllegalArgumentException("Running generator threshold must be >= 0: " + threshold);
        }
        return threshold;
    }
}
