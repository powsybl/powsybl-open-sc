package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;

import java.util.Objects;

public class OpenShortCircuitParameters extends AbstractExtension<ShortCircuitParameters> {
    public static final String NAME = "open-short-circuit-parameters";
    private LoadFlowParameters loadFlowParameters;

    public OpenShortCircuitParameters() {
        this(new LoadFlowParameters());
    }

    public OpenShortCircuitParameters(LoadFlowParameters loadFlowParameters) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
    }

    @Override
    public String getName() {
        return NAME;
    }

    public LoadFlowParameters getLoadFlowParameters() {
        return loadFlowParameters;
    }

    public OpenShortCircuitParameters setLoadFlowParameters(LoadFlowParameters loadFlowParameters) {
        this.loadFlowParameters = Objects.requireNonNull(loadFlowParameters);
        return this;
    }
}
