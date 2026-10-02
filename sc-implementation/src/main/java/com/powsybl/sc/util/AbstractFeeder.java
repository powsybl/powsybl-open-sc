package com.powsybl.sc.util;

import com.powsybl.iidm.network.ThreeSides;
import org.apache.commons.math3.complex.Complex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public abstract class AbstractFeeder implements Feeder {

    //Feeder class is used to post process the results of a short circuit computation to get the feeder contribution in short-circuit current
    protected AbstractFeeder(Complex zFeeder, String id) {
        Objects.requireNonNull(id, "id");
        this.z = zFeeder;
        this.id = id;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFeeder.class);
    private final Complex z;
    protected final String id; // id in LfNetwork

    public Complex getZ() {
        return z;
    }

    public String getId() {
        return id;
    }

    public ThreeSides getSide() {
        return null;
    }

    protected Complex computeCurrent(double vKv, double phiRad, double p, double q) {
        if (Double.isNaN(vKv) || Double.isNaN(phiRad) || Double.isNaN(p) || Double.isNaN(q)) {
            LOGGER.warn("No valid pre-fault state (disconnected or no load flow result) for feeder '{}', " +
                    "defaulting initial current to 0", id);
            return Complex.ZERO;
        }

        Complex v = new Complex(vKv * Math.cos(phiRad), vKv * Math.sin(phiRad));
        Complex s = new Complex(p, q);

        // I = conj(S) / conj(V)
        Complex current = s.conjugate().divide(v.conjugate());

        if (current.isNaN() || current.isInfinite()) {
            LOGGER.warn("Initial current for feeder '{}' is NaN/Infinite, defaulting to 0", id);
            return Complex.ZERO;
        }
        return current;
    }
}
