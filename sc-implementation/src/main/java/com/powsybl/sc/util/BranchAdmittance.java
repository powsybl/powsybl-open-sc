/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.math.matrix.ComplexMatrix;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.PiModelArray;
import com.powsybl.sc.util.extensions.AdmittanceConstants;
import com.powsybl.sc.util.extensions.HomopolarModel;
import com.powsybl.sc.util.extensions.ScTransfo3wKt;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;
import org.apache.commons.math3.complex.Complex;

public final class BranchAdmittance {

    private final double g12;

    private final double b12;

    private final double g1g12sum;

    private final double b1b12sum;

    private final double g21;

    private final double b21;

    private final double g2g21sum;

    private final double b2b21sum;

    private record KTCoeff(double kTr, double kTx) {

    }

    private BranchAdmittance(double g12, double b12, double g1g12sum, double b1b12sum,
                             double g21, double b21, double g2g21sum, double b2b21sum) {
        this.g12 = g12;
        this.b12 = b12;
        this.g1g12sum = g1g12sum;
        this.b1b12sum = b1b12sum;
        this.g21 = g21;
        this.b21 = b21;
        this.g2g21sum = g2g21sum;
        this.b2b21sum = b2b21sum;
    }

    public static BranchAdmittance create(LfBranch branch, ImpedanceLinearResolutionParameters parameters,
                                          AdmittanceEquationSystem.FrequencyType frequencyType) {
        return create(branch, parameters.getAdmittanceType(), parameters.isWithNeutralPosition(),
                parameters.isWithResistances(), parameters.isWithCapacities(), frequencyType);
    }

