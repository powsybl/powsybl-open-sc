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

    public static Predicate<LfBus> createInnerLfZonePredicate(Network network, Set<String> innerZoneBusIds) {
        if (innerZoneBusIds.isEmpty()) {
            return lfBus -> true;
        }
        Set<String> innerZoneLfBusIds = getLfBusIdsFromIidmBusId(network, innerZoneBusIds);
        return lfBus -> innerZoneLfBusIds.contains(lfBus.getId());
    }

    public static Predicate<Bus> createInnerIidmZonePredicate(Network network, Set<String> innerZoneBusIds) {
        if (innerZoneBusIds.isEmpty()) {
            return iidmBus -> true;
        }
        // inner zone bus ids are bus-breaker view ids, converted to bus view ids to be compared with the IIDM bus ids
        Set<String> innerZoneBusViewIds = getLfBusIdsFromIidmBusId(network, innerZoneBusIds);
        return iidmBus -> innerZoneBusViewIds.contains(iidmBus.getId());
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
        return isGeneratorSelected(innerZone.test(generator.getBus()), network.getGenerator(generator.getOriginalId()),
                parameters, voltageProfileMode);
    }

    public static boolean isGeneratorSelected(Bus iidmBus, Generator generator, OpenShortCircuitParameters parameters,
                                              Predicate<Bus> innerZone, InitialVoltageProfileMode voltageProfileMode) {
        return isGeneratorSelected(innerZone.test(iidmBus), Objects.requireNonNull(generator), parameters, voltageProfileMode);
    }

    private static boolean isGeneratorSelected(boolean inInnerZone, Generator iidmGenerator, OpenShortCircuitParameters parameters,
                                               InitialVoltageProfileMode voltageProfileMode) {
        GeneratorSelectionMode mode = inInnerZone ? parameters.getInnerZoneGeneratorSelectionMode() : parameters.getOuterZoneGeneratorSelectionMode();
        if (mode == GeneratorSelectionMode.ALL) {
            return true;
        }

        if (iidmGenerator == null) {
            return false;
        }
        // Synchronous condensers always contribute to the fault current, whatever their active power
        if (iidmGenerator.isCondenser()) {
            return true;
        }

        double threshold = inInnerZone ? parameters.getInnerZoneGeneratorPowerThreshold() : parameters.getOuterZoneGeneratorPowerThreshold();

        double absGeneratorPMw = voltageProfileMode.equals(InitialVoltageProfileMode.PREVIOUS_VALUE) ?
                Math.abs(iidmGenerator.getTerminal().getP()) : Math.abs(iidmGenerator.getTargetP());
        boolean running = absGeneratorPMw > threshold;
        if (!running) {
            LOGGER.info("Generator {} is not running (|P| = {} MW <= threshold {} MW) and is not taken into account",
                    iidmGenerator.getId(), absGeneratorPMw, threshold);
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
