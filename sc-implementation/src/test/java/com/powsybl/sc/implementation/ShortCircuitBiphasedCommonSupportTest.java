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
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
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
class ShortCircuitBiphasedCommonSupportTest {

    private LoadFlowParameters loadFlowParameters;

    private MatrixFactory matrixFactory;

    @BeforeEach
    void setUp() {
        loadFlowParameters = new LoadFlowParameters().setTwtSplitShuntAdmittance(true);
        matrixFactory = new DenseMatrixFactory();
    }

    @Test
    void shortCircuitIec31BiphasedCommonSupport() {

        Network network = ReferenceNetwork.create6NodesIec9094();

        List<ShortCircuitFault> faultList = new ArrayList<>();
        ShortCircuitFault sc1 = new ShortCircuitFault("B2", "B3", "sc1", new ShortCircuitFaultImpedance(new Complex(0.)), ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT, ShortCircuitFault.ShortCircuitBiphasedType.C1_A2);
        faultList.add(sc1);

        ShortCircuitParameters scp = new ShortCircuitParameters()
                .setWithVoltageResult(true)
                .setInitialVoltageProfileMode(InitialVoltageProfileMode.NOMINAL)
                .setWithShuntCompensators(true)
                .setWithLoads(true)
                .setStudyType(StudyType.SUB_TRANSIENT)
                .setWithNeutralPosition(true)
                .setMinVoltageDropProportionalThreshold(0.0);
        scp.addExtension(OpenShortCircuitParameters.class, new OpenShortCircuitParameters(loadFlowParameters)
                .setWithCapacities(true)
                .setWithResistances(true));

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

        Complex zFaultToGround = new Complex(0.);
        ShortCircuitFaultImpedance scz = new ShortCircuitFaultImpedance(zFaultToGround);
        ShortCircuitFault sc1 = new ShortCircuitFault("B2", "B3", "sc1", scz, ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT, ShortCircuitFault.ShortCircuitBiphasedType.C1_B2);
        ShortCircuitFault sc2 = new ShortCircuitFault("B4", "B5", "sc2", scz, ShortCircuitFault.ShortCircuitType.BIPHASED_COMMON_SUPPORT, ShortCircuitFault.ShortCircuitBiphasedType.C1_C2);
        // TODO : a list that contains BIPHASED_COMMON_SUPPORT with the same nodes is not supported yet : FIX_ME
        List<ShortCircuitFault> faultList = List.of(sc1, sc2);

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
            val.add(res.getValue().getDefaultIk().abs());
        }

        assertEquals(28.85869102832315, val.get(0), 0.00001); // TODO : check manually result
        assertEquals(0., val.get(1), 0.00001); // TODO : check manually result

    }

}

