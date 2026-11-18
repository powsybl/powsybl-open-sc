/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.sc.util.extensions.ScGenerator;
import com.powsybl.sc.util.extensions.ScLoad;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static com.powsybl.openloadflow.util.PerUnit.SB;
import static com.powsybl.sc.extensions.ShortCircuitConstants.ADMITTANCE_EPSILON_PU;
import static com.powsybl.sc.extensions.ShortCircuitConstants.IMPEDANCE_EPSILON_PU;
import static com.powsybl.sc.util.ShortCircuitHelper.createInnerZonePredicate;
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
    private final Predicate<LfBus> innerZone;

    public FeedersCreator(Network network,
                          LfNetwork lfNetwork,
                          List<Complex> initialVoltages,
                          AdmittanceType admittanceType,
                          ShortCircuitParameters shortCircuitParameters,
                          OpenShortCircuitParameters openScParameters) {
        this.network = Objects.requireNonNull(network);
        this.lfNetwork = Objects.requireNonNull(lfNetwork);
        this.initialVoltages = Objects.requireNonNull(initialVoltages);
        this.admittanceType = Objects.requireNonNull(admittanceType);
        this.shortCircuitParameters = Objects.requireNonNull(shortCircuitParameters);
        this.openScParameters = Objects.requireNonNull(openScParameters);
        this.innerZone = createInnerZonePredicate(network, openScParameters.getInnerZoneBusIds());
    }

    public FeedersAtNetwork create() {
        FeedersAtNetwork feeders = new FeedersAtNetwork();
        for (LfBus bus : lfNetwork.getBuses()) {
            feeders.busToFeeders.put(bus, new FeedersAtBus(createBusFeeders(bus), bus));
        }
        return feeders;
    }

    public List<Feeder> createBusFeeders(LfBus bus) {
        List<Feeder> feederList = new ArrayList<>();
        switch (admittanceType) {
            case ADM_THEVENIN -> {
                if (shortCircuitParameters.isWithShuntCompensators()) {
                    addShuntFeeders(bus, feederList);
                }
                if (shortCircuitParameters.isWithLoads()) {
                    addLoadFeeder(bus, feederList);
                }
                addGeneratorFeeders(bus, feederList);
            }
            case ADM_THEVENIN_HOMOPOLAR -> addGeneratorFeeders(bus, feederList); // only generators in homopolar for now
            default -> {
                // no feeders for the other admittance types
            }
        }
        return feederList;
    }

    private static void addShuntFeeders(LfBus bus, List<Feeder> feederList) {
        // Handling shunts that physically exist
        bus.getShunt().ifPresent(shunt -> feederList.add(createShuntFeeder(shunt, bus, Feeder.FeederType.SHUNT)));
        bus.getControllerShunt().ifPresent(shunt -> feederList.add(createShuntFeeder(shunt, bus, Feeder.FeederType.CONTROLLER_SHUNT)));
    }

    private static Feeder createShuntFeeder(LfShunt shunt, LfBus bus, Feeder.FeederType feederType) {
        //check if g will be implemented
        return new ShuntFeeder(new Complex(0., shunt.getB()), shunt.getId(), bus, feederType);
    }

    private void addLoadFeeder(LfBus bus, List<Feeder> feederList) {
        Complex v = initialVoltages.get(bus.getNum());
        ScLoad scLoad = (ScLoad) bus.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
        Complex yLoadEq = scLoad.ydEquivalent().divide(v.abs() * v.abs());
        if (yLoadEq.abs() > ADMITTANCE_EPSILON_PU) {
            feederList.add(new LoadFeeder(yLoadEq, bus)); // Currently only one feeder aggregating all the loads of the bus!
        }
    }

    private void addGeneratorFeeders(LfBus bus, List<Feeder> feederList) {
        double vnomVl = bus.getNominalV();
        for (LfGenerator lfgen : bus.getGenerators()) { //compute R'd or R"d from generators at bus
            // TODO : check how to verify that the generators are operating
            if (!isGeneratorSelected(lfgen, openScParameters, network, innerZone)) {
                continue;
            }
            Complex z = computeGeneratorImpedance(lfgen);
            if (z.abs() > IMPEDANCE_EPSILON_PU) {
                Complex yGen = z.reciprocal().multiply(vnomVl * vnomVl / SB);
                feederList.add(new GeneratorFeeder(yGen, lfgen));
            }
        }
    }

    private Complex computeGeneratorImpedance(LfGenerator lfgen) {
        ScGenerator scGen = (ScGenerator) lfgen.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
        if (admittanceType == AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
            // For now, xo and ro are fixed independently of x'd and x"d:
            // further improvement might be needed if xo and ro are different for transient and subTransient short circuit analysis
            return scGen.isGrounded() ? scGen.getZo() : Complex.ZERO;
        }
        double kG = (Double) lfgen.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
        Complex zd = shortCircuitParameters.getStudyType() == StudyType.SUB_TRANSIENT ? scGen.getSubTransZd() : scGen.getTransZd();
        return zd.add(scGen.getStepUpTfoZ()).multiply(kG);
    }
}
