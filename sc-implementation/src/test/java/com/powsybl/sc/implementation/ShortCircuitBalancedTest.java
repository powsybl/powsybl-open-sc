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
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerFortescue;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.sc.util.ReferenceNetwork;
import com.powsybl.sc.util.extensions.ThreeWindingsTransformerNorm;
import com.powsybl.shortcircuit.*;
import org.apache.commons.math3.complex.Complex;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ShortCircuitBalancedTest {

    private LoadFlowParameters parameters;

    private MatrixFactory matrixFactory;

    private LoadFlow.Runner loadFlowRunner;

    private static final double DELTA_I_A = 1e-2;
    private static final double DELTA_I_KA = DELTA_I_A / 1e3; // For tests not using Core API -> to be replaced
    private static final double DELTA_V = 1e-5;
    private static final double DELTA_Z = 1e-5;
    private static final double DELTA_K = 1e-5;

    private LoadFlowParameters loadFlowParameters;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        matrixFactory = new DenseMatrixFactory();
        loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(matrixFactory));
    }

    @Disabled
    @Test
    void computeIccTest() {
        Network nt2 = create2n(NetworkFactory.findDefault());
        LoadFlowResult resultnt2 = loadFlowRunner.run(nt2, parameters);

        List<ShortCircuitFault> tmpV = new ArrayList<>();
        ShortCircuitFault sc2 = new ShortCircuitFault("B2", "sc2", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        tmpV.add(sc2);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.TRANSIENT;

        ShortCircuitEngineParameters.VoltageProfileType vp = ShortCircuitEngineParameters.VoltageProfileType.CALCULATED;
        ShortCircuitEngineParameters.AnalysisType at = ShortCircuitEngineParameters.AnalysisType.SELECTIVE;

        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, at, tmpV, true, vp, false, periodType, shortCircuitNorm);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(nt2, scbParameters);

        scbEngine.run();

        scbEngine.resultsPerFault.get(sc2).updateFeedersResult();

        assertEquals(-0.4316661015058293, scbEngine.resultsPerFault.get(sc2).getId().getReal(), DELTA_I_KA / 10); // kA and not A
        assertEquals(-4.617486568622836, scbEngine.resultsPerFault.get(sc2).getId().getImaginary(), DELTA_I_KA / 10);

    }

    @Test
    void openShortCircuitProvider2n() {

        //set up LF info
        Network nt2 = create2n(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters().setStudyType(StudyType.SUB_TRANSIENT);

        List<Fault> faults = new ArrayList<>();
        BusFault bf1 = new BusFault("F1", "B1");
        BusFault bf2 = new BusFault("F2", "B2");
        faults.add(bf1);
        faults.add(bf2);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        String providerName = provider.getName();
        String providerVersion = provider.getVersion();

        MagnitudeFaultResult m0 = (MagnitudeFaultResult) frs.get(0);
        MagnitudeFaultResult m1 = (MagnitudeFaultResult) frs.get(1);

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

    @Test
    void openShortCircuitProvider4n() {
        //set up LF info
        Network nt4 = create4n(NetworkFactory.findDefault());
        LoadFlow.run(nt4, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters().setStudyType(StudyType.SUB_TRANSIENT);

        ShortCircuitAnalysisResult scar = provider.run(nt4, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3547.165283203125, 3747.61083984375, 3592.37939453125, 3411.642578125}
        );
        // assertFeederCurrents(frs, new double[]{2700.28195, 2886.75122, 2725.79395, 2586.51782}, "G2"); TODO: Add with next Core release
        assertBusVoltages(frs, new double[]{6.46059465, 0.0, 5.57589579, 10.400857}, 1);
    }

    @Test
    void openShortCircuitProvider4nLoadFlowInitialVoltages() {
        //set up LF info
        Network nt4 = create4n(NetworkFactory.findDefault());
        LoadFlow.run(nt4, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters()
                .setStudyType(StudyType.SUB_TRANSIENT)
                .setInitialVoltageProfileMode(InitialVoltageProfileMode.PREVIOUS_VALUE);

        ShortCircuitAnalysisResult scar = provider.run(nt4, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{3523.270263671875, 3767.30078125, 3557.220458984375, 3374.391845703125}
        );
        // assertFeederCurrents(frs, new double[]{3547.77686, 3767.2981, 3564.29761, 3424.16235}, "G2"); // TODO HG: Check values against CC and add with next Core release
        assertBusVoltages(frs, new double[]{7.60443974, 0.0, 7.03214169, 11.8865681}, 1);
    }

    @Test
    void openShortCircuitProvider2nTfo() {

        //set up LF info
        Network nt2 = create2nTfo(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters().setStudyType(StudyType.SUB_TRANSIENT);

        //CompletableFuture<ShortCircuitAnalysisResult> scar = provider.run(nt2, scp, cm);
        List<Fault> faults = new ArrayList<>(); // TODO

        BusFault bf1 = new BusFault("F1", "B1");
        BusFault bf2 = new BusFault("F2", "B2");
        faults.add(bf1);
        faults.add(bf2);

        ShortCircuitAnalysisResult scar = provider.run(nt2, faults, scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        assertMagnitudeCurrents(frs,
                new double[]{2945.050248502227, 1881.491000035193}
        );

    }

    @Test
    void shortCircuitSystematic() {

        Network nt2 = create2n(NetworkFactory.findDefault());
        LoadFlow.run(nt2, loadFlowParameters);

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> tmpV = new ArrayList<>();

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.TRANSIENT;

        ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, ShortCircuitEngineParameters.AnalysisType.SYSTEMATIC, tmpV, false, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNorm);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(nt2, scbParameters);

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

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        Complex zFaultToGround = new Complex(0.);
        ShortCircuitFaultImpedance scFaultz = new ShortCircuitFaultImpedance(zFaultToGround);
        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B1", "sc1", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc1);
        ShortCircuitFault sc2 = new ShortCircuitFault("B2", "sc2", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc2);
        ShortCircuitFault sc3 = new ShortCircuitFault("B3", "sc3", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc3);
        ShortCircuitFault sc4 = new ShortCircuitFault("B4", "sc4", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc4);
        ShortCircuitFault sc5 = new ShortCircuitFault("B5", "sc5", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc5);
        ShortCircuitFault sc6 = new ShortCircuitFault("B6", "sc6", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc6);
        ShortCircuitFault sc7 = new ShortCircuitFault("B7", "sc7", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc7);
        ShortCircuitFault sc8 = new ShortCircuitFault("B8", "sc8", scFaultz, ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc8);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        List<Double> valuesIk = new ArrayList<>();
        List<Complex> valuesZd = new ArrayList<>();
        List<Complex> valuesId = new ArrayList<>();
        List<Complex> valuesVd = new ArrayList<>();
        List<Complex> valuesEth = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            valuesIk.add(res.getValue().getIk().abs());
            valuesZd.add(res.getValue().getZd());
            valuesId.add(res.getValue().getId());
            valuesVd.add(res.getValue().getVd());
            valuesEth.add(res.getValue().getEth());
        }

        // I"k = 1/sqrt(3) * cmax * Un /(Zeq)
        assertEquals(40.64478476116188, valuesIk.get(0), 100 * DELTA_I_KA); // bus 1 : expected in doc = 40.6447 kA
        assertEquals(31.783052222534174, valuesIk.get(1), 100 * DELTA_I_KA); // bus 2 : expected in doc =  31.7831 kA
        assertEquals(19.672955775750143, valuesIk.get(2), 100 * DELTA_I_KA); // bus 3 : expected in doc =  19.673 kA
        assertEquals(16.227655866910894, valuesIk.get(3), 100 * DELTA_I_KA); // bus 4 : expected in doc =  16.2277 kA
        assertEquals(33.18941481677016, valuesIk.get(4), 100 * DELTA_I_KA); // bus 5 : expected in doc =  33.1894 kA
        assertEquals(37.56287899040728, valuesIk.get(5), 100 * DELTA_I_KA); // bus 6 : expected in doc =  37.5629 kA
        assertEquals(25.589463480212533, valuesIk.get(6), 100 * DELTA_I_KA); // bus 7 : expected in doc =  25.5895 kA
        assertEquals(13.577771545200052, valuesIk.get(7), 100 * DELTA_I_KA); // bus 8 : expected in doc =  13.5778 kA

        assertEquals(0.004092194811702985, valuesZd.get(0).getImaginary(), DELTA_Z);
        assertEquals(0.017334559734154213, valuesZd.get(4).getImaginary(), DELTA_Z);
        assertEquals(0.2377979119689316, valuesZd.get(6).getImaginary(), DELTA_Z);

        assertEquals(1.153344202949403, valuesId.get(6).getReal(), DELTA_I_KA / 10);
        assertEquals(-242.02989930029395, valuesId.get(0).getImaginary(), DELTA_I_KA / 10);

        assertEquals(0., valuesVd.get(5).getReal(), DELTA_V);
        assertEquals(0., valuesVd.get(1).getImaginary(), DELTA_V);

        assertEquals(1., valuesEth.get(5).getReal(), DELTA_V);
        assertEquals(0., valuesEth.get(1).getImaginary(), DELTA_V);

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

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B3", "F1", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc1);
        ShortCircuitFault sc2 = new ShortCircuitFault("B4", "F2", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc2);
        ShortCircuitFault sc3 = new ShortCircuitFault("B6", "F3", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc3);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        List<Double> val = new ArrayList<>();
        List<Double> coefPeakb = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getIk().abs());
            coefPeakb.add(res.getValue().getPeakCoefb());
        }

        // here Icc = 1/sqrt(3)*Eth(pu)/Zth(pu100)*Sb100/Vb*1000
        // and I"k = 1/sqrt(3) * cmax * Un /(Zeq) and expected I"k = 34.62 kA
        assertEquals(34.62398968800272, val.get(0), DELTA_I_KA); // F1 expected value in the document: 34.62 kA
        assertEquals(34.1162841954478, val.get(1), DELTA_I_KA); // F2 expected value in the document: 34.12 kA
        assertEquals(6.945173672144295, val.get(2), DELTA_I_KA); // F3 expected value in the document: 6.95 kA

        // Peak current method b
        assertEquals(70.73492731970777, val.get(0) * coefPeakb.get(0) * Math.sqrt(2.), DELTA_I_KA); // FIXME: expected 81.36 kA but factor 1.15 not triggered: check R/X ratio of all lines
        assertEquals(69.04648240748665, val.get(1) * coefPeakb.get(1) * Math.sqrt(2.), DELTA_I_KA);
        assertEquals(11.922267036509417, val.get(2) * coefPeakb.get(2) * Math.sqrt(2.), DELTA_I_KA);
    }

    @Test
    void shortCircuitSubTransientReference() {

        Network network = ReferenceNetwork.createShortCircuitReference();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B7", "sc1", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc1);

        ShortCircuitFault sc2 = new ShortCircuitFault("B7", "sc2", new ShortCircuitFaultImpedance(new Complex(0.0001, 0.0002)), ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND);
        faultList.add(sc2);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;

        ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, true, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNorm);
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);

        scbEngine.run();
        List<Double> val = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getIk().abs());
        }

        // here Icc = 1/sqrt(3)*Eth(pu)/Zth(pu100)*Sb100/Vb*1000
        // and Idocumentation = Ib*Eth(pu)/Zth(pu15) then Idocumentation = Icc * Ib * sqrt(3) * Vb / (1000 * Sb15)  with Ib = 18.064
        // in the documentation, expected Idocumentation ~ 35.656 kA
        assertEquals(35.69309945355154, val.get(0) * 18.064 * 0.277 * Math.sqrt(3) / 15., DELTA_I_KA);
        assertEquals(35.69084362105586, val.get(1) * 18.064 * 0.277 * Math.sqrt(3) / 15., DELTA_I_KA);

    }

    /**
     * Verifies short-circuit current calculations on a 4-node network
     * having transformers.
     */
    @Test
    void openShortCircuitProvider4nTfo() {
        //set up LF info
        Network network4nTfo = create4nTfoRatioTapChanger(NetworkFactory.findDefault());
        // Remove the 2 ratio tap changers in the network
        network4nTfo.getTwoWindingsTransformer("TFO_B1_B4").getRatioTapChanger().remove();
        network4nTfo.getTwoWindingsTransformer("TFO_B3_B4").getRatioTapChanger().remove();
        LoadFlow.run(network4nTfo, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters();

        //CompletableFuture<ShortCircuitAnalysisResult> scar = provider.run(network4Tfo, scp, cm);

        ShortCircuitAnalysisResult scar = provider.run(network4nTfo, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        // Note: Courcirc's results: new double[]{3526.60254, 3695.30591, 3554.53003, 2341.2373},
        assertMagnitudeCurrents(frs,
                new double[]{3526.46556, 3695.17940, 3554.39777, 2342.88995}
        );
    }

    /**
     * Verifies short-circuit current calculations on a 4-node network
     * having ratio tap changers.
     * WithNeutralPosition is set to True.
     */
    @Test
    void openShortCircuitProvider4nRatioTapChangerNeutralPosition() {
        //set up LF info
        Network network4nRtc = create4nTfoRatioTapChanger(NetworkFactory.findDefault());
        LoadFlow.run(network4nRtc, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters().setWithNeutralPosition(true);

        //CompletableFuture<ShortCircuitAnalysisResult> scar = provider.run(network4Tfo, scp, cm);

        ShortCircuitAnalysisResult scar = provider.run(network4nRtc, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        // Note: Courcirc's results: new double[]{3509.85864, 3697.65112, 3574.19287, 2274.43921},
        assertMagnitudeCurrents(frs,
                new double[]{3509.7220546590897, 3697.5206241890896, 3574.052947845015, 2276.0427607299207}
        );
    }


    /**
     * Verifies short-circuit current calculations on a 4-node network
     * having ratio tap changers.
     * WithNeutralPosition is set to False
     */
    @Test
    void openShortCircuitProvider4nRatioTapChangerPredefinedPosition() {
        //set up LF info
        Network network4nRtc = create4nTfoRatioTapChanger(NetworkFactory.findDefault());
        LoadFlow.run(network4nRtc, loadFlowParameters);

        //set up ShortCircuitProvider info
        ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new DenseMatrixFactory());
        ComputationManager cm = LocalComputationManager.getDefault();
        ShortCircuitParameters scp = new ShortCircuitParameters().setWithNeutralPosition(false);

        //CompletableFuture<ShortCircuitAnalysisResult> scar = provider.run(network4Tfo, scp, cm);

        ShortCircuitAnalysisResult scar = provider.run(network4nRtc, createBusFaultsFor4n(), scp, cm, Collections.emptyList()).join();

        List<FaultResult> frs = scar.getFaultResults();

        // Note: Courcirc's results: new double[]{3469.37451, 3757.03662, 3721.92114, 2379.39624},
        assertMagnitudeCurrents(frs,
                new double[]{3469.26752, 3757.00617, 3721.94626, 2379.35989}
        );
    }

    public static @NonNull Network create2n(NetworkFactory networkFactory) {
        Objects.requireNonNull(networkFactory);

        double p0l2 = 10;
        double q0l2 = 10;
        double pgen = 10;
        double xl = 2.;

        Network network = networkFactory.createNetwork("2n", "test");
        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(100.0).setAngle(0.);
        Generator gen1 = vl1.newGenerator()
                .setId("G1")
                .setBus(bus1.getId())
                .setMinP(0.0)
                .setMaxP(150)
                .setTargetP(pgen)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        gen1.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(20)
                .withDirectTransX(20)
                .withStepUpTransformerX(0.)
                .add();

        Substation substation2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = substation2.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(100.0).setAngle(0);
        vl2.newLoad()
                .setId("LOAD_2")
                .setBus(bus2.getId())
                .setP0(p0l2)
                .setQ0(q0l2)
                .add();

        network.newLine()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(xl)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        return network;
    }

    public static Network create4n(NetworkFactory networkFactory) {
        Objects.requireNonNull(networkFactory);
        //      2                               3
        //  (~)-|--------------X23--------------|-[X]  Po= 10.  Qo = 100.
        //      |--+                         +--|
        //         |                        /   |--+
        //         |                       /       |
        //         |                      /        |
        //        X12                    /         |
        //         |      +-----X13-----+         X34
        //         |     /                         |
        //      1  |    /                          |
        //      |--+   /                           |
        //      |-----+                         |--+
        //   +--|--------------X14--------------|-----[X] Po= 20.  Qo = 10.
        //   |                                  |--B4
        //   B1

        Network network = networkFactory.createNetwork("4n", "test");
        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(100.0).setAngle(0.);
        // test with shunt (could be removed)
        vl1.newShuntCompensator()
                .setId("SHUNT_1")
                .setBus(bus1.getId())
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .newLinearModel().setMaximumSectionCount(1).setBPerSection(-0.003).add()
                .add();

        Substation substation2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = substation2.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(100.0).setAngle(0);
        Generator gen2 = vl2.newGenerator()
                .setId("G2")
                .setBus(bus2.getId())
                .setMinP(0.0)
                .setMaxP(150)
                .setTargetP(30)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();
        gen2.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectTransX(20.)
                .withDirectSubtransX(20.)
                .withStepUpTransformerX(0.)
                .add();

        Substation substation3 = network.newSubstation()
                .setId("S3")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl3 = substation3.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        bus3.setV(100.0).setAngle(0.);
        vl3.newLoad()
                .setId("LOAD_3")
                .setBus(bus3.getId())
                .setP0(10.0)
                .setQ0(100.)
                .add();

        Substation substation4 = network.newSubstation()
                .setId("S4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation4.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        bus4.setV(100.0).setAngle(0.);
        vl4.newShuntCompensator()
                .setId("SHUNT_4")
                .setBus(bus4.getId())
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .newLinearModel().setMaximumSectionCount(1).setBPerSection(-0.00105).add()
                .add();
        vl4.newLoad()
                .setId("LOAD_4")
                .setBus(bus4.getId())
                .setP0(20.)
                .setQ0(10.)
                .add();

        network.newLine()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B1_B3")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B1_B4")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.6)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        network.newLine()
                .setId("B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        return network;
    }

    public static Network create2nTfo(NetworkFactory networkFactory) {
        Objects.requireNonNull(networkFactory);

        double p0l2 = 10;
        double q0l2 = 10;
        double pGen = 10;
        double xl = 2.;

        Network network = networkFactory.createNetwork("2nTfo", "test");
        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        bus1.setV(100.0).setAngle(0.);

        Generator gen1 = vl1.newGenerator()
                .setId("G1")
                .setBus(bus1.getId())
                .setMinP(0.0)
                .setMaxP(150)
                .setTargetP(pGen)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        gen1.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(20)
                .withDirectTransX(20)
                .withStepUpTransformerX(0.)
                .add();

        VoltageLevel vl2 = substation1.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(150.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        bus2.setV(150.0).setAngle(0);
        vl2.newLoad()
                .setId("LOAD_2")
                .setBus(bus2.getId())
                .setP0(p0l2)
                .setQ0(q0l2)
                .add();

        TwoWindingsTransformer t2w = substation1.newTwoWindingsTransformer()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(xl)
                .setRatedU1(100.0)
                .setRatedU2(150.0)
                .setG(0.0)
                .setB(0.0)
                .add();

        return network;
    }

    /**
     * Creates a variant of the 4-bus benchmark network containing two
     * two-winding transformers.
     *
     * <p>Compared to {@link #create4n(NetworkFactory)}:
     *
     * <ul>
     *   <li>Voltage level VL_4 is changed from 100 kV to 150 kV.</li>
     *   <li>Line B1_B4 is replaced by transformer TFO_B1_B4.</li>
     *   <li>Line B3_B4 is replaced by transformer TFO_B3_B4.</li>
     *   <li>Each transformer has a ratio tap changer.</li>
     *   <li>Lines B1_B2, B1_B3 and B2_B3 are kept unchanged.</li>
     * </ul>
     *
     * <p>The network is organised as follows:
     *
     * <pre>
     * Substation S1
     *   - VL_1 (100 kV) : B1
     *   - VL_3 (100 kV) : B3
     *   - VL_4 (150 kV) : B4
     * Substation S1
     *  - VL_2 (100 kV) : B2
     */
    public static Network create4nTfoRatioTapChanger(NetworkFactory networkFactory) {

        Objects.requireNonNull(networkFactory);
        //      2                               3
        //  (~)-|--------------X23--------------|-[X]  Po= 10.  Qo = 100.
        //      |--+                         +--|
        //         |                        /   |--+
        //         |                       /       |
        //         |                      /        |
        //        X12                    /         |
        //         |      +-----X13-----+        TFO34
        //         |     /                         |
        //      1  |    /                          |
        //      |--+   /                           |
        //      |-----+                         |--+
        //   +--|--------------TFO14--------------|-----[X] Po= 20.  Qo = 10.
        //   |                                  |--B4
        //   B1

        final double nominalV4 = 150.0;

        final double rTransfo = 0.05;
        final double xTransfo = 1.50;

        final double gTransfo = 1E-5;
        final double bTransfo = 5E-4;

        final int tapPosition1 = -1;
        final int tapPosition2 = 1;

        Network network = networkFactory.createNetwork("4nTfo", "test");

        Substation substation1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl1 = substation1.newVoltageLevel()
                .setId("VL_1")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        Bus bus1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();

        bus1.setV(100.0).setAngle(0.0);

        vl1.newShuntCompensator()
                .setId("SHUNT_1")
                .setBus(bus1.getId())
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .newLinearModel()
                .setMaximumSectionCount(1)
                .setBPerSection(-0.003)
                .add()
                .add();

        Substation substation2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl2 = substation2.newVoltageLevel()
                .setId("VL_2")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        Bus bus2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();

        bus2.setV(100.0).setAngle(0.0);

        Generator gen2 = vl2.newGenerator()
                .setId("G2")
                .setBus(bus2.getId())
                .setMinP(0.0)
                .setMaxP(150.0)
                .setTargetP(30.0)
                .setTargetV(100.0)
                .setVoltageRegulatorOn(true)
                .add();

        gen2.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectTransX(20.0)
                .withDirectSubtransX(20.0)
                .withStepUpTransformerX(0.0)
                .add();

        VoltageLevel vl3 = substation1.newVoltageLevel()
                .setId("VL_3")
                .setNominalV(100.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        Bus bus3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();

        bus3.setV(100.0).setAngle(0.0);

        vl3.newLoad()
                .setId("LOAD_3")
                .setBus(bus3.getId())
                .setP0(10.0)
                .setQ0(100.0)
                .add();

        VoltageLevel vl4 = substation1.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(nominalV4)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(200)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();

        bus4.setV(nominalV4).setAngle(0.0);

        vl4.newShuntCompensator()
                .setId("SHUNT_4")
                .setBus(bus4.getId())
                .setSectionCount(1)
                .setVoltageRegulatorOn(false)
                .newLinearModel()
                .setMaximumSectionCount(1)
                .setBPerSection(-0.00105)
                .add()
                .add();

        vl4.newLoad()
                .setId("LOAD_4")
                .setBus(bus4.getId())
                .setP0(20.0)
                .setQ0(10.0)
                .add();

        network.newLine()
                .setId("B1_B2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(0.0)
                .setX(1 / 0.5)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        network.newLine()
                .setId("B1_B3")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.4)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        network.newLine()
                .setId("B2_B3")
                .setVoltageLevel1(vl2.getId())
                .setBus1(bus2.getId())
                .setConnectableBus1(bus2.getId())
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setR(0.0)
                .setX(1 / 0.6)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();

        // Transformers
        TwoWindingsTransformer tfoB1B4 = substation1.newTwoWindingsTransformer()
                .setId("TFO_B1_B4")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setRatedU1(100.0)
                .setRatedU2(nominalV4)
                .setR(rTransfo)
                .setX(xTransfo)
                .setG(gTransfo)
                .setB(bTransfo)
                .add();

        tfoB1B4.newRatioTapChanger()
                .setRegulationMode(RatioTapChanger.RegulationMode.VOLTAGE)
                .setLowTapPosition(-1)
                .setTapPosition(tapPosition1)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(true)
                .setRegulationValue(nominalV4)
                .setTargetDeadband(1.0)
                .setRegulationTerminal(tfoB1B4.getTerminal2())
                .beginStep()
                .setRho(0.97)
                .setR(0)
                .setX(0)
                .setG(0)
                .setB(0)
                .endStep()
                .beginStep()
                .setRho(1.01)
                .setR(0)
                .setX(0)
                .setG(0)
                .setB(0)
                .endStep()
                .beginStep()
                .setRho(1.05)
                .setR(0)
                .setX(0)
                .setG(0)
                .setB(0)
                .endStep()
                .add();

        TwoWindingsTransformer tfoB3B4 = substation1.newTwoWindingsTransformer()
                .setId("TFO_B3_B4")
                .setVoltageLevel1(vl3.getId())
                .setBus1(bus3.getId())
                .setConnectableBus1(bus3.getId())
                .setVoltageLevel2(vl4.getId())
                .setBus2(bus4.getId())
                .setConnectableBus2(bus4.getId())
                .setRatedU1(100.0)
                .setRatedU2(nominalV4)
                .setR(rTransfo)
                .setX(xTransfo)
                .setG(gTransfo)
                .setB(bTransfo)
                .add();

        tfoB3B4.newRatioTapChanger()
                .setRegulationMode(RatioTapChanger.RegulationMode.VOLTAGE)
                .setLowTapPosition(-1)
                .setTapPosition(tapPosition2)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(true)
                .setRegulationValue(nominalV4)
                .setTargetDeadband(1.0)
                .setRegulationTerminal(tfoB3B4.getTerminal2())
                .beginStep()
                .setRho(0.99)
                .setR(0)
                .setX(0)
                .setG(0)
                .setB(0)
                .endStep()
                .beginStep()
                .setRho(1.02)
                .setR(0)
                .setX(0)
                .setG(0)
                .setB(0)
                .endStep()
                .beginStep()
                .setRho(1.03)
                .setR(0)
                .setX(0)
                .setG(0)
                .setB(0)
                .endStep()
                .add();

        return network;
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

