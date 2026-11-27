/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.LfBus;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ImpedanceLinearResolutionResult {

    private final LfBus bus;
    private final Complex zthEq;
    private final Complex eth; // Thevenin voltage
    private final Complex zthEq20Hz;

    // zknf corresponds to extracted impedance term z(k,nf) from inv(Y) to be able to compute Vk = z(k,nf).icc.
    // k is the bus index of the voltage Vk we want to compute and nf is the index of faulted bus
    private Complex zknf;

    // This is a map to easily access the impedance terms resulting from inv(Y), extracting only useful z(i,j) terms
    // the key stores the number of the bus k at which we want to compute the voltage from formula Vk = z(k,nf).icc, the value stores the resolved value [Res] = inv(Y)*[En],
    // with n of vector [En] corresponding to the studied short circuit fault and values at lines of [Res] corresponding real and imaginary parts at bus in key
    private Map<Integer, Complex> busToZknf;

    private List<TwoBusImpedanceLinearResolutionResult> twoBusResults; // we store here all necessary information for all two-bus faults with first bus equal to LfBus = bus

    ImpedanceLinearResolutionResult(LfBus bus, Complex eth, Complex zth, Complex zthEq20Hz) {
        this.bus = bus;
        this.eth = eth;
        this.zthEq = zth;
        this.zthEq20Hz = zthEq20Hz;
    }

    public LfBus getBus() {
        return bus;
    }

    public Complex getEth() {
        return eth;
    }

    public Complex getZthEq() {
        return zthEq;
    }

    public Complex getZthEq20Hz() {
        return zthEq20Hz;
    }

    public Map<Integer, Complex> getBusToZknf() {
        return busToZknf;
    }

    public Complex getZknf() {
        return zknf;
    }

    public List<TwoBusImpedanceLinearResolutionResult> getTwoBusResults() {
        return twoBusResults;
    }

    public void updateZknf(Complex enBus) {
        this.zknf = enBus;
    }

    public void updateWithVoltagesdelta(AdmittanceMatrix y, DenseMatrix dEn, int numDef) {
        busToZknf = y.getDeltaV(dEn, numDef);
    }

    public void addTwoBusResult(LfBus bus2, Complex initV2, ImpedanceUpperTriangle zT, ImpedanceUpperTriangle zTAt20Hz, int numBus2Fault) {
        // numBus2Fault is store to easily get the extraction vector for the second bus, in order to compute the full voltage export if required
        TwoBusImpedanceLinearResolutionResult twoBusResult = new TwoBusImpedanceLinearResolutionResult(
                bus2, initV2, zT, zTAt20Hz, numBus2Fault);

        if (twoBusResults == null) {
            twoBusResults = new ArrayList<>();
        }
        twoBusResults.add(twoBusResult);
    }
}
