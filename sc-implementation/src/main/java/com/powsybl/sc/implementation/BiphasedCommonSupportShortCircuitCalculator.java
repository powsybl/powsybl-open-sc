/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class BiphasedCommonSupportShortCircuitCalculator extends AbstractShortCircuitCalculator {

    protected Complex zo12;
    protected Complex zo22;
    protected Complex zo21;

    protected Complex zd12;
    protected Complex zd22;
    protected Complex zd21;

    protected Complex zif;
    protected Complex zi12;
    protected Complex zi22;
    protected Complex zi21;

    protected Complex v2dInit;

    protected Complex i2o; // current from bus 2
    protected Complex i2d;
    protected Complex i2i;

    protected Complex vo;
    protected Complex vd;
    protected Complex vi;

    protected Complex v2o;
    protected Complex v2d;
    protected Complex v2i;

    protected Complex zt; // values of total impedance used to get Ic

    protected Complex ic; // short circuit phase C1 current circulating from common support 1 to 2

    public BiphasedCommonSupportShortCircuitCalculator(Complex zdf, Complex zof, Complex zg,
                                                       Complex initV, Complex v2dInit,
                                                       Complex zo12, Complex zo22, Complex zo21,
                                                       Complex zd12, Complex zd22, Complex zd21) {
        super(zdf, zof, zg, initV);
        this.zo12 = zo12;
        this.zo22 = zo22;
        this.zo21 = zo21;

        this.zd12 = zd12;
        this.zd22 = zd22;
        this.zd21 = zd21;

        // By default, zif = zdf
        this.zif = zdf;
        this.zi12 = zd12;
        this.zi22 = zd22;
        this.zi21 = zd21;

        this.v2dInit = v2dInit;
    }

    public void computeZt() { }

    public void computeIc() { }

    public void computeCurrents() { }

    public void computeVoltages() {
        // Function using no input args
        // [ Vof ] = -tM * inv(Yo) * M * [ Iof ]
        // [ Vdf ] = -tM * inv(Yd) * M * [ Idf ] + tM * [ V(init) ]
        // [ Vif ] = -tM * inv(Yd) * M * [ Iif ]

        //get the voltage vectors
        // Vo :
        // [v1ox]          [ rof_11  -xof_11  rof_12  -xof_12 ]   [ i1ox ]
        // [v1oy] = -1  *  [ xof_11   rof_11  xof_12   rof_12 ] * [ i1oy ]
        // [v2ox]          [ rof_21  -xof_21  rof_22  -xof_22 ]   [ i2ox ]
        // [v2oy]          [ xof_21   rof_21  xof_22   rof_22 ]   [ i2oy ]
        vo = zof.multiply(io).add(zo12.multiply(i2o)).multiply(-1.);
        v2o = zo21.multiply(io).add(zo22.multiply(i2o)).multiply(-1.);

        // Vd :
        // [v1dx]          [ rdf_11  -xdf_11  rdf_12  -xdf_12 ]   [ i1ox ]     [v1dx(init)]
        // [v1dy] = -1  *  [ xdf_11   rdf_11  xdf_12   rdf_12 ] * [ i1oy ]  +  [v1dy(init)]
        // [v2dx]          [ rdf_21  -xdf_21  rdf_22  -xdf_22 ]   [ i2ox ]     [v2dx(init)]
        // [v2dy]          [ xdf_21   rdf_21  xdf_22   rdf_22 ]   [ i2oy ]     [v2dy(init)]
        vd = initV.subtract(zdf.multiply(id).add(zd12.multiply(i2d)));
        v2d = v2dInit.subtract(zd21.multiply(id).add(zd22.multiply(i2d)));

        // Vi :
        // [v1ix]          [ rdf_11  -xdf_11  rdf_12  -xdf_12 ]   [ i1dx ]
        // [v1iy] = -1  *  [ xdf_11   rdf_11  xdf_12   rdf_12 ] * [ i1dy ]
        // [v2ix]          [ rdf_21  -xdf_21  rdf_22  -xdf_22 ]   [ i2dx ]
        // [v2iy]          [ xdf_21   rdf_21  xdf_22   rdf_22 ]   [ i2dy ]
        vi = zif.multiply(ii).add(zi12.multiply(i2i)).multiply(-1.);
        v2i = zi21.multiply(ii).add(zi22.multiply(i2i)).multiply(-1.);
    }

    public Complex getI2d() {
        return i2d;
    }

    public Complex getI2i() {
        return i2i;
    }

    public Complex getI2o() {
        return i2o;
    }

    public Complex getV2d() {
        return v2d;
    }

    public Complex getV2o() {
        return v2o;
    }

    public Complex getV2i() {
        return v2i;
    }

    public Complex getVd() {
        return vd;
    }

    public Complex getVi() {
        return vi;
    }

    public Complex getVo() {
        return vo;
    }
}
