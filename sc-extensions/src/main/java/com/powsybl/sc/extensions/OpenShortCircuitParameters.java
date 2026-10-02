package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;

import java.util.Objects;

import static com.powsybl.sc.extensions.ShortCircuitConstants.DEFAULT_WITH_CAPACITIES;
import static com.powsybl.sc.extensions.ShortCircuitConstants.DEFAULT_WITH_RESISTANCES;

public class OpenShortCircuitParameters extends AbstractExtension<ShortCircuitParameters> {
    public static final String NAME = "open-short-circuit-parameters";
    private LoadFlowParameters loadFlowParameters;
    private boolean withCapacities = DEFAULT_WITH_CAPACITIES;
    private boolean withResistances = DEFAULT_WITH_RESISTANCES;

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

    public void setWithCapacities(boolean withCapacities) {
        this.withCapacities = withCapacities;
    }

    public void setWithResistances(boolean withResistances) {
        this.withResistances = withResistances;
    }

    public void setLoadFlowParameters(LoadFlowParameters loadFlowParameters) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
    }
}
