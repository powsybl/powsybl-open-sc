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
class ShortCircuitBiphasedGroundTest {

    private LoadFlowParameters loadFlowParameters;

    private MatrixFactory matrixFactory;

    @BeforeEach
    void setUp() {
        loadFlowParameters = new LoadFlowParameters().setTwtSplitShuntAdmittance(true);
        matrixFactory = new DenseMatrixFactory();
    }

    @Test
    void shortCircuit6NodesIec9094() {

        Network network = ReferenceNetwork.create6NodesIec9094();

        ShortCircuitFaultImpedance scZ1 = new ShortCircuitFaultImpedance(new Complex(0.));
        ShortCircuitFault sc1 = createBiphasedGroundFault("sc1", "B3", scZ1);

        ShortCircuitFaultImpedance scZ2 = new ShortCircuitFaultImpedance(new Complex(0.), new Complex(0.0001, 0.0002), new Complex(0.0003, 0.0004));
        ShortCircuitFault sc2 = createBiphasedGroundFault("sc2", "B3", scZ2);

        ShortCircuitFaultImpedance scZ3 = new ShortCircuitFaultImpedance(new Complex(0.000007, 0.00005), new Complex(0.0001, 0.0002), new Complex(0.0003, 0.0004));
        ShortCircuitFault sc3 = createBiphasedGroundFault("sc3", "B3", scZ3);

        List<ShortCircuitFault> faultList = List.of(sc1, sc2, sc3);

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

        assertEquals(36.83479069716216, val.get(0), 0.00001);
        assertEquals(36.83269305365278, val.get(1), 0.00001);
        assertEquals(36.831803523930354, val.get(2), 0.00001);

    }

    private static ShortCircuitFault createBiphasedGroundFault(String id, String busId, ShortCircuitFaultImpedance impedance) {
        return new ShortCircuitFault(busId, id, impedance, ShortCircuitFault.ShortCircuitType.BIPHASED_GROUND);
    }
}

