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
class ShortCircuitBiphasedCommonSupportTest {

    private MatrixFactory matrixFactory;

    private ShortCircuitParameters scp;

    @BeforeEach
    void setUp() {
        matrixFactory = new DenseMatrixFactory();
        scp = new ShortCircuitParameters();
        scp.addExtension(OpenShortCircuitParameters.class,
                new OpenShortCircuitParameters(new LoadFlowParameters().setTwtSplitShuntAdmittance(true)));
    }

    @Test
    void shortCircuitIec31BiphasedCommonSupport() {
        Network network = ReferenceNetwork.create6NodesIec9094();

        List<ShortCircuitFault> faultList = List.of(
                createZeroImpedanceBiphasedCommonSupportFault("sc1", "B2", "B3", ShortCircuitFault.ShortCircuitBiphasedType.C1_A2)
        );

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, scp, new ShortCircuitNormIec());
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();

        List<Double> val = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getDefaultIk().abs());
        }

        assertEquals(28.85869102832315, val.getFirst(), 0.00001); // TODO : check manually result

    }

    @Test
    void shortCircuitIec31MultiBiphasedCommonSupport() {
        Network network = ReferenceNetwork.create6NodesIec9094();

        // TODO : a list that contains BIPHASED_COMMON_SUPPORT with the same nodes is not supported yet : FIX_ME
        List<ShortCircuitFault> faultList = List.of(
                createZeroImpedanceBiphasedCommonSupportFault("sc1", "B2", "B3", ShortCircuitFault.ShortCircuitBiphasedType.C1_B2),
                createZeroImpedanceBiphasedCommonSupportFault("sc2", "B4", "B5", ShortCircuitFault.ShortCircuitBiphasedType.C1_C2)
        );

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, scp, new ShortCircuitNormIec());
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();

        List<Double> val = new ArrayList<>();
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> res : scbEngine.resultsPerFault.entrySet()) {
            val.add(res.getValue().getDefaultIk().abs());
        }

        assertEquals(28.85869102832315, val.get(0), 0.00001); // TODO : check manually result
        assertEquals(0., val.get(1), 0.00001); // TODO : check manually result

    }

    private static ShortCircuitFault createZeroImpedanceBiphasedCommonSupportFault(String id, String busId1, String busId2,
                                                                                   ShortCircuitFault.ShortCircuitBiphasedType biphasedType) {
        return new ShortCircuitFault(busId1, busId2, id, new ShortCircuitFaultImpedance(Complex.ZERO),
                ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT, biphasedType);
    }
}
