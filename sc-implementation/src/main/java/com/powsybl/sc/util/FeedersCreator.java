/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.*;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.sc.util.extensions.ShortCircuitNormExtensions;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static com.powsybl.sc.extensions.ShortCircuitConstants.ADMITTANCE_EPSILON_PU;
import static com.powsybl.sc.util.ShortCircuitHelper.createInnerIidmZonePredicate;
import static com.powsybl.sc.util.ShortCircuitHelper.isGeneratorSelected;

/**
 * @author Thoi-Nay Vo <thoi-nay.vo at artelys.com>
 */
public final class FeedersCreator {

    private final Network network;
    private final LfNetwork lfNetwork;
    private final List<Complex> initialVoltages;
    private final AdmittanceType admittanceType;
    private final ShortCircuitParameters shortCircuitParameters;
    private final OpenShortCircuitParameters openScParameters;
    private final ShortCircuitNormExtensions normExtensions;
    private final Predicate<Bus> innerZone;

    public FeedersCreator(Network network,
                          LfNetwork lfNetwork,
                          List<Complex> initialVoltages,
                          AdmittanceType admittanceType,
                          ShortCircuitParameters shortCircuitParameters,
                          OpenShortCircuitParameters openScParameters,
                          ShortCircuitNormExtensions normExtensions) {
        this.network = Objects.requireNonNull(network);
        this.lfNetwork = Objects.requireNonNull(lfNetwork);
        this.initialVoltages = Objects.requireNonNull(initialVoltages);
        this.admittanceType = Objects.requireNonNull(admittanceType);
        this.shortCircuitParameters = Objects.requireNonNull(shortCircuitParameters);
        this.openScParameters = Objects.requireNonNull(openScParameters);
        this.normExtensions = Objects.requireNonNull(normExtensions);
        this.innerZone = createInnerIidmZonePredicate(network, openScParameters.getInnerZoneBusIds());
    }

    public FeedersAtNetwork create() {
        FeedersAtNetwork feeders = new FeedersAtNetwork();
        for (LfBus lfBus : lfNetwork.getBuses()) {
            Bus iidmBus = network.getBusView().getBus(lfBus.getId());
            List<Feeder> busFeeders = iidmBus != null ? createBusFeeders(iidmBus, lfBus) : Collections.emptyList();
            feeders.busToFeeders.put(lfBus, new FeedersAtBus(busFeeders, lfBus));
        }
        return feeders;
    }

    private List<Feeder> createBusFeeders(Bus iidmBus, LfBus lfBus) {
        List<Feeder> feederList = new ArrayList<>();
        switch (admittanceType) {
            case ADM_THEVENIN -> {
                if (shortCircuitParameters.isWithShuntCompensators()) {
                    addShuntFeeders(iidmBus, lfBus, feederList);
                }
                if (shortCircuitParameters.isWithLoads()) {
                    addLoadFeeders(iidmBus, lfBus, feederList);
                }
                addGeneratorFeeders(iidmBus, lfBus, feederList);
            }
            case ADM_THEVENIN_HOMOPOLAR -> addGeneratorFeeders(iidmBus, lfBus, feederList); // only generators in homopolar for now
            default -> {
                // no feeders for the other admittance types
            }
        }
        return feederList;
    }

    private static void addShuntFeeders(Bus iidmBus, LfBus lfBus, List<Feeder> feederList) {
        // Handling shunts that physically exist
        for (ShuntCompensator shunt : iidmBus.getShuntCompensators()) {
            Complex yShunt = new Complex(0., InjectionAdmittances.getShuntB(shunt));
            feederList.add(new ShuntFeeder(yShunt, shunt, lfBus));
        }
    }

    private void addLoadFeeders(Bus iidmBus, LfBus lfBus, List<Feeder> feederList) {
        Complex v = initialVoltages.get(lfBus.getNum());
        for (Load load : iidmBus.getLoads()) {
            Complex yLoad = InjectionAdmittances.getLoadAdmittance(load, v);
            if (yLoad.abs() > ADMITTANCE_EPSILON_PU) {
                feederList.add(new LoadFeeder(yLoad, load, lfBus));
            }
        }
    }

    private void addGeneratorFeeders(Bus iidmBus, LfBus lfBus, List<Feeder> feederList) {
        for (Generator generator : iidmBus.getGenerators()) {
            // TODO : check how to verify that the generators are operating
            if (!isGeneratorSelected(iidmBus, generator, openScParameters, innerZone, shortCircuitParameters.getInitialVoltageProfileMode())) {
                continue;
            }
            Complex yGen = InjectionAdmittances.getGeneratorAdmittance(generator, normExtensions, admittanceType, shortCircuitParameters.getStudyType());
            if (!yGen.equals(Complex.ZERO)) {
                feederList.add(new GeneratorFeeder(yGen, generator, lfBus));
            }
        }
    }
}