    public static BranchAdmittance create(LfBranch branch, AdmittanceEquationSystem.AdmittanceType admittanceType,
                                          boolean isWithNeutralPosition, boolean isWithResistances, boolean isWithCapacities,
                                          AdmittanceEquationSystem.FrequencyType frequencyType) {
        double freqCoef = 1.0;
        if (frequencyType == AdmittanceEquationSystem.FrequencyType.FREQ_20_HZ) {
            freqCoef = 20. / 50.;
        }

        PiModel piModel = branch.getPiModel();
        if (piModel.getX() == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has reactance equal to zero");
        }

        KTCoeff ktCoeff = computeKTCoeff(branch);
        double kTr = ktCoeff.kTr();
        double kTx = ktCoeff.kTx();

        double rho;
        double r;
        double x;
        // Take the rho of the neutral position (model(0)) of tapChanger if isWithNeutralPosition set to True
        if (isWithNeutralPosition
                && piModel instanceof PiModelArray piModelArray) {
            rho = piModelArray.getModel(0).getR1();
            r = piModelArray.getModel(0).getR() * kTr;
            x = piModelArray.getModel(0).getX() * kTx * freqCoef;
        } else {
            rho = piModel.getR1();
            r = piModel.getR() * kTr;
            x = piModel.getX() * kTx * freqCoef;
        }
        if (!isWithResistances) {
            r = 0.0;
        }
        double zk2 = r * r + x * x;
        if (zk2 == 0) {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has Z equal to zero");
        }
        double zInvSquare = 1 / zk2;

        double alpha = piModel.getA1();
        double cosA = Math.cos(alpha);
        double sinA = Math.sin(alpha);

        double gPi1 = isWithCapacities ? piModel.getG1() / kTr : 0.0;
        double bPi1 = isWithCapacities ? piModel.getB1() / kTx : 0.0;
        double gPi2 = isWithCapacities ? piModel.getG2() / kTr : 0.0;
        double bPi2 = isWithCapacities ? piModel.getB2() / kTx : 0.0;

        if (admittanceType != AdmittanceEquationSystem.AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
            return new BranchAdmittance(
                    rho * zInvSquare * (r * cosA + x * sinA),
                    -rho * zInvSquare * (x * cosA + r * sinA),
                    rho * rho * (gPi1 + r * zInvSquare),
                    rho * rho * (bPi1 - x * zInvSquare),
                    rho * zInvSquare * (r * cosA + x * sinA),
                    rho * zInvSquare * (r * sinA - x * cosA),
                    r * zInvSquare + gPi2,
                    -x * zInvSquare + bPi2);
        }

        HomopolarModel homopolarModel = (HomopolarModel) branch.getProperty(ShortCircuitExtensions.PROPERTY_HOMOPOLAR_MODEL);
        if (frequencyType == AdmittanceEquationSystem.FrequencyType.FREQ_20_HZ) {
            homopolarModel = (HomopolarModel) branch.getProperty(ShortCircuitExtensions.PROPERTY_HOMOPOLAR_MODEL_20HZ);
        }
        if (branch.getBranchType() == LfBranch.BranchType.LINE) {
            // case where branch is a line with available homopolar parameters
            double zoInvSquare = homopolarModel.getZoInvSquare();
            double ro = homopolarModel.getZo().getReal();
            double xo = homopolarModel.getZo().getImaginary();
            return new BranchAdmittance(
                    rho * zoInvSquare * (ro * cosA + xo * sinA),
                    -rho * zoInvSquare * (xo * cosA + ro * sinA),
                    rho * rho * (homopolarModel.getYom().getReal() + ro * zoInvSquare),
                    rho * rho * (homopolarModel.getYom().getImaginary() - xo * zoInvSquare),
                    rho * zoInvSquare * (ro * cosA + xo * sinA),
                    rho * zoInvSquare * (ro * sinA - xo * cosA),
                    ro * zoInvSquare + gPi2 * AdmittanceConstants.COEF_XO_XD,
                    -xo * zoInvSquare + bPi2 * AdmittanceConstants.COEF_XO_XD);
        } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_2
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
            // case where branch is part of a transformer
            ComplexMatrix mo = homopolarModel.getHomopolarAdmittanceMatrix();
            return new BranchAdmittance(
                    -mo.get(0, 1).getReal(),
                    -mo.get(0, 1).getImaginary(),
                    mo.get(0, 0).getReal(),
                    mo.get(0, 0).getImaginary(),
                    -mo.get(1, 0).getReal(),
                    -mo.get(1, 0).getImaginary(),
                    mo.get(1, 1).getReal(),
                    mo.get(1, 1).getImaginary());
        } else {
            throw new IllegalArgumentException("branch type not yet handled");
        }
    }

    private static KTCoeff computeKTCoeff(LfBranch branch) {
        double kTr = 1.;
        double kTx = 1.;
        if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_2) {
            // branch is a 2 windings transformer
            if (branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM) != null) {
                kTx = (Double) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
                kTr = kTx;
            }
        } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
            // branch is a leg of a 3 windings transformer and homopolar data available
            ScTransfo3wKt scTransfoKt = (ScTransfo3wKt) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
            if (scTransfoKt != null) {
                if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1) {
                    kTr = scTransfoKt.getLeg1().getkTr();
                    kTx = scTransfoKt.getLeg1().getkTx();
                } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2) {
                    kTr = scTransfoKt.getLeg2().getkTr();
                    kTx = scTransfoKt.getLeg2().getkTx();
                } else {
                    kTr = scTransfoKt.getLeg3().getkTr();
                    kTx = scTransfoKt.getLeg3().getkTx();
                }
            }
        }

        return new KTCoeff(kTr, kTx);
    }

    /**
     * @return the 2x2 complex admittance matrix such that [I1, I2] = [[y11, y12], [y21, y22]] [V1, V2]
     */
    public ComplexMatrix toComplexMatrix() {
        ComplexMatrix admittance = new ComplexMatrix(2, 2);
        admittance.set(0, 0, new Complex(g1g12sum, b1b12sum));
        admittance.set(0, 1, new Complex(-g12, -b12));
        admittance.set(1, 0, new Complex(-g21, -b21));
        admittance.set(1, 1, new Complex(g2g21sum, b2b21sum));
        return admittance;
    }

    public double getG12() {
        return g12;
    }

    public double getB12() {
        return b12;
    }

    public double getG1g12sum() {
        return g1g12sum;
    }

    public double getB1b12sum() {
        return b1b12sum;
    }

    public double getG21() {
        return g21;
    }

    public double getB21() {
        return b21;
    }

    public double getG2g21sum() {
        return g2g21sum;
    }

    public double getB2b21sum() {
        return b2b21sum;
    }
}
