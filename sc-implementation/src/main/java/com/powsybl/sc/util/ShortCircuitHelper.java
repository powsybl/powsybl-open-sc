/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.extensions.GeneratorSelectionMode;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.VoltageRange;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author Thoi-Nay Vo <thoi-nay.vo at artelys.com>
 */
public final class ShortCircuitHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitHelper.class);

    private ShortCircuitHelper() {
    }

    public static Predicate<LfBus> createInnerZonePredicate(Network network, Set<String> innerZoneBusIds) {
        if (innerZoneBusIds.isEmpty()) {
            return lfBus -> true;
        }
        Set<String> innerZoneLfBusIds = getLfBusIdsFromIidmBusId(network, innerZoneBusIds);
        return lfBus -> innerZoneLfBusIds.contains(lfBus.getId());
    }

    public static Set<String> getLfBusIdsFromIidmBusId(Network network, Set<String> innerZoneBusIds) {
        Set<String> lfBusIds = new HashSet<>();
        for (String busId : innerZoneBusIds) {
            Bus busBreakerBus = network.getBusBreakerView().getBus(busId);
            if (busBreakerBus == null) {
                // the provider removes unknown buses beforehand, but the engines can be used directly
                LOGGER.warn("Inner zone bus {} not found in the network, it is ignored", busId);
                continue;
            }
            Bus mergedBus = busBreakerBus.getVoltageLevel().getBusView().getMergedBus(busId);
            if (mergedBus != null) {
                lfBusIds.add(mergedBus.getId());
            }
        }
        return lfBusIds;
    }

    public static OpenShortCircuitParameters getOrDefaultOpenScParameters(ShortCircuitParameters scParameters) {
        OpenShortCircuitParameters openScParameters = Objects.requireNonNull(scParameters)
                .getExtension(OpenShortCircuitParameters.class);
        if (openScParameters == null) {
            openScParameters = new OpenShortCircuitParameters();
            scParameters.addExtension(OpenShortCircuitParameters.class, openScParameters);
        }
        return openScParameters;
    }

    public static boolean isGeneratorSelected(LfGenerator generator, OpenShortCircuitParameters parameters, Network network,
                                              Predicate<LfBus> innerZone, InitialVoltageProfileMode voltageProfileMode) {
        GeneratorSelectionMode innerZoneMode = parameters.getInnerZoneGeneratorSelectionMode();
        GeneratorSelectionMode outerZoneMode = parameters.getOuterZoneGeneratorSelectionMode();
        boolean inInnerZone = innerZone.test(generator.getBus());

        GeneratorSelectionMode mode = inInnerZone ? innerZoneMode : outerZoneMode;
        if (mode == GeneratorSelectionMode.ALL) {
            return true;
        }

        // Synchronous condensers always contribute to the fault current, whatever their active power
        Generator iidmGenerator = network.getGenerator(generator.getOriginalId());
        if (iidmGenerator == null) {
            return false;
        }
        if (iidmGenerator.isCondenser()) {
            return true;
        }

        double innerZoneThreshold = parameters.getInnerZoneGeneratorPowerThreshold();
        double outerZoneThreshold = parameters.getOuterZoneGeneratorPowerThreshold();

        double threshold = inInnerZone ? innerZoneThreshold : outerZoneThreshold;

        double absGeneratorPMw = voltageProfileMode.equals(InitialVoltageProfileMode.PREVIOUS_VALUE) ?
                Math.abs(iidmGenerator.getTerminal().getP()) : Math.abs(iidmGenerator.getTargetP());
        boolean running = absGeneratorPMw > threshold;
        if (!running) {
            LOGGER.info("Generator {} is not running (|P| = {} MW <= threshold {} MW) and is not taken into account",
                    generator.getOriginalId(), absGeneratorPMw, threshold);
        }
        return running;
    }

    public static AcLoadFlowParameters getAcLfParametersFromOpenScParameters(OpenShortCircuitParameters openScParameters) {
        OpenLoadFlowParameters loadflowParametersExt = OpenLoadFlowParameters.get(openScParameters.getLoadFlowParameters());
        return OpenLoadFlowParameters.createAcParameters(
                openScParameters.getLoadFlowParameters(),
                loadflowParametersExt,
                openScParameters.getMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(),
                false,
                false);
    }

    public static List<Complex> fillInitialVoltages(LfNetwork lfNetwork, ShortCircuitParameters shortCircuitParameters) {
        List<Complex> initialVs = new ArrayList<>(lfNetwork.getBuses().size());
        for (LfBus lfBus : lfNetwork.getBuses()) {
            initialVs.add(lfBus.getNum(), computeBusInitialVoltage(lfBus, shortCircuitParameters));
        }
        return initialVs;
    }

    private static Complex computeBusInitialVoltage(LfBus bus, ShortCircuitParameters shortCircuitParameters) {
        return switch (shortCircuitParameters.getInitialVoltageProfileMode()) {
            case PREVIOUS_VALUE -> ComplexUtils.polar2Complex(bus.getV(), bus.getAngle());
            case CONFIGURED -> computeVoltageUsingRanges(bus, shortCircuitParameters);
            case NOMINAL -> Complex.ONE;
        };
    }

    private static Complex computeVoltageUsingRanges(LfBus bus, ShortCircuitParameters shortCircuitParameters) {
        Complex v = new Complex(1.); // If bus nominal voltage is outside every range, it takes default value 1 pu

        double nominalV = bus.getNominalV();
        for (VoltageRange range : shortCircuitParameters.getVoltageRanges()) {
            if (bus.getNominalV() >= range.getMinimumNominalVoltage() && bus.getNominalV() <= range.getMaximumNominalVoltage()) {
                double voltage = Double.isNaN(range.getVoltage()) ? 1.0 : range.getVoltage() / nominalV;
                voltage *= Double.isNaN(range.getRangeCoefficient()) ? 1.0 : range.getRangeCoefficient();
                return new Complex(voltage);
            }
        }

        return v;
    }
}
