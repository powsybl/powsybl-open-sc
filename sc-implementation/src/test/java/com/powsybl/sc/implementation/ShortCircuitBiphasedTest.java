/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
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
class ShortCircuitBiphasedTest {

    private LoadFlowParameters loadFlowParameters;

    private MatrixFactory matrixFactory;

    @BeforeEach
    void setUp() {
        loadFlowParameters = new LoadFlowParameters()
                .setTwtSplitShuntAdmittance(true);
        matrixFactory = new DenseMatrixFactory();
    }

    @Test
    void shortCircuit6NodesIec9094() {

        Network network = ReferenceNetwork.create6NodesIec9094();

        ShortCircuitFault sc1 = new ShortCircuitFault("B3", "sc1", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.BIPHASED);

        ShortCircuitFaultImpedance scZ2 = new ShortCircuitFaultImpedance(new Complex(0.), new Complex(0.0001, 0.0002), new Complex(0.0003, 0.0004));
        ShortCircuitFault sc2 = new ShortCircuitFault("B3", "sc1", scZ2, ShortCircuitFault.ShortCircuitType.BIPHASED);

        ShortCircuitFaultImpedance scZ3 = new ShortCircuitFaultImpedance(new Complex(0.), new Complex(0.0001, 0.0002), new Complex(0., 0.));
        ShortCircuitFault sc3 = new ShortCircuitFault("B3", "sc1", scZ3, ShortCircuitFault.ShortCircuitType.BIPHASED);

        ShortCircuitFaultImpedance scZ4 = new ShortCircuitFaultImpedance(new Complex(0.0003, 0.0004), new Complex(0.0001, 0.0002), new Complex(0., 0.));
        ShortCircuitFault sc4 = new ShortCircuitFault("B3", "sc1", scZ4, ShortCircuitFault.ShortCircuitType.BIPHASED);

        List<ShortCircuitFault> faultList = List.of(sc1, sc2, sc3, sc4);

        ShortCircuitEngineParameters.PeriodType periodType = ShortCircuitEngineParameters.PeriodType.SUB_TRANSIENT;
        ShortCircuitNormIec shortCircuitNormIec = new ShortCircuitNormIec();
        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(loadFlowParameters, matrixFactory, ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, false, ShortCircuitEngineParameters.VoltageProfileType.NOMINAL, false, false, false, periodType, shortCircuitNormIec, true);
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();
        List<Double> val = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getIk().abs());
        }

        assertEquals(29.985254650180778, val.get(0), 0.00001);
        assertEquals(29.98450280547588, val.get(1), 0.00001);
        assertEquals(29.98450280547588, val.get(2), 0.00001);
        assertEquals(29.98450280547588, val.get(3), 0.00001);

    }

}
