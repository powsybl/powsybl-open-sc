package com.powsybl.sc.extensions;

public enum GeneratorSelectionMode {

    /**
     * All connected generators of the zone are taken into account,
     */
    ALL,

    /**
     * Only generators of the zone whose target active power is greater than or equal
     * to the zone's running generator threshold are taken into account.
     */
    ABOVE_POWER_THRESHOLD
}
