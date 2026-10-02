package com.powsybl.sc.implementation;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.shortcircuit.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.powsybl.sc.util.Networks.create4nShunts;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShortCircuitParametersTest {

    private static final double DELTA_I_A = 1e-2;
    private static final double DELTA_V = 1e-5;
    private static final int BUS_VOLTAGE_RESULT_INDEX = 1;

    private static final List<Fault> BUS_FAULTS_4N = List.of(
            new BusFault("F1", "B1"),
            new BusFault("F2", "B2"),
            new BusFault("F3", "B3"),
            new BusFault("F4", "B4"));

    private LoadFlowParameters loadFlowParameters;
    private ShortCircuitAnalysisProvider provider;

    @BeforeEach
    void setUp() {
        loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
    }

    @Test
    void open4nWithLoadsFalse() {
        ShortCircuitParameters scp = createDefaultShortCircuitParameters().setWithLoads(false);
        List<FaultResult> frs = run(create4nShunts(NetworkFactory.findDefault()), scp);

        assertMagnitudeCurrents(frs, 2949.84546, 3119.4812, 2954.60254, 2837.94067);
        assertBusVoltages(frs, 5.87773705, 0.0, 5.55469227, 9.46258354);
    }

    @Test
    void open4nWithCapacitiesTrue() {
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class).setWithCapacities(true);
        List<FaultResult> frs = run(create4nShuntsNetworkWithLossyLine(), scp);

        assertMagnitudeCurrents(frs, 3618.10645, 3824.44263, 3659.74683, 3481.50415);
        assertBusVoltages(frs, 6.56107378, 0.0, 5.71924686, 10.46735);
    }

    @Test
    void open4nWithCapacitiesFalse() {
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class).setWithCapacities(false);
        List<FaultResult> frs = run(create4nShuntsNetworkWithLossyLine(), scp);

        assertMagnitudeCurrents(frs, 3543.02051, 3747.56567, 3590.66821, 3411.61499);
        assertBusVoltages(frs, 6.56107378, 0.0, 5.63112307, 10.4006405);
    }

    @Test
    void open4nWithResistancesTrue() {
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class).setWithResistances(true);
        List<FaultResult> frs = run(create4nShuntsNetworkWithLossyLine(), scp);

        assertMagnitudeCurrents(frs, 3618.10645, 3824.44263, 3659.74683, 3481.50415);
        assertBusVoltages(frs, 6.56107378, 0.0, 5.71924686, 10.46735);
    }

    @Test
    void open4nWithResistancesFalse() {
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class).setWithResistances(false);
        List<FaultResult> frs = run(create4nShuntsNetworkWithLossyLine(), scp);

        assertMagnitudeCurrents(frs, 3619.93359, 3824.12598, 3665.83569, 3481.27563);
        assertBusVoltages(frs, 6.46059752, 0.0, 5.60450268, 10.4660444);
    }

    /**
     * Add a series resistance and a shunt conductance to line B1_B3
     */
    private static Network create4nShuntsNetworkWithLossyLine() {
        Network network = create4nShunts(NetworkFactory.findDefault());
        network.getLine("B1_B3").setR(1.0).setG1(0.01);
        return network;
    }

    private ShortCircuitParameters createDefaultShortCircuitParameters() {
        OpenShortCircuitParameters openScParametersScParameters = new OpenShortCircuitParameters(loadFlowParameters);
        ShortCircuitParameters scp = new ShortCircuitParameters().setStudyType(StudyType.SUB_TRANSIENT);
        scp.addExtension(OpenShortCircuitParameters.class, openScParametersScParameters);
        return scp;
    }

    private List<FaultResult> run(Network network, ShortCircuitParameters scp) {
        return provider.run(network, BUS_FAULTS_4N, scp, LocalComputationManager.getDefault(), Collections.emptyList())
                .join()
                .getFaultResults();
    }

    private static void assertMagnitudeCurrents(List<FaultResult> faultResults, double... expected) {
        for (int i = 0; i < expected.length; i++) {
            MagnitudeFaultResult result = (MagnitudeFaultResult) faultResults.get(i);
            assertEquals(expected[i], result.getCurrent(), DELTA_I_A, "Current for fault " + result.getFault().getId());
        }
    }

    private static void assertBusVoltages(List<FaultResult> faultResults, double... expected) {
        for (int i = 0; i < expected.length; i++) {
            FaultResult faultResult = faultResults.get(i);
            MagnitudeShortCircuitBusResults busResult =
                    (MagnitudeShortCircuitBusResults) faultResult.getShortCircuitBusResults().get(BUS_VOLTAGE_RESULT_INDEX);
            assertEquals(expected[i], busResult.getVoltage(), DELTA_V, "Bus voltage for fault " + faultResult.getFault().getId());
        }
    }
}
