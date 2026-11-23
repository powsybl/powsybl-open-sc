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
import com.powsybl.sc.extensions.FrequencyType;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.sc.util.extensions.AdmittanceConstants;
import com.powsybl.sc.util.extensions.HomopolarModel;
import com.powsybl.sc.util.extensions.ScTransfo3wKt;
import com.powsybl.sc.util.extensions.ShortCircuitExtensions;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class BranchAdmittance {

    private final Complex y12;

    private final Complex y1y12sum;

    private final Complex y21;

    private final Complex y2y21sum;

    private record KTCoeff(double kTr, double kTx) {

    }

    private BranchAdmittance(Complex y12, Complex y1y12sum, Complex y21, Complex y2y21sum) {
        this.y12 = y12;
        this.y1y12sum = y1y12sum;
        this.y21 = y21;
        this.y2y21sum = y2y21sum;
    }

    public static BranchAdmittance create(LfBranch branch, ShortCircuitParameters parameters,
                                          OpenShortCircuitParameters openScParameters,
                                          AdmittanceType admittanceType,
                                          FrequencyType frequencyType) {
        return create(branch, admittanceType, parameters.isWithNeutralPosition(),
                openScParameters.isWithResistances(), openScParameters.isWithCapacities(), frequencyType);
    }

    public static BranchAdmittance create(LfBranch branch, AdmittanceType admittanceType,
                                          boolean isWithNeutralPosition, boolean isWithResistances, boolean isWithCapacities,
                                          FrequencyType frequencyType) {
        double freqCoef = 1.0;
        if (frequencyType == FrequencyType.FREQ_20_HZ) {
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

        double gPi1 = 0.0;
        double bPi1 = 0.0;
        double gPi2 = 0.0;
        double bPi2 = 0.0;
        if (isWithCapacities) {
            gPi1 = piModel.getG1() / kTr;
            bPi1 = piModel.getB1() / kTx;
            gPi2 = piModel.getG2() / kTr;
            bPi2 = piModel.getB2() / kTx;
        }

        if (admittanceType != AdmittanceType.ADM_THEVENIN_HOMOPOLAR) {
            double seriesConductance = rho * zInvSquare * (r * cosA + x * sinA);
            return new BranchAdmittance(
                    new Complex(seriesConductance, -rho * zInvSquare * (x * cosA + r * sinA)),
                    new Complex(rho * rho * (gPi1 + r * zInvSquare), rho * rho * (bPi1 - x * zInvSquare)),
                    new Complex(seriesConductance, rho * zInvSquare * (r * sinA - x * cosA)),
                    new Complex(r * zInvSquare + gPi2, -x * zInvSquare + bPi2));
        }

        return createHomopolar(branch, frequencyType, rho, cosA, sinA, gPi2, bPi2);
    }

    private static BranchAdmittance createHomopolar(LfBranch branch, FrequencyType frequencyType,
                                                    double rho, double cosA, double sinA, double gPi2, double bPi2) {

        HomopolarModel homopolarModel = (HomopolarModel) branch.getProperty(ShortCircuitExtensions.PROPERTY_HOMOPOLAR_MODEL);
        if (frequencyType == FrequencyType.FREQ_20_HZ) {
            homopolarModel = (HomopolarModel) branch.getProperty(ShortCircuitExtensions.PROPERTY_HOMOPOLAR_MODEL_20HZ);
        }
        return switch (branch.getBranchType()) {
            case LINE -> {
                // case where branch is a line with available homopolar parameters
                double zoInvSquare = homopolarModel.getZoInvSquare();
                double ro = homopolarModel.getZo().getReal();
                double xo = homopolarModel.getZo().getImaginary();
                double seriesConductance = rho * zoInvSquare * (ro * cosA + xo * sinA);
                yield new BranchAdmittance(
                        new Complex(seriesConductance, -rho * zoInvSquare * (xo * cosA + ro * sinA)),
                        new Complex(rho * rho * (homopolarModel.getYom().getReal() + ro * zoInvSquare),
                                rho * rho * (homopolarModel.getYom().getImaginary() - xo * zoInvSquare)),
                        new Complex(seriesConductance, rho * zoInvSquare * (ro * sinA - xo * cosA)),
                        new Complex(ro * zoInvSquare + gPi2 * AdmittanceConstants.COEF_XO_XD,
                                -xo * zoInvSquare + bPi2 * AdmittanceConstants.COEF_XO_XD));
            }
            case TRANSFO_2, TRANSFO_3_LEG_1, TRANSFO_3_LEG_2, TRANSFO_3_LEG_3 -> {
                // case where branch is part of a transformer
                ComplexMatrix mo = homopolarModel.getHomopolarAdmittanceMatrix();
                yield new BranchAdmittance(
                        mo.get(0, 1).multiply(-1),
                        mo.get(0, 0),
                        mo.get(1, 0).multiply(-1),
                        mo.get(1, 1));
            }
            default -> throw new IllegalArgumentException("branch type not yet handled");
        };
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

    public ComplexMatrix toComplexMatrix() {
        ComplexMatrix admittance = new ComplexMatrix(2, 2);
        admittance.set(0, 0, y1y12sum);
        admittance.set(0, 1, y12.multiply(-1));
        admittance.set(1, 0, y21.multiply(-1));
        admittance.set(1, 1, y2y21sum);
        return admittance;
    }

    public Complex getY12() {
        return y12;
    }

    public Complex getY1y12sum() {
        return y1y12sum;
    }

    public Complex getY21() {
        return y21;
    }

    public Complex getY2y21sum() {
        return y2y21sum;
    }
}
