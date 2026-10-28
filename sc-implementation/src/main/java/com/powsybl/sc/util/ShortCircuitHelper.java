package com.powsybl.sc.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.sc.extensions.GeneratorSelectionMode;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ShortCircuitHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitHelper.class);

    public void shortCircuitHelper() { }

    public static boolean isLfBusInInnerZone(LfBus lfBus, Network network, Set<String> innerZoneBusIds) {
        if (innerZoneBusIds.isEmpty()) {
            return true;
        } else {
            Set<String> innerZoneLfBusIds = getLfBusIdsFromIidmBusId(network, innerZoneBusIds);
            return innerZoneLfBusIds.contains(lfBus.getId());
        }
    }

    public static Set<String> getLfBusIdsFromIidmBusId(Network network, Set<String> innerZoneBusIds) {
        Set<String> lfBusIds = new HashSet<>();
        for (String busId : innerZoneBusIds) {
            Bus mergedBus = network.getBusBreakerView().getBus(busId)
                    .getVoltageLevel().getBusView().getMergedBus(busId);
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

    static boolean isGeneratorSelected(LfGenerator generator, ImpedanceLinearResolutionParameters parameters, Network network) {
        return isGeneratorSelected(generator, network, parameters.getInnerZoneBusIds(),
                parameters.getInnerZoneGeneratorSelectionMode(), parameters.getOuterZoneGeneratorSelectionMode(),
                parameters.getInnerZoneGeneratorPowerThreshold(), parameters.getOuterZoneGeneratorPowerThreshold());
    }

    public static boolean isGeneratorSelected(LfGenerator generator, Network network, Set<String> innerZoneBusIds,
                                              GeneratorSelectionMode innerZoneMode, GeneratorSelectionMode outerZoneMode,
                                              double innerZoneThreshold, double outerZoneThreshold) {
        LfBus bus = generator.getBus();
        // Note: if the set innerZoneBusIds is empty (by default), it is interpreted as the whole network
        boolean inInnerZone = isLfBusInInnerZone(bus, network, innerZoneBusIds);

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
