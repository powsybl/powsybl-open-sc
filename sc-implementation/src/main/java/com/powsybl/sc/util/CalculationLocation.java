/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import org.apache.commons.math3.util.Pair;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class CalculationLocation {

    public enum LocationType {
        BUS,
        LINE,
        BIPHASED_COMMON_SUPPORT
    }

    private final LocationType locationType;

    private final String busLocation;

    private final String bus2Location; // meaningful for LINE and BIPHASED_COMMON_SUPPORT only

    private final String branchLocation; // meaningful for LINE only

    private final double proportionalLocationOnLine; // meaningful for LINE only

    private Pair<String, Integer> iidmBusInfo; // additional iidm info to make the correspondence between iidm info and lfNetwork info

    private Pair<String, Integer> iidmBus2Info; // meaningful for LINE and BIPHASED_COMMON_SUPPORT only

    private String lfBusInfo; // additional info to have the correspondence between iidm and lfNetwork

    private String lfBus2Info; // meaningful for LINE and BIPHASED_COMMON_SUPPORT only

    public CalculationLocation(String busLocation, String bus2Location, String branchLocation,
                                double proportionalLocationOnLine, LocationType locationType) {
        this.busLocation = Objects.requireNonNull(busLocation);
        this.bus2Location = bus2Location;
        this.branchLocation = branchLocation;
        this.proportionalLocationOnLine = proportionalLocationOnLine;
        this.locationType = locationType;
        validate();
    }

    private void validate() {
        if (locationType == LocationType.LINE) {
            if (branchLocation == null) {
                throw new IllegalArgumentException("branch id of a location of type LINE must be defined");
            }
            if (proportionalLocationOnLine < 0.0 || proportionalLocationOnLine > 100.0) {
                throw new IllegalArgumentException("percentageFromBus1 must be between 0 and 100 inclusive");
            }
        } else if (branchLocation != null) {
            throw new IllegalArgumentException("branchLocation must be null for locationType " + locationType);
        }

        if (locationType != LocationType.LINE && locationType != LocationType.BIPHASED_COMMON_SUPPORT
                && bus2Location != null) {
            throw new IllegalArgumentException("bus2Location must be null for locationType " + locationType);
        }
    }

    /**
     * Single bus fault.
     */
    public CalculationLocation(String busLocation) {
        this(busLocation, null, null, 0.0, LocationType.BUS);
    }

    /**
     * Biphased common support fault, tying together two independent buses
     * (not necessarily on the same branch, unlike {@link #CalculationLocation(String, String, String, double)}).
     */
    public CalculationLocation(String busLocation, String bus2Location) {
        this(busLocation, Objects.requireNonNull(bus2Location), null, 0.0, LocationType.BIPHASED_COMMON_SUPPORT);
    }

    /**
     * Fault located along a line, between its two terminal buses.
     */
    public CalculationLocation(String busLocation, String bus2Location, String branchLocation, double proportionalLocationOnLine) {
        this(busLocation, Objects.requireNonNull(bus2Location), Objects.requireNonNull(branchLocation),
                proportionalLocationOnLine, LocationType.LINE);
    }

    public LocationType getLocationType() {
        return locationType;
    }

    public String getBusLocation() {
        return busLocation;
    }

    public String getBus2Location() {
        return bus2Location;
    }

    public String getBranchLocation() {
        return branchLocation;
    }

    public double getProportionalLocationOnLine() {
        return proportionalLocationOnLine;
    }

    public Pair<String, Integer> getIidmBusInfo() {
        return iidmBusInfo;
    }

    public void setIidmBusInfo(Pair<String, Integer> iidmBusInfo) {
        this.iidmBusInfo = iidmBusInfo;
    }

    public Pair<String, Integer> getIidmBus2Info() {
        return iidmBus2Info;
    }

    public void setIidmBus2Info(Pair<String, Integer> iidmBus2Info) {
        this.iidmBus2Info = iidmBus2Info;
    }

    public String getLfBusInfo() {
        return lfBusInfo;
    }

    public void setLfBusInfo(String lfBusInfo) {
        this.lfBusInfo = lfBusInfo;
    }

    public String getLfBus2Info() {
        return lfBus2Info;
    }

    public void setLfBus2Info(String lfBus2Info) {
        this.lfBus2Info = lfBus2Info;
    }
}
