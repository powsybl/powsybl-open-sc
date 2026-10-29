package com.powsybl.sc.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.sc.extensions.GeneratorSelectionMode;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.sc.implementation.ShortCircuitEngineParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

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
        return openScParameters != null ? openScParameters : new OpenShortCircuitParameters();
    }

    public static boolean isGeneratorSelected(LfGenerator generator, ImpedanceLinearResolutionParameters parameters, Network network,
                                       Predicate<LfBus> innerZone) {
        return isGeneratorSelected(generator, network, innerZone,
                parameters.getInnerZoneGeneratorSelectionMode(), parameters.getOuterZoneGeneratorSelectionMode(),
                parameters.getInnerZoneGeneratorPowerThreshold(), parameters.getOuterZoneGeneratorPowerThreshold());
    }

    public static boolean isGeneratorSelected(LfGenerator generator, ShortCircuitEngineParameters parameters, Network network,
                                       Predicate<LfBus> innerZone) {
        return isGeneratorSelected(generator, network, innerZone,
                parameters.getInnerZoneGeneratorSelectionMode(), parameters.getOuterZoneGeneratorSelectionMode(),
                parameters.getInnerZoneGeneratorPowerThreshold(), parameters.getOuterZoneGeneratorPowerThreshold());
    }

    public static boolean isGeneratorSelected(LfGenerator generator, Network network, Predicate<LfBus> innerZone,
                                              GeneratorSelectionMode innerZoneMode, GeneratorSelectionMode outerZoneMode,
                                              double innerZoneThreshold, double outerZoneThreshold) {
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

        double threshold = inInnerZone ? innerZoneThreshold : outerZoneThreshold;
        double absGeneratorPMw = Math.abs(iidmGenerator.getTerminal().getP());
        boolean running = absGeneratorPMw > threshold;
        if (!running) {
            LOGGER.info("Generator {} is not running (|P| = {} MW <= threshold {} MW) and is not taken into account",
                    generator.getOriginalId(), absGeneratorPMw, threshold);
        }
        return running;
    }
}
