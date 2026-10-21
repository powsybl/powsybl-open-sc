package com.powsybl.sc.util;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
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
            Bus busBreakerBus = network.getBusBreakerView().getBus(busId);
            if (busBreakerBus == null) {
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
}
