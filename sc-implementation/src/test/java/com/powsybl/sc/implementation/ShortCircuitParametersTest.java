package com.powsybl.sc.implementation;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.sc.extensions.GeneratorSelectionMode;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.shortcircuit.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.powsybl.sc.util.Networks.create4nShunts;
import static org.junit.jupiter.api.Assertions.*;

class ShortCircuitParametersTest {

    private static final double DELTA_I_A = 1e-2;
    private static final double DELTA_V = 1e-5;
    private static final String BUS_VOLTAGE_RESULT_ID = "VL_2_0";

    private static final List<Fault> BUS_FAULTS_4N = List.of(
            new BusFault("F1", "B1"),
            new BusFault("F2", "B2"),
            new BusFault("F3", "B3"),
            new BusFault("F4", "B4"));

    private LoadFlowParameters loadFlowParameters;
    private ShortCircuitAnalysisProvider provider;
    private ShortCircuitParameters scp;

    @BeforeEach
    void setUp() {
        loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        provider = new OpenShortCircuitProvider();
        scp = new ShortCircuitParameters().setStudyType(StudyType.SUB_TRANSIENT);
        OpenShortCircuitParameters openScParameters = new OpenShortCircuitParameters().setLoadFlowParameters(loadFlowParameters);
        scp.addExtension(OpenShortCircuitParameters.class, openScParameters);
    }

    @Test
    void open4nWithLoadsFalse() {
        scp.setWithLoads(false);
        List<FaultResult> frs = run(create4nShunts(NetworkFactory.findDefault()), scp);

        assertMagnitudeCurrents(frs, 2949.84546, 3119.4812, 2954.60254, 2837.94067);
        assertBusVoltages(frs, 5.87773705, 0.0, 5.55469227, 9.46258354);
    }

    @Test
    void open4nWithCapacitiesTrue() {
        scp.getExtension(OpenShortCircuitParameters.class).setWithCapacities(true);
        List<FaultResult> frs = run(create4nShuntsNetworkWithLossyLine(), scp);

        assertMagnitudeCurrents(frs, 3618.10645, 3824.44263, 3659.74683, 3481.50415);
        assertBusVoltages(frs, 6.56107378, 0.0, 5.71924686, 10.46735);
    }

    @Test
    void open4nWithCapacitiesFalse() {
        scp.getExtension(OpenShortCircuitParameters.class).setWithCapacities(false);
        List<FaultResult> frs = run(create4nShuntsNetworkWithLossyLine(), scp);

        assertMagnitudeCurrents(frs, 3543.02051, 3747.56567, 3590.66821, 3411.61499);
        assertBusVoltages(frs, 6.56107378, 0.0, 5.63112307, 10.4006405);
    }

    @Test
    void open4nWithResistancesTrue() {
        scp.getExtension(OpenShortCircuitParameters.class).setWithResistances(true);
        List<FaultResult> frs = run(create4nShuntsNetworkWithLossyLine(), scp);

        assertMagnitudeCurrents(frs, 3618.10645, 3824.44263, 3659.74683, 3481.50415);
        assertBusVoltages(frs, 6.56107378, 0.0, 5.71924686, 10.46735);
    }

    @Test
    void open4nWithResistancesFalse() {
        scp.getExtension(OpenShortCircuitParameters.class).setWithResistances(false);
        List<FaultResult> frs = run(create4nShuntsNetworkWithLossyLine(), scp);

        assertMagnitudeCurrents(frs, 3619.93359, 3824.12598, 3665.83569, 3481.27563);
        assertBusVoltages(frs, 6.46059752, 0.0, 5.60450268, 10.4660444);
    }

    @Test
    void open4nWithInconsistentShortCircuitParameters() {
        Network n4nLossyLine = create4nShuntsNetworkWithLossyLine();
        LoadFlow.run(n4nLossyLine, loadFlowParameters);
        scp.setInitialVoltageProfileMode(InitialVoltageProfileMode.PREVIOUS_VALUE)
                .setWithLoads(false)
                .setWithShuntCompensators(false)
                .setWithNeutralPosition(true);
        scp.getExtension(OpenShortCircuitParameters.class)
                .setWithResistances(false)
                .setWithCapacities(false);
        List<FaultResult> frs = run(n4nLossyLine, scp);

        assertMagnitudeCurrents(frs, 2684.06998, 2886.75134, 2690.00646, 2579.44128);
    }

