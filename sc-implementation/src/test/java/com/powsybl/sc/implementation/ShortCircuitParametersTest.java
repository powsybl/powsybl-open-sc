package com.powsybl.sc.implementation;

import com.powsybl.computation.ComputationManager;
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

public class ShortCircuitParametersTest {

    private static final double DELTA_I_A = 1e-2;
    private static final double DELTA_V = 1e-5;

    private LoadFlowParameters loadFlowParameters;

    @BeforeEach
    void setUp() {
        loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
    }

    @Test
    void openSc4nShuntWithLoadsFalse() {
        Network nt4Shunts = create4nShunts(NetworkFactory.findDefault());
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters()
                .setStudyType(StudyType.SUB_TRANSIENT)
                .setWithLoads(false);
        scp.addExtension(OpenShortCircuitParameters.class, new OpenShortCircuitParameters(loadFlowParameters));

        ShortCircuitAnalysisResult scar = provider.run(nt4Shunts, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{2949.84546, 3119.4812, 2954.60254, 2837.94067}
        );
        assertBusVoltages(frs, new double[]{5.87773705, 0.0, 5.55469227, 9.46258354}, 1);
    }

    @Test
    void openSc4nShuntWithCapacitiesFalse() {
        Network nt4Shunts = create4nShunts(NetworkFactory.findDefault());
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters()
                .setStudyType(StudyType.SUB_TRANSIENT);
        scp.addExtension(OpenShortCircuitParameters.class, new OpenShortCircuitParameters(loadFlowParameters)
                .setWithCapacities(false));

        ShortCircuitAnalysisResult scar = provider.run(nt4Shunts, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3547.16528, 3747.61084, 3592.37964, 3411.64258}
        );
        assertBusVoltages(frs, new double[]{6.46059752, 0.0, 5.57590151, 10.4008541}, 1);
    }

    private static List<Fault> createBusFaultsFor4n() {
        return List.of(
                new BusFault("F1", "B1"),
                new BusFault("F2", "B2"),
                new BusFault("F3", "B3"),
                new BusFault("F4", "B4")
        );
    }

    private static void assertMagnitudeCurrents(List<FaultResult> faultResults, double[] expected) {
        for (int i = 0; i < expected.length; i++) {
            MagnitudeFaultResult result = (MagnitudeFaultResult) faultResults.get(i);
            assertEquals(expected[i], result.getCurrent(), DELTA_I_A);
        }
    }

    private static void assertFeederCurrents(List<FaultResult> faultResults, double[] expected, String genId) {
        for (int i = 0; i < expected.length; i++) {
            double result = faultResults.get(i).getFeederCurrent(genId);
            assertEquals(expected[i], result, DELTA_I_A);
        }
    }

    private static void assertBusVoltages(List<FaultResult> faultResults, double[] expected, int busNum) {
        for (int i = 0; i < expected.length; i++) {
            MagnitudeShortCircuitBusResults magnitudeResult = (MagnitudeShortCircuitBusResults) faultResults.get(i).getShortCircuitBusResults().get(busNum);
            double result = magnitudeResult.getVoltage();
            assertEquals(expected[i], result, DELTA_V);
        }
    }
}
