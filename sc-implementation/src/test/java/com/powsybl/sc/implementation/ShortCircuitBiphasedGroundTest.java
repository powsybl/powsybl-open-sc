/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.sc.util.ReferenceNetwork;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
class ShortCircuitBiphasedGroundTest {

    private LoadFlowParameters parameters;

    private MatrixFactory matrixFactory;

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        matrixFactory = new DenseMatrixFactory();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(matrixFactory));
    }

    @Test
    void shortCircuitIec31Mono() {

        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setTwtSplitShuntAdmittance(true);

        Network network = ReferenceNetwork.createShortCircuitIec31();

        MatrixFactory matrixFactory = new DenseMatrixFactory();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFaultImpedance scZ1 = new ShortCircuitFaultImpedance(new Complex(0.));
        ShortCircuitFault sc1 = new ShortCircuitFault("B3", "sc1", scZ1, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
        faultList.add(sc1);

        ShortCircuitFaultImpedance scZ2 = new ShortCircuitFaultImpedance(new Complex(0.), new Complex(0.0001, 0.0002), new Complex(0.0003, 0.0004));
        ShortCircuitFault sc2 = new ShortCircuitFault("B3", "sc2", scZ2, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
        faultList.add(sc2);

        ShortCircuitFaultImpedance scZ3 = new ShortCircuitFaultImpedance(new Complex(0.000007, 0.00005), new Complex(0.0001, 0.0002), new Complex(0.0003, 0.0004));
        ShortCircuitFault sc3 = new ShortCircuitFault("B3", "sc3", scZ3, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
        faultList.add(sc3);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, false, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, periodType, shortCircuitNormIec);
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();
        List<Double> val = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getIk().abs());
        }

        assertEquals(36.83479069716216, val.get(0), 0.00001);
        assertEquals(36.83269305365278, val.get(1), 0.00001);
        assertEquals(36.831803523930354, val.get(2), 0.00001);

    }
}