    /**
     * Default empty innerZoneBusIds is interpreted as the whole Network
     * */
    @Test
    void open4nWithEmptyInnerZone() {
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        List<FaultResult> frs = run(create4nShuntsNetworkWithMoreGenerators(), scp);

        assertEquals(4, frs.size());
        assertEquals(4, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 11372.7627, 11049.2148, 11570.4268, 10883.417);
        assertBusVoltages(frs, 10.3602247, 0.0, 8.68918419, 18.8513908);
    }

    /**
     * Return only two FaultResults
     * */
    @Test
    void simulateOnlyBusesInInnerZone() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"));
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 11049.2148, 11570.4268);
        assertBusVoltages(frs, 0.0, 8.68918419);
    }

    /**
     * Keep all generators in both zones, whatever the thresholds
     * */
    @Test
    void allModeInBothZonesKeepsAllGenerators() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode.ALL)
                .setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode.ALL)
                .setInnerZoneGeneratorPowerThreshold(1000)
                .setOuterZoneGeneratorPowerThreshold(1000);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertMagnitudeCurrents(frs, 11049.2148, 11570.4268);
        assertBusVoltages(frs, 0.0, 8.68918419);
    }

    /**
     * Drop all generators in both zones
     * */
    @Test
    void abovePowerThresholdInBothZonesDropsAllGenerators() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setInnerZoneGeneratorPowerThreshold(1000)
                .setOuterZoneGeneratorPowerThreshold(1000);
        PowsyblException e = assertThrows(PowsyblException.class, () -> run(n4nWithGenerators, scp));
        assertTrue(e.getMessage().startsWith("No generator contributes to the short-circuit current"));
    }

    /**
     * Keep "G2" and "G3" in inner zone
     * */
    @Test
    void keepAllGeneratorsInInnerZoneWhenThresholdBelowTheirPower() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setInnerZoneGeneratorPowerThreshold(5.0);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 11049.2148, 11570.4268);
        assertBusVoltages(frs, 0.0, 8.68918419);
    }

    /**
     * Drop "G2" and "G3" in inner zone
     * */
    @Test
    void dropAllGeneratorsInInnerZoneWhenThresholdAboveTheirPower() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setInnerZoneGeneratorPowerThreshold(20.0);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 5850.84082, 6142.35938);
        assertBusVoltages(frs, 0.0, 3.55381966);
    }

    /**
     * Drop "G3" in inner zone
     * */
    @Test
    void dropLowPowerGeneratorsInInnerZone() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setInnerZoneGeneratorPowerThreshold(10.0);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 8737.08594, 8684.00879);
        assertBusVoltages(frs, 0.0, 8.68918419);
    }

    /**
     * Bus "B5" is unknown so innerZOne: "B1" and "B3"
     * */
    @Test
    void unknowBusInInnerZoneBusIdsIgnored() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3", "B5"))
                .setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setInnerZoneGeneratorPowerThreshold(10.0);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 8737.08594, 8684.00879);
        assertBusVoltages(frs, 0.0, 8.68918419);
    }

    @Test
    void throwErrorForNoneValidInnerBusIds() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B5", "B6"));

        PowsyblException error = assertThrows(PowsyblException.class, () -> run(n4nWithGenerators, scp));

        assertTrue(error.getMessage().contains("B5"));
        assertTrue(error.getMessage().contains("B6"));
    }

    /**
     * Keep "G1" and "G4" in outer zone
     * */
    @Test
    void keepAllGeneratorsInOuterZoneWhenThresholdBelowTheirPower() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setOuterZoneGeneratorPowerThreshold(1);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 11049.2148, 11570.4268);
        assertBusVoltages(frs, 0.0, 8.68918419);
    }

    /**
     * Drop "G1" and "G4" in outer zone
     * */
    @Test
    void dropAllGeneratorsInOuterZoneWhenThresholdAboveTheirPower() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setOuterZoneGeneratorPowerThreshold(20);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 6397.60498, 6477.34131);
        assertBusVoltages(frs, 0.0, 5.57590151);
    }

    /**
     * Drop "G1" in outer zone
     * */
    @Test
    void dropLowPowerGeneratorsInOuterZone() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setOuterZoneGeneratorPowerThreshold(5);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 8730.9502, 9104.2627);
        assertBusVoltages(frs, 0.0, 6.55653477);
    }

    /**
     * Drop "G3" in inner zone and "G1" in outer zone
     * */
    @Test
    void dropLowPowerGeneratorsInTwoZones() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setOuterZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setInnerZoneGeneratorPowerThreshold(10)
                .setOuterZoneGeneratorPowerThreshold(5);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 6287.43311, 6218.13379);
        assertBusVoltages(frs, 0.0, 6.55653477);
    }

    @Test
    void generatorAtThresholdIsNotRunning() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneBusIds(List.of("B2", "B3"))
                .setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setInnerZoneGeneratorPowerThreshold(Math.abs(n4nWithGenerators.getGenerator("G3").getTerminal().getP()));
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(2, frs.size());
        assertEquals(2, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 8737.08594, 8684.00879);
        assertBusVoltages(frs, 0.0, 8.68918419);
    }

    /**
     * Only G2 is kept
     * */
    @Test
    void emptyInnerZoneAppliesInnerRulesToWholeNetwork() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setInnerZoneGeneratorSelectionMode(GeneratorSelectionMode.ABOVE_POWER_THRESHOLD)
                .setInnerZoneGeneratorPowerThreshold(10);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(4, frs.size());
        assertEquals(4, frs.getFirst().getShortCircuitBusResults().size());
        assertMagnitudeCurrents(frs, 3547.16528, 3747.61084, 3592.37964, 3411.64258);
        assertBusVoltages(frs, 6.46059752, 0.0, 5.57590151, 10.4008541);
    }

    @Test
    void emptyInnerZoneIgnoresOuterRules() {
        Network n4nWithGenerators = create4nShuntsNetworkWithMoreGenerators();
        LoadFlow.run(n4nWithGenerators, loadFlowParameters);
        ShortCircuitParameters scp = createDefaultShortCircuitParameters();
        scp.getExtension(OpenShortCircuitParameters.class)
                .setOuterZoneGeneratorPowerThreshold(10);
        List<FaultResult> frs = run(n4nWithGenerators, scp);

        assertEquals(4, frs.size());
        assertMagnitudeCurrents(frs, 11372.7627, 11049.2148, 11570.4268, 10883.417);
        assertBusVoltages(frs, 10.3602247, 0.0, 8.68918419, 18.8513908);
    }

    /**
     * Add a series resistance and a shunt conductance to line B1_B3
     */
    private static Network create4nShuntsNetworkWithLossyLine() {
        Network network = create4nShunts(NetworkFactory.findDefault());
        network.getLine("B1_B3").setR(1.0).setG1(0.01);
        return network;
    }

    private static Network create4nShuntsNetworkWithMoreGenerators() {
        Network network = create4nShunts(NetworkFactory.findDefault());
        addGenerator(network.getVoltageLevel("VL_1"), "G1", "B1", 20.0);
        addGenerator(network.getVoltageLevel("VL_3"), "G3", "B3", 25.0);
        addGenerator(network.getVoltageLevel("VL_4"), "G4", "B4", 22.0);
        return network;
    }

    private static void addGenerator(VoltageLevel voltageLevel, String id, String busId, double targetP) {
        double targetV = voltageLevel.getNominalV();
        Generator generator = voltageLevel.newGenerator()
                .setId(id)
                .setBus(busId)
                .setConnectableBus(busId)
                .setMinP(0.0)
                .setMaxP(150.0)
                .setTargetP(targetP)
                .setTargetV(targetV)
                .setVoltageRegulatorOn(true)
                .add();
        generator.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(20.)
                .withDirectTransX(20.)
                .withStepUpTransformerX(0.)
                .add();
    }

    private ShortCircuitParameters createDefaultShortCircuitParameters() {
        OpenShortCircuitParameters openScParametersScParameters = new OpenShortCircuitParameters().setLoadFlowParameters(loadFlowParameters);
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
            MagnitudeShortCircuitBusResults busResult = (MagnitudeShortCircuitBusResults) faultResult.getShortCircuitBusResults().stream()
                    .filter(result -> BUS_VOLTAGE_RESULT_ID.equals(result.getBusId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No voltage result for bus " + BUS_VOLTAGE_RESULT_ID
                            + " in fault " + faultResult.getFault().getId()));
            assertEquals(expected[i], busResult.getVoltage(), DELTA_V, "Bus voltage for fault " + faultResult.getFault().getId());
        }
    }
}
