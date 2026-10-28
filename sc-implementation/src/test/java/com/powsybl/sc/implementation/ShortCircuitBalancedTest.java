/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.computation.ComputationManager;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerFortescue;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.sc.util.ReferenceNetwork;
import com.powsybl.sc.util.extensions.ThreeWindingsTransformerNorm;
import com.powsybl.shortcircuit.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.powsybl.sc.util.Networks.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitBalancedTest {

    private MatrixFactory matrixFactory;

    private static final double DELTA_I_A = 1e-2;
    private static final double DELTA_I_KA = DELTA_I_A / 1e3; // For tests not using Core API -> to be replaced
    private static final double DELTA_V = 1e-5;
    private static final double DELTA_Z = 1e-5;
    private static final double DELTA_K = 1e-5;

    private LoadFlowParameters loadFlowParameters;

    private ShortCircuitAnalysisProvider provider;

    private ComputationManager cm;

    private ShortCircuitParameters scp;

    @BeforeEach
    void setUp() {
        matrixFactory = new DenseMatrixFactory();
        loadFlowParameters = LoadFlowParameters.load()
                .setTwtSplitShuntAdmittance(true);

        provider = new OpenShortCircuitProvider(matrixFactory);

        cm = LocalComputationManager.getDefault();

        scp = new ShortCircuitParameters()
                .setStudyType(StudyType.SUB_TRANSIENT);
        scp.addExtension(OpenShortCircuitParameters.class, new OpenShortCircuitParameters(loadFlowParameters));
    }

    @Disabled
    @Test
    void computeIccTest() {
        Network nt2 = create2n(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        List<ShortCircuitFault> tmpV = new ArrayList<>();
        ShortCircuitFault sc2 = new ShortCircuitFault("B2", "sc2", "B2", new ShortCircuitFaultImpedance(Complex.ZERO), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        tmpV.add(sc2);

        scp.setInitialVoltageProfileMode(InitialVoltageProfileMode.PREVIOUS_VALUE);

        ShortCircuitEngineParameters scbParameters =
                new ShortCircuitEngineParameters(matrixFactory,
                        ShortCircuitEngineParameters.AnalysisType.SELECTIVE,
                        tmpV,
                        scp,
                        new ShortCircuitNormNone());
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(nt2, scbParameters);

        scbEngine.run();

        scbEngine.resultsPerFault.get(sc2).updateFeedersResult(false);

        assertEquals(-0.4316661015058293, scbEngine.resultsPerFault.get(sc2).getId().getReal(), DELTA_I_KA / 10); // kA and not A
        assertEquals(-4.617486568622836, scbEngine.resultsPerFault.get(sc2).getId().getImaginary(), DELTA_I_KA / 10);

    }

    @Test
    void openShortCircuitProvider2n() {
        Network nt2 = create2n(NetworkFactory.findDefault());

        List<Fault> faults = List.of(
                new BusFault("F1", "B1"),
                new BusFault("F2", "B2"));

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        String providerName = provider.getName();
        String providerVersion = provider.getVersion();

        assertEquals(2, scar.getFaultResults().size());

        MagnitudeFaultResult m0 = (MagnitudeFaultResult) scar.getFaultResult("F1");
        MagnitudeFaultResult m1 = (MagnitudeFaultResult) scar.getFaultResult("F2");

        assertEquals(2945.047378902121, m0.getCurrent(), DELTA_I_A);
        assertEquals(2682.67577453832, m1.getCurrent(), DELTA_I_A);
        // assertEquals(2886.75171, m0.getFeederCurrent("G1"), DELTA_I_A); TODO: Add with next Core release
        // assertEquals(2624.31941, m1.getFeederCurrent("G1"), DELTA_I_A); TODO: Add with next Core release
        assertEquals(6.662151249755523E-14, m0.getVoltage(), DELTA_V);
        assertEquals(6.662151249755523E-14, m1.getVoltage(), DELTA_V);
        assertEquals(0.28227759385913187, ((MagnitudeShortCircuitBusResults) m0.getShortCircuitBusResults().get(1)).getVoltage(), DELTA_V);
        assertEquals(9.090909090909093, ((MagnitudeShortCircuitBusResults) m1.getShortCircuitBusResults().getFirst()).getVoltage(), DELTA_V);
        assertEquals("OpenShortCircuit", providerName);
        assertEquals("0.1", providerVersion);
    }

    /**
     * Verifies handling of:
     * - unknown elements
     * - bus faults on a branch element
     * - branch faults on non-branch element
     */
    @Test
    void openShortCircuitProvider2nInvalidElementId() {
        Network nt2 = create2n(NetworkFactory.findDefault());

        //CompletableFuture<ShortCircuitAnalysisResult> scar = provider.run(nt2, scp, cm);
        BusFault bf1 = new BusFault("F1", "B1");
        BusFault bf2 = new BusFault("F2", "UNKNOWN_BUS");
        BusFault bf3 = new BusFault("F3", "B1_B2");
        BranchFault bf4 = new BranchFault("F4", "B1_B2", 10);
        BranchFault bf5 = new BranchFault("F5", "UNKNOWN_BRANCH", 10);
        BranchFault bf6 = new BranchFault("F6", "B1", 10);

        List<Fault> faults = List.of(bf1, bf2, bf3, bf4, bf5, bf6);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        assertEquals(2, scar.getFaultResults().size());
        assertNull(scar.getFaultResult("F2"));
        assertNull(scar.getFaultResult("F3"));
        assertNull(scar.getFaultResult("F5"));
        assertNull(scar.getFaultResult("F6"));

        MagnitudeFaultResult m1 = (MagnitudeFaultResult) scar.getFaultResult("F1");
        MagnitudeFaultResult m4 = (MagnitudeFaultResult) scar.getFaultResult("F4");

        assertEquals(2945.047378902121, m1.getCurrent(), DELTA_I_A);
        assertEquals(2916.471923828125, m4.getCurrent(), DELTA_I_A);

    }

    @Test
    void openShortCircuitProvider2nFaultsOnLine() {
        Network nt2 = create2n(NetworkFactory.findDefault());

        BranchFault bf1 = new BranchFault("F1", "B1_B2", 10);
        BranchFault bf2 = new BranchFault("F2", "B1_B2", 70);

        List<Fault> faults = List.of(bf1, bf2);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        MagnitudeFaultResult m0 = (MagnitudeFaultResult) scar.getFaultResult("F1");
        MagnitudeFaultResult m1 = (MagnitudeFaultResult) scar.getFaultResult("F2");

        assertEquals(2916.471923828125, m0.getCurrent(), DELTA_I_A);
        assertEquals(2756.23681640625, m1.getCurrent(), DELTA_I_A);
    }

    @Test
    void openShortCircuitProvider2nFaultsOnTransformer() {
        Network nt2 = create2nTfo(NetworkFactory.findDefault());

        BranchFault bf1 = new BranchFault("F1", "B1_B2", 10);
        BranchFault bf2 = new BranchFault("F2", "B1_B2", 70);
        List<Fault> faults = List.of(bf1, bf2);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        MagnitudeFaultResult m0 = (MagnitudeFaultResult) scar.getFaultResult("F1");
        MagnitudeFaultResult m1 = (MagnitudeFaultResult) scar.getFaultResult("F2");

        assertEquals(2932.280029296875, m0.getCurrent(), DELTA_I_A);
        assertEquals(2857.96875, m1.getCurrent(), DELTA_I_A);
    }

    @Test
    void openShortCircuitProviderBusAndBranchFaults2n() {
        Network nt2 = create2n(NetworkFactory.findDefault());

        BusFault bf1 = new BusFault("F1", "B1");
        BusFault bf2 = new BusFault("F2", "B2");

        BranchFault bf3 = new BranchFault("F3", "B1_B2", 0);
        BranchFault bf4 = new BranchFault("F4", "B1_B2", 100);

        BranchFault bf5 = new BranchFault("F5", "B1_B2", 70);

        List<Fault> faults = List.of(bf1, bf2, bf3, bf4, bf5);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        assertEquals(2945.047378902121, getCurrentResult(scar, "F1"), DELTA_I_A);
        assertEquals(2682.67577453832, getCurrentResult(scar, "F2"), DELTA_I_A);
        assertEquals(2945.047378902121, getCurrentResult(scar, "F3"), DELTA_I_A);
        assertEquals(2682.67577453832, getCurrentResult(scar, "F4"), DELTA_I_A);
        assertEquals(2756.23681640625, getCurrentResult(scar, "F5"), DELTA_I_A);
    }

    @Test
    void openShortCircuitProvider4n() {
        Network nt4 = create4nShunts(NetworkFactory.findDefault());

        ShortCircuitAnalysisResult scar = provider.run(nt4, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3547.165283203125, 3747.61083984375, 3592.37939453125, 3411.642578125}
        );
        // assertFeederCurrents(frs, new double[]{2700.28195, 2886.75122, 2725.79395, 2586.51782}, "G2"); TODO: Add with next Core release
        assertBusVoltages(frs, new double[]{6.46059465, 0.0, 5.57589579, 10.400857}, 1);
    }

    @Test
    void openShortCircuitProviderBusAndBranchFaults4n() {
        Network nt4 = create4nShunts(NetworkFactory.findDefault());

        BusFault bf1 = new BusFault("F1", "B1");

        BranchFault bf2 = new BranchFault("F2", "B1_B2", 0);
        BranchFault bf3 = new BranchFault("F3", "B1_B2", 30);
        BranchFault bf4 = new BranchFault("F4", "B1_B3", 30);
        BranchFault bf5 = new BranchFault("F5", "B1_B4", 30);
        BranchFault bf6 = new BranchFault("F6", "B2_B3", 30);
        BranchFault bf7 = new BranchFault("F7", "B3_B4", 30);

        List<Fault> faults = List.of(bf1, bf2, bf3, bf4, bf5, bf6, bf7);

        ShortCircuitAnalysisResult scar = provider.run(nt4, faults, scp, cm, Collections.emptyList()).join();

        assertEquals(3547.1650598424766, getCurrentResult(scar, "F1"), DELTA_I_A);
        assertEquals(3547.1650598424766, getCurrentResult(scar, "F2"), DELTA_I_A);
        assertEquals(3569.175048828125, getCurrentResult(scar, "F3"), DELTA_I_A);
        assertEquals(3497.883544921875, getCurrentResult(scar, "F4"), DELTA_I_A);
        assertEquals(3459.245849609375, getCurrentResult(scar, "F5"), DELTA_I_A);
        assertEquals(3673.041748046875, getCurrentResult(scar, "F6"), DELTA_I_A);
        assertEquals(3505.940673828125, getCurrentResult(scar, "F7"), DELTA_I_A);

    }

    @Test
    void openShortCircuitProvider4nLoadFlowInitialVoltages() {
        //set up LF info
        Network nt4 = create4nShunts(NetworkFactory.findDefault());
        LoadFlow.run(nt4, loadFlowParameters);

        scp.setInitialVoltageProfileMode(InitialVoltageProfileMode.PREVIOUS_VALUE);
        ShortCircuitAnalysisResult scar = provider.run(nt4, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();
        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3523.270263671875, 3767.30078125, 3557.220458984375, 3374.391845703125}
        );
        // assertFeederCurrents(frs, new double[]{3547.77686, 3767.2981, 3564.29761, 3424.16235}, "G2"); // TODO: Add with next Core release
        assertBusVoltages(frs, new double[]{7.60443974, 0.0, 7.03214169, 11.8865681}, 1);
    }

    @Test
    void openShortCircuitProvider4nTfoLoadFlowInitialVoltages() {
        Network network4nTfo = create4nTfoRatioTapChanger(NetworkFactory.findDefault());
        LoadFlow.run(network4nTfo, loadFlowParameters);
        scp.setInitialVoltageProfileMode(InitialVoltageProfileMode.PREVIOUS_VALUE);

        ShortCircuitAnalysisResult scar = provider.run(network4nTfo, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();
        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3516.67236328125, 3792.351318359375, 3649.132568359375, 2368.190673828125}
        );
        // assertFeederCurrents(frs, new double[]{3592.60962, 3792.05566, 3601.41699, 3570.39941}, "G2"); // TODO: Add with next Core release
        assertBusVoltages(frs, new double[]{6.90904474, 0.0, 6.60392666, 7.67868948}, 1);
    }

    @Test
    void openShortCircuitProvider2nTfo() {
        Network nt2 = create2nTfo(NetworkFactory.findDefault());

        List<Fault> faults = List.of(
                new BusFault("F1", "B1"),
                new BusFault("F2", "B2"));

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();
        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{2945.050248502227, 1881.491000035193}
        );
    }

    @Test
    void openShortCircuitProvider2nTfoLoadFlowVoltages() {
        Network nt2Tfo = create2nTfo(NetworkFactory.findDefault());
        LoadFlow.run(nt2Tfo, loadFlowParameters);
        scp.setInitialVoltageProfileMode(InitialVoltageProfileMode.PREVIOUS_VALUE);

        List<Fault> faults = List.of(
                new BusFault("F1", "B1"),
                new BusFault("F2", "B2"));

        ShortCircuitAnalysisResult scar = provider.run(nt2Tfo, faults, scp, cm, Collections.emptyList()).join();
        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs, new double[]{2945.155029296875, 1879.8883056640625});
        // assertFeederCurrents(frs, new double[]{2945.104, 2819.78149}, "G1"); // TODO: Add with next Core release
        assertBusVoltages(frs, new double[]{0.0, 4.34129143}, 0);
        assertBusVoltages(frs, new double[]{0.000213178486, 0.0}, 1);

    }

    /**
     * Verifies short-circuit current calculations on a 2-node network
     * having configured voltage profile.
     */
    @Test
    void openShortCircuitProvider2nTfoConfiguredVoltages() {
        Network network2nTfo = create2nTfo(NetworkFactory.findDefault());

        scp.setInitialVoltageProfileMode(InitialVoltageProfileMode.CONFIGURED)
                .setVoltageRanges(List.of(
                        new VoltageRange(90, 110, 0.9),
                        new VoltageRange(140, 160, 1.1)));

        List<Fault> faults = List.of(
                new BusFault("F1", "B1"),
                new BusFault("F2", "B2"));

        ShortCircuitAnalysisResult scar = provider.run(network2nTfo, faults, scp, cm, Collections.emptyList()).join();
        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{2641.36792, 2062.15551}
        );
        // assertFeederCurrents(frs, new double[]{2598.07642, 3040.30176}, "G1"); // TODO: Add with next Core release
        assertBusVoltages(frs, new double[]{30.09933, 0.0}, 1);
    }

    @Test
    void shortCircuitSystematic() {
        Network network = create2n(NetworkFactory.findDefault());
        List<ShortCircuitFault> faults = new ArrayList<>();

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SYSTEMATIC, faults, scp, new ShortCircuitNormNone());
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);
        scbEngine.run();

        List<Double> val = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getId().getReal());
        }

        assertEquals(0.0996007987855852, val.get(0), DELTA_I_KA); // kA and not A
        assertEquals(0.0999999987871081, val.get(1), DELTA_I_KA);

    }

    @Test
    void shortCircuit8NodesIEC9094() {
        Network network = ReferenceNetwork.create8NodesIEC9094();

        ShortCircuitFault sc1 = createZeroImpedanceTriphasedGroundFault("sc1", "B1");
        ShortCircuitFault sc2 = createZeroImpedanceTriphasedGroundFault("sc2", "B2");
        ShortCircuitFault sc3 = createZeroImpedanceTriphasedGroundFault("sc3", "B3");
        ShortCircuitFault sc4 = createZeroImpedanceTriphasedGroundFault("sc4", "B4");
        ShortCircuitFault sc5 = createZeroImpedanceTriphasedGroundFault("sc5", "B5");
        ShortCircuitFault sc6 = createZeroImpedanceTriphasedGroundFault("sc6", "B6");
        ShortCircuitFault sc7 = createZeroImpedanceTriphasedGroundFault("sc7", "B7");
        ShortCircuitFault sc8 = createZeroImpedanceTriphasedGroundFault("sc8", "B8");

        List<ShortCircuitFault> faultList = List.of(sc1, sc2, sc3, sc4, sc5, sc6, sc7, sc8);

        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        scp.setStudyType(StudyType.TRANSIENT);
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, scp, shortCircuitNormIec);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        Map<ShortCircuitFault, Double> valuesIk = new HashMap<>();
        Map<ShortCircuitFault, Complex> valuesZd = new HashMap<>();
        Map<ShortCircuitFault, Complex> valuesId = new HashMap<>();
        Map<ShortCircuitFault, Complex> valuesVd = new HashMap<>();
        Map<ShortCircuitFault, Complex> valuesEth = new HashMap<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            valuesIk.put(res.getKey(), res.getValue().getIk().abs());
            valuesZd.put(res.getKey(), res.getValue().getZd());
            valuesId.put(res.getKey(), res.getValue().getId());
            valuesVd.put(res.getKey(), res.getValue().getVd());
            valuesEth.put(res.getKey(), res.getValue().getEth());
        }

        // I"k = 1/sqrt(3) * cmax * Un /(Zeq)
        assertEquals(40.64478476116188, valuesIk.get(sc1), 100 * DELTA_I_KA); // bus 1 : expected in doc = 40.6447 kA
        assertEquals(31.783052222534174, valuesIk.get(sc2), 100 * DELTA_I_KA); // bus 2 : expected in doc =  31.7831 kA
        assertEquals(19.672955775750143, valuesIk.get(sc3), 100 * DELTA_I_KA); // bus 3 : expected in doc =  19.673 kA
        assertEquals(16.227655866910894, valuesIk.get(sc4), 100 * DELTA_I_KA); // bus 4 : expected in doc =  16.2277 kA
        assertEquals(33.18941481677016, valuesIk.get(sc5), 100 * DELTA_I_KA); // bus 5 : expected in doc =  33.1894 kA
        assertEquals(37.56287899040728, valuesIk.get(sc6), 100 * DELTA_I_KA); // bus 6 : expected in doc =  37.5629 kA
        assertEquals(25.589463480212533, valuesIk.get(sc7), 100 * DELTA_I_KA); // bus 7 : expected in doc =  25.5895 kA
        assertEquals(13.577771545200052, valuesIk.get(sc8), 100 * DELTA_I_KA); // bus 8 : expected in doc =  13.5778 kA

        assertEquals(0.004092194811702985, valuesZd.get(sc1).getImaginary(), DELTA_Z);
        assertEquals(0.017334559734154213, valuesZd.get(sc5).getImaginary(), DELTA_Z);
        assertEquals(0.2377979119689316, valuesZd.get(sc7).getImaginary(), DELTA_Z);

        assertEquals(1.153344202949403, valuesId.get(sc7).getReal(), DELTA_I_KA / 10);
        assertEquals(-242.02989930029395, valuesId.get(sc1).getImaginary(), DELTA_I_KA / 10);

        assertEquals(0., valuesVd.get(sc6).getReal(), DELTA_V);
        assertEquals(0., valuesVd.get(sc2).getImaginary(), DELTA_V);

        assertEquals(1., valuesEth.get(sc6).getReal(), DELTA_V);
        assertEquals(0., valuesEth.get(sc2).getImaginary(), DELTA_V);

        //assertEquals(4039.8610235151364, values.get(8), 0.1); // T3 U0 node : for check only
        //assertEquals(4039.8610235151364, values.get(8), 0.1); // T4 U0 node : for check only

        // Test on the IEC norm
        TwoWindingsTransformer t2w = network.getTwoWindingsTransformer("T5");
        double kt2w = shortCircuitNormIec.getKtT2W(t2w);

        assertEquals(0.9765171405429419, kt2w, DELTA_K / 100);
        assertEquals("IEC", shortCircuitNormIec.getNormType());
        assertEquals(1., shortCircuitNormIec.getCminVoltageFactor(30.), DELTA_K);

        ShortCircuitNormNone shortCircuitNormNone = new ShortCircuitNormNone();

        kt2w = shortCircuitNormNone.getKtT2W(t2w);

        assertEquals(1.0, kt2w, 0.0000001);
        assertEquals("NONE", shortCircuitNormNone.getNormType());
        assertEquals(1., shortCircuitNormNone.getCminVoltageFactor(30.), DELTA_K);

        ThreeWindingsTransformer t3w = network.getThreeWindingsTransformer("T3");
        double kt3w = shortCircuitNormIec.getKtT3Wij(t3w, 1, 2);
        assertEquals(1.011217185074488, kt3w, DELTA_K);
        kt3w = shortCircuitNormIec.getKtT3Wij(t3w, 1, 3);
        assertEquals(0.9344644092032702, kt3w, DELTA_K);
        kt3w = shortCircuitNormIec.getKtT3Wij(t3w, 2, 3);
        assertEquals(0.9638258888806438, kt3w, DELTA_K);
        kt3w = shortCircuitNormIec.getKtT3Wij(t3w, 3, 1);
        assertEquals(0.9344644092032702, kt3w, DELTA_K);

        shortCircuitNormIec.setKtT3Wi(t3w);

        ThreeWindingsTransformerFortescue extension = t3w.getExtension(ThreeWindingsTransformerFortescue.class);
        double coefcX0 = extension.getLeg3().getXz() / t3w.getLeg3().getX();
        assertEquals(1.0, coefcX0, DELTA_Z);

        ThreeWindingsTransformerNorm t3wNormExtension = shortCircuitNormIec.getNormExtensions().getNormExtension(t3w);
        double kTaR = t3wNormExtension.getLeg1().getKtR();
        double kTbX = t3wNormExtension.getLeg2().getKtX();
        assertEquals(0.86939867723079, kTaR, DELTA_K);
        assertEquals(-6.710685661589687, kTbX, DELTA_K);

        Generator g1 = network.getGenerator("G1");
        double kg = shortCircuitNormIec.getKg(g1);
        assertEquals(1.0015680959819921, kg, DELTA_K);
    }

    @Test
    void shortCircuit6NodesIEC9094subtransient() {
        Network network = ReferenceNetwork.create6NodesIec9094();

        ShortCircuitFault sc1 = createZeroImpedanceTriphasedGroundFault("F1", "B3");
        ShortCircuitFault sc2 = createZeroImpedanceTriphasedGroundFault("F2", "B4");
        ShortCircuitFault sc3 = createZeroImpedanceTriphasedGroundFault("F3", "B6");
        List<ShortCircuitFault> faultList = List.of(sc1, sc2, sc3);

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, scp, new ShortCircuitNormIec());
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        Map<ShortCircuitFault, Double> val = new HashMap<>();
        Map<ShortCircuitFault, Double> coefPeakb = new HashMap<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.put(res.getKey(), res.getValue().getIk().abs());
            coefPeakb.put(res.getKey(), res.getValue().getPeakCoefb());
        }

        // here Icc = 1/sqrt(3)*Eth(pu)/Zth(pu100)*Sb100/Vb*1000
        // and I"k = 1/sqrt(3) * cmax * Un /(Zeq) and expected I"k = 34.62 kA
        assertEquals(34.62398968800272, val.get(sc1), DELTA_I_KA); // F1 expected value in the document: 34.62 kA
        assertEquals(34.1162841954478, val.get(sc2), DELTA_I_KA); // F2 expected value in the document: 34.12 kA
        assertEquals(6.945173672144295, val.get(sc3), DELTA_I_KA); // F3 expected value in the document: 6.95 kA

        // Peak current method b
        assertEquals(70.73492731970777, val.get(sc1) * coefPeakb.get(sc1) * Math.sqrt(2.), DELTA_I_KA); // FIXME: expected 81.36 kA but factor 1.15 not triggered: check R/X ratio of all lines
        assertEquals(69.04648240748665, val.get(sc2) * coefPeakb.get(sc2) * Math.sqrt(2.), DELTA_I_KA);
        assertEquals(11.922267036509417, val.get(sc3) * coefPeakb.get(sc3) * Math.sqrt(2.), DELTA_I_KA);
    }

    @Test
    void shortCircuitSubTransientReference() {
        Network network = ReferenceNetwork.createShortCircuitReference();

        ShortCircuitFault sc1 = createZeroImpedanceTriphasedGroundFault("sc1", "B7");
        ShortCircuitFault sc2 = createTriphasedGroundFault("sc2", "B7", new Complex(0.0001, 0.0002));

        List<ShortCircuitFault> faultList = List.of(sc1, sc2);

        scp.setWithNeutralPosition(true);
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, scp, new ShortCircuitNormNone());
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        Map<ShortCircuitFault, Double> val = new HashMap<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.put(res.getKey(), res.getValue().getIk().abs());
        }

        // here Icc = 1/sqrt(3)*Eth(pu)/Zth(pu100)*Sb100/Vb*1000
        // and Idocumentation = Ib*Eth(pu)/Zth(pu15) then Idocumentation = Icc * Ib * sqrt(3) * Vb / (1000 * Sb15)  with Ib = 18.064
        // in the documentation, expected Idocumentation ~ 35.656 kA
        assertEquals(35.69309945355154, val.get(sc1) * 18.064 * 0.277 * Math.sqrt(3) / 15., DELTA_I_KA);
        assertEquals(35.69084362105586, val.get(sc2) * 18.064 * 0.277 * Math.sqrt(3) / 15., DELTA_I_KA);

    }

    /**
     * Verifies short-circuit current calculations on a 2-node network for
     * bus faults with fault impedance modeled using both series and parallel
     * connection types.
     */
    @Test
    void openShortCircuitProvider2nWithFaultImpedance() {

        Network nt2 = create2n(NetworkFactory.findDefault());

        BusFault bf1 = new BusFault("F1", "B1", 0.5, 1, Fault.ConnectionType.SERIES, Fault.FaultType.THREE_PHASE);
        BusFault bf2 = new BusFault("F2", "B2", 5, 10, Fault.ConnectionType.PARALLEL, Fault.FaultType.THREE_PHASE);
        List<Fault> faults = List.of(bf1, bf2);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        MagnitudeFaultResult m0 = (MagnitudeFaultResult) frs.getFirst();
        MagnitudeFaultResult m1 = (MagnitudeFaultResult) frs.get(1);

        assertEquals(2800.05127, m0.getCurrent(), DELTA_I_A);
        assertEquals(2412.03491, m1.getCurrent(), DELTA_I_A);
    }

    @Test
    void openShortCircuitProvider4nTfo() {
        Network network4nTfo = create4nTfoRatioTapChanger(NetworkFactory.findDefault());
        // Remove the 2 ratio tap changers in the network
        network4nTfo.getTwoWindingsTransformer("TFO_B1_B4").getRatioTapChanger().remove();
        network4nTfo.getTwoWindingsTransformer("TFO_B3_B4").getRatioTapChanger().remove();
        LoadFlow.run(network4nTfo, loadFlowParameters);

        ShortCircuitAnalysisResult scar = provider.run(network4nTfo, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();
        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3526.60254, 3695.30591, 3554.53003, 2341.2373}
        );
    }

    @Test
    void openShortCircuitProvider4nRatioTapChangerNeutralPosition() {
        Network network4nRtc = create4nTfoRatioTapChanger(NetworkFactory.findDefault());

        scp.setWithNeutralPosition(true);
        ShortCircuitAnalysisResult scar = provider.run(network4nRtc, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();
        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3509.85864, 3697.65112, 3574.19287, 2274.43921}
        );
    }

    @Test
    void openShortCircuitProvider4nRatioTapChangerPredefinedPosition() {
        Network network4nRtc = create4nTfoRatioTapChanger(NetworkFactory.findDefault());

        scp.setWithNeutralPosition(false);
        ShortCircuitAnalysisResult scar = provider.run(network4nRtc, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();
        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3469.37451, 3757.03662, 3721.92114, 2379.39624}
        );
    }

    @Test
    void openShortCircuitProvider4nTapChangerConfiguredVoltages() {
        Network network4nTfo = create4nTfoRatioTapChanger(NetworkFactory.findDefault());

        scp.setInitialVoltageProfileMode(InitialVoltageProfileMode.CONFIGURED)
                .setVoltageRanges(List.of(
                        new VoltageRange(90, 110, 0.9),
                        new VoltageRange(140, 160, 1.1)));

        ShortCircuitAnalysisResult scar = provider.run(network4nTfo, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();
        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3222.086181640625, 3486.87646484375, 3462.0830078125, 2701.750732421875}
        );
        // assertFeederCurrents(frs, new double[]{2407.81299, 2598.07642, 2500.03687, 2972.69214}, "G2"); // TODO: Add with next Core release
        assertBusVoltages(frs, new double[]{6.59124184, 0.0, 3.39618897, 12.9770918}, 1);
    }

    private static List<Fault> createBusFaultsFor4n() {
        return List.of(
                new BusFault("F1", "B1"),
                new BusFault("F2", "B2"),
                new BusFault("F3", "B3"),
                new BusFault("F4", "B4")
        );
    }

    private static ShortCircuitFault createZeroImpedanceTriphasedGroundFault(String id, String busId) {
        return createTriphasedGroundFault(id, busId, Complex.ZERO);
    }

    private static ShortCircuitFault createTriphasedGroundFault(String id, String busId, Complex impedance) {
        return new ShortCircuitFault(busId, id, busId, new ShortCircuitFaultImpedance(impedance),
                ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
    }

    private static double getCurrentResult(ShortCircuitAnalysisResult scar, String faultId) {
        return ((MagnitudeFaultResult) scar.getFaultResult(faultId)).getCurrent();
    }

    private static void assertMagnitudeCurrents(List<FaultResult> faultResults, double[] expected) {
        for (int i = 0; i < expected.length; i++) {
            MagnitudeFaultResult result = (MagnitudeFaultResult) faultResults.get(i);
            assertEquals(expected[i], result.getCurrent(), ShortCircuitBalancedTest.DELTA_I_A);
        }
    }

    private static void assertFeederCurrents(List<FaultResult> faultResults, double[] expected, String genId) {
        for (int i = 0; i < expected.length; i++) {
            double result = faultResults.get(i).getFeederCurrent(genId);
            assertEquals(expected[i], result, ShortCircuitBalancedTest.DELTA_I_A);
        }
    }

    private static void assertBusVoltages(List<FaultResult> faultResults, double[] expected, int busNum) {
        for (int i = 0; i < expected.length; i++) {
            MagnitudeShortCircuitBusResults magnitudeResult = (MagnitudeShortCircuitBusResults) faultResults.get(i).getShortCircuitBusResults().get(busNum);
            double result = magnitudeResult.getVoltage();
            assertEquals(expected[i], result, ShortCircuitBalancedTest.DELTA_V);
        }
    }
}
