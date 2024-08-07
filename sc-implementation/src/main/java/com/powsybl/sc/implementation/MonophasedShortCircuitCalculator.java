/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.math.matrix.DenseMatrix;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class MonophasedShortCircuitCalculator extends AbstractShortCircuitCalculator {

    public MonophasedShortCircuitCalculator(double rdf, double xdf, double rof, double xof, double rg, double xg,
                                            double initVx, double initVy) {
        super(rdf, xdf, rof, xof, rg, xg, initVx, initVy);

    }

    public void computeCurrents() {

        //Description of the fault:
        // a ---------------x------------------  by definition : Ia = Ib = 0
        // b ---------------x------------------
        // c ---------------+------------------                  Vc = Zf * Ic
        //                  |
        //                 Zf
        //                  |
        //                /////

        //Problem to solve:
        // Given the equalities above, we need to solve for V and I the following problem:
        // [ Vof ] = -tM * inv(Yo) * M * [ Iof ]
        // [ Vdf ] = tM * [ V(init) ] - tM * inv(Yd) * M * [ Idf ]
        // [ Vif ] = -tM * inv(Yd) * M * [ Iif ]
        // Where:
        // - Yo and Yd are the full network admittance matrices (zero and direct fortescue components)
        // - M is the extraction matrix to get the subvectors V1, I1 of the full network vectors [V] and [I]
        //
        // Step 1: express the currents in fortescue basis :
        //
        // [ Io ]         [ 1  1  1 ]   [ 0  ]              [ 1 ]
        // [ Id ] = 1/3 * [ 1  a  a²] * [ 0  ] = 1/3 * Ic * [ a²]
        // [ Ii ]         [ 1  a² a ]   [ Ic ]              [ a ]
        //
        // Step 2: get Ic1 :
        // Given:  Vc = a² * Vo + Vd + a * Vi  and replacing it in Vc = Zf * Ic we get :
        //
        //            a * tM * [Vinit]
        // Ic = -----------------------------
        //       1/3 * (Zof + 2 * Zdf) + Zf
        //
        // Where Zof and Zdf are complex impedance matrix elements :
        // Zof = tM * inv(Yo) * M   and Zdf = tM * inv(Yd) * M
        //
        // Step 3: compute the short circuit voltages:
        // From computed Ic1 we get complex values : I1o, I1d, I1i, I2o, I2d, I2i using step 1 formulas expressed with Ic1
        // Then compute the voltages from current values

        // Complex expression of Ic :
        //            a * Vd(init)                 a * Vd(init)
        // Ic = ----------------------------- = -----------------
        //       1/3 * (Zof + 2 * Zdf) + Zf            Zt

        double rt = (2 * rdf + rof) / 3 + rg;
        double xt = (2 * xdf + xof) / 3 + xg;

        // [Zt] = [ rt  -xt ]
        //        [ xt   rt ]
        //
        // Cartesian expression of Ic using matrices :
        //                                        1
        // [icx] = inv([Zt]) * [a] * [vdx] = ------------ * [ rt xt ] * [ -1/2  -sqrt(3)/2 ] * [vdx]
        // [icy]                     [vdy]   (rt² + xt²)    [-xt rt ]   [ sqrt(3)/2  -1/2  ]   [vdy]

        DenseMatrix vdInit = new DenseMatrix(2, 1);
        vdInit.add(0, 0, initVx);
        vdInit.add(1, 0, initVy);

        DenseMatrix ma = getMatrixByType(BlocType.A, 1.0);
        DenseMatrix ma2 = getMatrixByType(BlocType.A2, 1.0);

        DenseMatrix invZt = getInvZt(rt, xt);

        DenseMatrix tmpaVd = ma.times(vdInit).toDense();
        DenseMatrix mIc = invZt.times(tmpaVd).toDense();

        //
        //        -rt*(vdxi + vdyi*sqrt(3)) + xt*(vdxi*sqrt(3)-vdyi)
        //Icx =  ----------------------------------------------------
        //                        2 * (rt² + xt²)

        //double icx = (-rt * (v1dxInit + v1dyInit * Math.sqrt(3)) + xt*(v1dxInit * Math.sqrt(3) - v1dyInit)) / (2 * detZt);

        //
        //         rt*(vdxi*sqrt(3) - vdyi) + xt*(vdxi+sqrt(3)*vdyi)
        //Icy =  ----------------------------------------------------
        //                        2 * (rt² + xt²)

        //double icy = (-rt * (v1dxInit * Math.sqrt(3) - v1dyInit) + xt * (v1dxInit + v1dyInit * Math.sqrt(3))) / (2 * detZt);

        DenseMatrix mIdiv3 = getMatrixByType(BlocType.I_D, 1. / 3.);

        mIo = mIdiv3.times(mIc).toDense();
        mId = ma2.times(mIo).toDense();
        mIi = ma.times(mIo).toDense();
    }
}
