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
public class BiphasedC1B2Calculator extends BiphasedCommonSupportShortCircuitCalculator {

    public BiphasedC1B2Calculator(double rdf, double xdf, double rof, double xof, double rg, double xg,
                                  double initVx, double initVy,
                                  double v2dxInit, double v2dyInit,
                                  double ro12, double xo12, double ro22, double xo22, double ro21, double xo21,
                                  double rd12, double xd12, double rd22, double xd22, double rd21, double xd21) {
        super(rdf, xdf, rof, xof, rg, xg, initVx, initVy, v2dxInit, v2dyInit, ro12, xo12, ro22, xo22, ro21, xo21, rd12, xd12, rd22, xd22, rd21, xd21);

        //Description of the fault (short circuit between c1 and a2) :
        // a1 ---------------x------------------  by definition : Ia1 = Ib1 = Ia2 = Ic2 = 0
        // b1 ---------------x------------------                  Ic1 = -Ib2
        // c1 ---------------+------------------                  Vc1 = Zf * Ic1 + Vb2
        //                   |
        //                  Zf
        //                   |
        // b2 ---------------+------------------
        // c2 ---------------x------------------
        // a2 ---------------x------------------

        //Problem to solve:
        // Given the equalities above, we need to solve for V and I the following problem:
        // [ Vof ] = -tM * inv(Yo) * M * [ Iof ]
        // [ Vdf ] = tM * [ V(init) ] - tM * inv(Yd) * M * [ Idf ]
        // [ Vif ] = -tM * inv(Yd) * M * [ Iif ]
        // Where:
        // - Yo and Yd are the full network admittance matrices (zero and direct fortescue components)
        // - M is the extraction matrix to get the subvectors V1, V2, I1 and I2 of the full network vectors [V] and [I]
        //
        // Step 1: express the currents in fortescue basis :
        //
        // [ I1o ]         [ 1  1  1 ]   [ 0  ]               [ 1 ]
        // [ I1d ] = 1/3 * [ 1  a  a²] * [ 0  ] = 1/3 * Ic1 * [ a²]
        // [ I1i ]         [ 1  a² a ]   [ Ic1]               [ a ]
        //
        // [ I2o ]               [ 1 ]
        // [ I2d ] = -1/3 *Ic1 * [ a ]
        // [ I2i ]               [ a²]
        //
        // Step 2: get Ic1 :
        // Given:  Vc1 = V1o + a * V1d + a² * V1i        and       Vb2 = V2o + a² * V2d + a * V2i
        // and replacing them in:  Vc1 = Zf * Ic1 + Vb2
        // we get
        //                                                   a * V1d(init) - a² * V2d(init)
        // Ic1 = --------------------------------------------------------------------------------------------------------------------
        //        Zf + 1/3*(Zd_11 - a²*Zd_12 + Zd_22 - a*Zd_21 + Zo_11 - Zo_21 + Zo_22 - Zo_12 + Zi_22 - a*Zi_12 + Zi_11 - a²*Zi_21)
        //
        // Where Zof_ij and Zdf_ij are complex impedance matrix elements at nodes 1 and 2:
        // Zof = tM * inv(Yo) * M   and Zdf = tM * inv(Yd) * M
        //
        // Then, for example, we have with complex variables :
        // [ V1of ]          [ I1of ]     [ Zof_11 Zof_12 ]   [ I1of ]
        // [ V2of ] = -Zof * [ I2of ] = - [ Zof_21 Zof_22 ] * [ I2of ]
        //
        // Step 3: compute the short circuit voltages:
        // From computed Ic1 we get complex values : I1o, I1d, I1i, I2o, I2d, I2i using step 1 formulas expressed with Ic1
        // Then compute the voltages from current values

        computeZt(); // computes rt and xt
        computeIc(); // computes Ic of common support from rt and xt
        computeCurrents(); // computes Io, Id, Ii for both supports from computed Ic
        computeVoltages(); // computes Vo, Vd, Vi from computed currents and computed terms of the impedance matrix

    }

