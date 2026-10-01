package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;

import java.util.Objects;

import static com.powsybl.sc.extensions.ShortCircuitConstants.DEFAULT_WITH_CAPACITIES;

public class OpenShortCircuitParameters extends AbstractExtension<ShortCircuitParameters> {
    public static final String NAME = "open-short-circuit-parameters";
    private LoadFlowParameters loadFlowParameters;
    private boolean withCapacities = DEFAULT_WITH_CAPACITIES;

    public OpenShortCircuitParameters() {
        this(new LoadFlowParameters());
    }

    public OpenShortCircuitParameters(LoadFlowParameters loadFlowParameters) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
    }

    public OpenShortCircuitParameters(LoadFlowParameters loadFlowParameters, boolean withCapacities) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        this.withCapacities = withCapacities;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public LoadFlowParameters getLoadFlowParameters() {
        return loadFlowParameters;
    }

    /**
     * Whether the capacities should be taken into account for the computation.
     * If false, the capacities X are considered to be set to 0.
     */
    public boolean isWithCapacities() {
        return withCapacities;
    }

    public OpenShortCircuitParameters setWithCapacities(boolean withCapacities) {
        this.withCapacities = withCapacities;
        return this;
    }

    public OpenShortCircuitParameters setLoadFlowParameters(LoadFlowParameters loadFlowParameters) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        return this;
    }

}
