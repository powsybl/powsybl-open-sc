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

import java.util.List;

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
        OpenShortCircuitParameters openScParameters = new OpenShortCircuitParameters().setLoadFlowParameters(new LoadFlowParameters().setTwtSplitShuntAdmittance(true));
        scp.addExtension(OpenShortCircuitParameters.class, openScParameters);
    }

    @Test
    void shortCircuitIec31BiphasedCommonSupport() {

        Network network = ReferenceNetwork.create6NodesIec9094();

        ShortCircuitFault sc1 = createZeroImpedanceBiphasedCommonSupportFault("B2", "B3", "sc1", "B2", ShortCircuitFault.ShortCircuitBiphasedType.C1_A2);
        List<ShortCircuitFault> faultList = List.of(sc1);

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, scp, new ShortCircuitNormIec());
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();

        assertEquals(28.85869102832315, getDefaultIk(scbEngine, sc1), 0.00001); // TODO : check manually result

    }

    @Test
    void shortCircuitIec31MultiBiphasedCommonSupport() {

        Network network = ReferenceNetwork.create6NodesIec9094();
        Complex zFaultToGround = new Complex(0.);
        ShortCircuitFaultImpedance scz = new ShortCircuitFaultImpedance(zFaultToGround);
        ShortCircuitFault sc1 = createZeroImpedanceBiphasedCommonSupportFault("B2", "B3", "sc1", "B2", ShortCircuitFault.ShortCircuitBiphasedType.C1_B2);
        ShortCircuitFault sc2 = createZeroImpedanceBiphasedCommonSupportFault("B4", "B5", "sc2", "B4", ShortCircuitFault.ShortCircuitBiphasedType.C1_C2);
        // TODO : a list that contains BIPHASED_COMMON_SUPPORT with the same nodes is not supported yet : FIX_ME
        List<ShortCircuitFault> faultList = List.of(sc1, sc2);

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory,
                ShortCircuitEngineParameters.AnalysisType.SELECTIVE, faultList, scp, new ShortCircuitNormIec());
        ShortCircuitUnbalancedEngine scbEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);

        scbEngine.run();

        assertEquals(28.85869102832315, getDefaultIk(scbEngine, sc1), 0.00001); // TODO : check manually result
        assertEquals(0., getDefaultIk(scbEngine, sc2), 0.00001); // TODO : check manually result

    }

    private static double getDefaultIk(ShortCircuitUnbalancedEngine scbEngine,
                                 ShortCircuitFault fault) {
        return scbEngine.resultsPerFault.get(fault).getDefaultIk().abs();
    }

    private static ShortCircuitFault createZeroImpedanceBiphasedCommonSupportFault(String busId1, String busId2, String id, String elementId,
                                                                                   ShortCircuitFault.ShortCircuitBiphasedType biphasedType) {
        return new ShortCircuitFault(busId1, busId2, id, elementId, new ShortCircuitFaultImpedance(Complex.ZERO),
                ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT, biphasedType);
    }

}

