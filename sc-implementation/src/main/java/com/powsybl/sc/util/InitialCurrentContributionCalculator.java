package com.powsybl.sc.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import org.apache.commons.math3.complex.Complex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Computes the pre-fault (initial) complex current contribution of a feeder,
 * i.e. the current flowing through the feeder before the fault is applied,
 * derived from the network's current working state (bus V/angle and terminal
 * P/Q, typically populated by a prior load flow run).
 *
 * Note: {@code Feeder.getId()} means different things depending on the feeder
 * type, mirroring how elements are represented in LfNetwork:
 * - BRANCH: id of the IIDM branch / three-winding transformer
 * - GENERATOR: id of the IIDM generator
 * - LOAD: id of the (bus-view) bus the loads are aggregated on
 * - SHUNT: id of the (bus-view) bus the loadflow-modeled fixed shunts are
 *   aggregated on, SUFFIXED with {@value #SHUNT_ID_SUFFIX}
 * - CONTROLLED_SHUNT: id of the (bus-view) bus the loadflow-modeled
 *   voltage-controlling shunts are aggregated on, SUFFIXED with
 *   {@value #CONTROLLER_SHUNT_ID_SUFFIX}
 */
public final class InitialCurrentContributionCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialCurrentContributionCalculator.class);

    // Note: strings used when LfShuntImpl/LfShunt ids are built in
    // https://github.com/powsybl/powsybl-open-loadflow/blob/main/src/main/java/com/powsybl/openloadflow/network/impl/LfShuntImpl.java
    private static final String SHUNT_ID_SUFFIX = "_shunt_compensators";
    private static final String CONTROLLER_SHUNT_ID_SUFFIX = "_controller_shunt_compensators";

    private InitialCurrentContributionCalculator() {
    }

    public static Complex getInitialCurrentContribution(Network network, Feeder feeder) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(feeder);

        return switch (feeder.getFeederType()) {
            case BRANCH -> computeCurrent(getBranchTerminal(network, feeder), feeder.getId());
            case GENERATOR -> computeCurrent(getGeneratorTerminal(network, feeder), feeder.getId());
            case LOAD -> computeAggregatedCurrent(network, feeder.getId(), Bus::getLoadStream,
                    load -> load.getTerminal().getP(), load -> load.getTerminal().getQ());
            case SHUNT -> computeAggregatedCurrent(network, extractBusId(feeder, SHUNT_ID_SUFFIX), Bus::getShuntCompensatorStream,
                    sc -> sc.getTerminal().getP(), sc -> sc.getTerminal().getQ());
            case CONTROLLED_SHUNT -> computeAggregatedCurrent(network, extractBusId(feeder, CONTROLLER_SHUNT_ID_SUFFIX), Bus::getShuntCompensatorStream,
                    sc -> sc.getTerminal().getP(), sc -> sc.getTerminal().getQ());
        };
    }

    /**
     * SHUNT / CONTROLLED_SHUNT feeder ids are the underlying bus id with a
     * fixed suffix appended (mirroring how LfShunt/LfShuntImpl builds its own
     * id from the LfBus it's aggregated on), so the suffix must be stripped
     * before doing an IIDM bus lookup.
     */
    private static String extractBusId(Feeder feeder, String suffix) {
        String id = feeder.getId();
        if (!id.endsWith(suffix)) {
            throw new PowsyblException("Shunt feeder id '" + id + "' does not end with expected suffix '"
                    + suffix + "'; cannot recover underlying bus id");
        }
        return id.substring(0, id.length() - suffix.length());
    }

    private static Terminal getBranchTerminal(Network network, Feeder feeder) {
        String feederId = feeder.getId();
        Identifiable<?> identifiable = network.getIdentifiable(feederId);
        if (identifiable == null) {
            throw new PowsyblException("Branch feeder '" + feederId + "' not found in the network");
        }
        ThreeSides side = feeder.getSide();
        if (side == null) {
            throw new PowsyblException("Branch feeder '" + feederId + "' has no side set");
        }
        if (identifiable instanceof ThreeWindingsTransformer twt) {
            return twt.getTerminal(side);
        } else if (identifiable instanceof Branch<?> branch) {
            return branch.getTerminal(toTwoSides(side, feederId));
        }
        throw new PowsyblException("Feeder '" + feederId + "' is declared as BRANCH but network element is a "
                + identifiable.getClass().getSimpleName());
    }

    private static Terminal getGeneratorTerminal(Network network, Feeder feeder) {
        String feederId = feeder.getId();
        Generator generator = network.getGenerator(feederId);
        if (generator == null) {
            throw new PowsyblException("Generator feeder '" + feederId + "' not found in the network");
        }
        return generator.getTerminal();
    }

    /**
     * Aggregates P/Q over all elements of a given type connected to a bus, for
     * feeder types where LfNetwork models one equivalent element per bus
     * instead of one per IIDM injection (loads, shunts).
     */
    private static <T> Complex computeAggregatedCurrent(Network network, String busId,
                                                        java.util.function.Function<Bus, java.util.stream.Stream<T>> elementsOf,
                                                        java.util.function.ToDoubleFunction<T> pOf,
                                                        java.util.function.ToDoubleFunction<T> qOf) {
        Bus bus = network.getBusView().getBus(busId);
        if (bus == null) {
            LOGGER.warn("Bus '{}' not found, defaulting feeder current to 0", busId);
            return Complex.ZERO;
        }
        double p = elementsOf.apply(bus).mapToDouble(pOf).sum();
        double q = elementsOf.apply(bus).mapToDouble(qOf).sum();
        return computeCurrentFromBus(bus, p, q, busId);
    }

    private static TwoSides toTwoSides(ThreeSides side, String feederId) {
        return switch (side) {
            case ONE -> TwoSides.ONE;
            case TWO -> TwoSides.TWO;
            case THREE -> throw new PowsyblException("Invalid side THREE for two-terminal branch feeder '" + feederId + "'");
        };
    }

    private static Complex computeCurrent(Terminal terminal, String feederId) {
        return computeCurrentFromBus(terminal.getBusView().getBus(), terminal.getP(), terminal.getQ(), feederId);
    }

    private static Complex computeCurrentFromBus(Bus bus, double p, double q, String feederId) {
        if (bus == null || Double.isNaN(bus.getV()) || Double.isNaN(p) || Double.isNaN(q)) {
            LOGGER.warn("No valid pre-fault state (disconnected or no load flow result) for feeder '{}', " +
                    "defaulting initial current to 0", feederId);
            return Complex.ZERO;
        }

        double vKv = bus.getV();
        double angleRad = Math.toRadians(bus.getAngle());
        Complex v = new Complex(vKv * Math.cos(angleRad), vKv * Math.sin(angleRad));
        Complex s = new Complex(p, q);

        // I = conj(S) / conj(V)
        Complex current = s.conjugate().divide(v.conjugate());

        if (current.isNaN() || current.isInfinite()) {
            LOGGER.warn("Initial current for feeder '{}' is NaN/Infinite, defaulting to 0", feederId);
            return Complex.ZERO;
        }
        return current;
    }
}
