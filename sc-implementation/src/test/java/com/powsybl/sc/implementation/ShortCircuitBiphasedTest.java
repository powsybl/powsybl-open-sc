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
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.sc.util.ReferenceNetwork;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
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

        ShortCircuitFault sc1 = createBiphasedFault("sc1", "B3", new ShortCircuitFaultImpedance(Complex.ZERO));

        ShortCircuitFaultImpedance scZ2 = new ShortCircuitFaultImpedance(new Complex(0.), new Complex(0.0001, 0.0002), new Complex(0.0003, 0.0004));
        ShortCircuitFault sc2 = createBiphasedFault("sc2", "B3", scZ2);

        ShortCircuitFaultImpedance scZ3 = new ShortCircuitFaultImpedance(new Complex(0.), new Complex(0.0001, 0.0002), new Complex(0., 0.));
        ShortCircuitFault sc3 = createBiphasedFault("sc3", "B3", scZ3);

        ShortCircuitFaultImpedance scZ4 = new ShortCircuitFaultImpedance(new Complex(0.0003, 0.0004), new Complex(0.0001, 0.0002), new Complex(0., 0.));
        ShortCircuitFault sc4 = createBiphasedFault("sc4", "B3", scZ4);

        List<ShortCircuitFault> faultList = List.of(sc1, sc2, sc3, sc4);

        ShortCircuitParameters scp = new ShortCircuitParameters()
                .setStudyType(StudyType.SUB_TRANSIENT)
                .setWithNeutralPosition(true);
        scp.addExtension(OpenShortCircuitParameters.class, new OpenShortCircuitParameters(loadFlowParameters));

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, scp, new ShortCircuitNormIec());
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

    private static ShortCircuitFault createBiphasedFault(String id, String busId, ShortCircuitFaultImpedance impedance) {
        return new ShortCircuitFault(busId, id, impedance, ShortCircuitFault.ShortCircuitType.BIPHASED);
    }

}