    @Override
    public void computeIc() {

        // Compute Ic1 :
        // Complex expression :
        //                                                a * V1d(init) - a² * V2d(init)
        // Ic1 = -------------------------------------------------------------------------------------------------------------------
        //        Zf + 1/3*(Zd_11 - a²*Zd_12 + Zd_22 - a*Zd_21 + Zo_11 - Zo_21 + Zo_22 - Zo_12 + Zi_22 - a*Zi_12 + Zi_11 - a²*Zi_21)
        //
        //
        // The equivalent cartesian matrix expression of Ic :
        //                    1
        // [ic1x] =  ------------------------ * [ rt xt ] *  ( [ -1/2  -sqrt(3)/2 ] *  [vd1x] - [  1/2  -sqrt(3)/2 ] * [vd2x] )
        // [ic1y]         (rt² + xt²)           [-xt rt ]    ( [ sqrt(3)/2  -1/2  ]    [vd1y]   [ sqrt(3)/2   1/2  ]   [vd2y] )
        //

        //compute the numerator matrix = a * V1d(init) - a² * V2d(init)
        DenseMatrix ma = getMatrixByType(AbstractShortCircuitCalculator.BlocType.A, 1.0);

        DenseMatrix mVd2Init = new DenseMatrix(2, 1);
        mVd2Init.add(0, 0, -v2dxInit);
        mVd2Init.add(1, 0, -v2dyInit);
        DenseMatrix maVd2 = ma.times(mVd2Init).toDense();

        maVd2.add(0, 0, initVx);
        maVd2.add(1, 0, initVy);

        DenseMatrix numerator = ma.times(maVd2).toDense();

        // get Ic by multiplying the numerator to inv(Zt)
        DenseMatrix invZt = getInvZt(rt, xt);
        mIc = invZt.times(numerator).toDense();
    }

    @Override
    public void computeZt() {

        // Zf + 1/3*(Zd_11 - a²*Zd_12 + Zd_22 - a*Zd_21 + Zo_11 - Zo_21 + Zo_22 - Zo_12 + Zi_22 - a*Zi_12 + Zi_11 - a²*Zi_21)
        DenseMatrix a2 = getMatrixByType(AbstractShortCircuitCalculator.BlocType.A2, 1.0);
        DenseMatrix a = getMatrixByType(AbstractShortCircuitCalculator.BlocType.A, 1.0);
        DenseMatrix minusId = getMatrixByType(AbstractShortCircuitCalculator.BlocType.I_D, -1.0);
        DenseMatrix idDiv3 = getMatrixByType(AbstractShortCircuitCalculator.BlocType.I_D, 1. / 3.);

        // td12 = - a²*Zd_12
        DenseMatrix tmpd12 = a2.times(zdf12).toDense();
        DenseMatrix td12 = minusId.times(tmpd12).toDense();

        // td21 = - a*Zd_21
        DenseMatrix tmpd21 = a.times(zdf21).toDense();
        DenseMatrix td21 = minusId.times(tmpd21).toDense();

        // to21 = -zof21 and to12 = -zof12
        DenseMatrix to21 = minusId.times(zof21).toDense();
        DenseMatrix to12 = minusId.times(zof12).toDense();

        // ti12 = - a*Zi_12
        DenseMatrix tmpi12 = a.times(zif12).toDense();
        DenseMatrix ti12 = minusId.times(tmpi12).toDense();

        // ti21 = - a²*Zi_21
        DenseMatrix tmpi21 = a2.times(zif21).toDense();
        DenseMatrix ti21 = minusId.times(tmpi21).toDense();

        DenseMatrix zt = addMatrices22(zdf11, td12.toDense());
        zt = addMatrices22(zt, zdf22);
        zt = addMatrices22(zt, td21);
        zt = addMatrices22(zt, zof11);
        zt = addMatrices22(zt, to21);
        zt = addMatrices22(zt, zof22);
        zt = addMatrices22(zt, to12);
        zt = addMatrices22(zt, zif22);
        zt = addMatrices22(zt, ti12);
        zt = addMatrices22(zt, zif11);
        zt = addMatrices22(zt, ti21);

        DenseMatrix tmpzt = idDiv3.times(zt).toDense();

        DenseMatrix zf = getZ(rg, xg);

        zt = addMatrices22(tmpzt, zf);

        rt = zt.get(0, 0);
        xt = zt.get(1, 0);

    }

    @Override
    public void computeCurrents() {
        // step 3:
        // get the currents vectors
        // [ I1o ]               [ 1 ]
        // [ I1d ] = 1/3 * Ic1 * [ a²]
        // [ I1i ]               [ a ]
        //
        // [ I2o ]               [ 1 ]
        // [ I2d ] = -1/3 *Ic1 * [ a ]
        // [ I2i ]               [ a²]

        DenseMatrix mI3 = getMatrixByType(AbstractShortCircuitCalculator.BlocType.I_D, 1. / 3);
        DenseMatrix ma2Div3 = getMatrixByType(AbstractShortCircuitCalculator.BlocType.A2, 1. / 3);
        DenseMatrix maDiv3 = getMatrixByType(AbstractShortCircuitCalculator.BlocType.A, 1. / 3);
        DenseMatrix mMinusI = getMatrixByType(AbstractShortCircuitCalculator.BlocType.I_D, -1.);

        mIo = mI3.times(mIc).toDense();
        mId = ma2Div3.times(mIc).toDense();
        mIi = maDiv3.times(mIc).toDense();

        mI2o = mMinusI.times(mIo).toDense();
        mI2d = maDiv3.times(mIo).toDense();
        mI2i = ma2Div3.times(mIo).toDense();
    }
}
