/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.StudyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class TheveninTest {

    private LoadFlowParameters loadFlowParameters;

    private MatrixFactory matrixFactory;

    @BeforeEach
    void setUp() {
        loadFlowParameters = new LoadFlowParameters()
                .setTwtSplitShuntAdmittance(true);
        matrixFactory = new DenseMatrixFactory();
    }

    @Test
    void computeZthTest() {
        Network network = Networks.create4n();
        LoadFlow.run(network, loadFlowParameters);

        List<CalculationLocation> locationList = List.of(
                new CalculationLocation("B4"),
                new CalculationLocation("B2"));

        AcLoadFlowParameters acLoadFlowParameters = OpenLoadFlowParameters.createAcParameters(network,
                loadFlowParameters, OpenLoadFlowParameters.get(loadFlowParameters), matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>());

        TheveninEquivalentParameters thParameters = new TheveninEquivalentParameters(acLoadFlowParameters, matrixFactory, locationList)
                .setTheveninVoltageProfileType(InitialVoltageProfileMode.PREVIOUS_VALUE);
        TheveninEquivalent thEq = new TheveninEquivalent(network, thParameters);

        thEq.run();
        Map<String, ImpedanceLinearResolution.ImpedanceLinearResolutionResult> resultByBusId =
                thEq.getImpedanceLinearResolution().results.entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> entry.getKey().getId(),
                                Map.Entry::getValue
                        ));
        assertEquals(0.0027661335620416884, resultByBusId.get("VL_2_0").getZthEq().getReal(), 0.000001);
        assertEquals(0.16629396899928067, resultByBusId.get("VL_2_0").getZthEq().getImaginary(), 0.000001);
        assertEquals(0.0030247992008329934, resultByBusId.get("VL_4_0").getZthEq().getReal(), 0.000001);
        assertEquals(0.1833452236067607, resultByBusId.get("VL_4_0").getZthEq().getImaginary(), 0.000001);
        //assertEquals(0.16522876711663945, thEq.results.get(0).getDvr1().get(0), 0.000001);

    }

    @Test
    void referenceTransientTest() {
        Network network = ReferenceNetwork.createShortCircuitReference();

        List<CalculationLocation> locationList = List.of(
                new CalculationLocation("B2"),
                new CalculationLocation("B5"),
                new CalculationLocation("B7"),
                new CalculationLocation("B8"));

        AcLoadFlowParameters acLoadFlowParameters = OpenLoadFlowParameters.createAcParameters(network,
                loadFlowParameters, OpenLoadFlowParameters.get(loadFlowParameters), matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>());

        TheveninEquivalentParameters thParameters = new TheveninEquivalentParameters(acLoadFlowParameters, matrixFactory, locationList);
        TheveninEquivalent thEq = new TheveninEquivalent(network, thParameters);

        thEq.run();
        Map<String, ImpedanceLinearResolution.ImpedanceLinearResolutionResult> resultByBusId =
                thEq.getImpedanceLinearResolution().results.entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> entry.getKey().getId(),
                                Map.Entry::getValue
                        ));

        // results here are with Sbase = 100 MVA we convert them into Sbase = 15 MVA to be in line with the reference doc result :
        assertEquals(0.003683374391319212, resultByBusId.get("VL_2_0").getZthEq().getReal() * 15. / 100., 0.000001); //F1 : doc result = Zth(Sbase15) ~ 0.0036+j0.0712
        assertEquals(0.07118802892811232, resultByBusId.get("VL_2_0").getZthEq().getImaginary() * 15. / 100., 0.000001);
        assertEquals(0.019949496420349225, resultByBusId.get("VL_5_0").getZthEq().getReal() * 15. / 100., 0.000001); //F2 : doc result = Zth(Sbase15) ~ 0.0199+j0.2534
        assertEquals(0.2534161781273357, resultByBusId.get("VL_5_0").getZthEq().getImaginary() * 15. / 100., 0.000001);

    }

    @Test
    void referenceSubTransientTest() {
        Network network = ReferenceNetwork.createShortCircuitReference();

        List<CalculationLocation> locationList = List.of(
                new CalculationLocation("B2"),
                new CalculationLocation("B5"),
                new CalculationLocation("B7"),
                new CalculationLocation("B8"));

        AcLoadFlowParameters acLoadFlowParameters = OpenLoadFlowParameters.createAcParameters(network,
                loadFlowParameters, OpenLoadFlowParameters.get(loadFlowParameters), matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>());

        TheveninEquivalentParameters thParameters = new TheveninEquivalentParameters(acLoadFlowParameters, matrixFactory, locationList)
                .setTheveninStudyType(StudyType.SUB_TRANSIENT);
        TheveninEquivalent thEq = new TheveninEquivalent(network, thParameters);

        thEq.run();
        Map<String, ImpedanceLinearResolution.ImpedanceLinearResolutionResult> resultByBusId =
                thEq.getImpedanceLinearResolution().results.entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> entry.getKey().getId(),
                                Map.Entry::getValue
                        ));
        // results here are with Sbase = 100 MVA we convert them into Sbase = 15 MVA to be in line with the reference doc result :
        assertEquals(0.0035803351059196286, resultByBusId.get("VL_2_0").getZthEq().getReal() * 15. / 100., 0.000001); //F1 : doc result = Zth(Sbase15) ~ 0.0035+j0.0666
        assertEquals(0.0666102621341282, resultByBusId.get("VL_2_0").getZthEq().getImaginary() * 15. / 100., 0.000001);
        assertEquals(0.01766454025768954, resultByBusId.get("VL_5_0").getZthEq().getReal() * 15. / 100., 0.000001); //F2 : doc result = Zth(Sbase15) ~ 0.0175+j0.2313
        assertEquals(0.2313127317660599, resultByBusId.get("VL_5_0").getZthEq().getImaginary() * 15. / 100., 0.000001);
        assertEquals(0.07967413109647312, resultByBusId.get("VL_7_0").getZthEq().getReal() * 15. / 100., 0.000001); //F2 : doc result = Zth(Sbase15) ~ 0.0796+j0.5
        assertEquals(0.4997813218278794, resultByBusId.get("VL_7_0").getZthEq().getImaginary() * 15. / 100., 0.000001);

    }
}
