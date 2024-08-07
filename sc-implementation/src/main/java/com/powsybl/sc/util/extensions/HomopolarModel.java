/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.LfBranch;

import java.util.Objects;

/**
 * zero sequence additional attributes
 *
 * Proposed Transformer model :
 *      Ia       Yg    A'  rho                 B'     Yg        Ib        Zga : grounding impedance on A side (in ohms expressed on A side)
 *   A-->--3*Zga--+    +--(())--+--Zoa--+--Zob--+     +--3*ZGb--<--B      Zoa : leakage impedance of A-winding (in ohms expressed on B side)
 *                Y +                   |           + Y                   Zob : leakage impdedance of B-winding (in ohms expressed on B side)
 *                    + D              Zom        + D                     Zom : magnetizing impedance of the two windings (in ohms expressed on B side)
 *                    |                 |         |                       Zgb : grounding impedance on B side (in ohms expressed on B side)
 *                    |                 |         |                       rho might be a complex value
 *                    |    free fluxes \          |
 *                    |                 |         |
 *                  /////             /////     /////                     A' and B' are connected to Yg, Y or D depending on the winding connection type (Y to ground, Y or Delta)
 *
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class HomopolarModel {

    private final LfBranch branch;

    // values here are expressed in pu (Vnom_B, Sbase = 100.)
    private double ro = 0; // ro = roa + rob
    private double xo = 0; // xo = xoa + xob

    private double gom = 0; // Zom = 1 / Yom with Yom = gom + j*bom
    private double bom = 0;

    private double rga = 0;
    private double xga = 0;

    private double rgb = 0;
    private double xgb = 0;

    // if the branch is not a transfo, then it is the correct default behaviour
    private WindingConnectionType leg1ConnectionType = WindingConnectionType.Y_GROUNDED;
    private WindingConnectionType leg2ConnectionType = WindingConnectionType.Y_GROUNDED;

    private boolean freeFluxes = false;

    protected HomopolarModel(LfBranch branch) {
        this.branch = Objects.requireNonNull(branch);
    }

    public double getRo() {
        return ro;
    }

    public double getXo() {
        return xo;
    }

    public double getGom() {
        return gom;
    }

    public double getBom() {
        return bom;
    }

    public double getRga() {
        return rga;
    }

    public double getXga() {
        return xga;
    }

    public double getRgb() {
        return rgb;
    }

    public double getXgb() {
        return xgb;
    }

    public WindingConnectionType getLeg1ConnectionType() {
        return leg1ConnectionType;
    }

    public WindingConnectionType getLeg2ConnectionType() {
        return leg2ConnectionType;
    }

    public boolean isFreeFluxes() {
        return freeFluxes;
    }

    public double getZoInvSquare() {
        return ro != 0 || xo != 0 ? 1 / (ro * ro + xo * xo) : 0;
    }

    public static HomopolarModel build(LfBranch branch) {
        Objects.requireNonNull(branch);

        var piModel = branch.getPiModel();
        double r = piModel.getR();
        double x = piModel.getX();
        double gPi1 = piModel.getG1();
        double bPi1 = piModel.getB1();

        var homopolarExtension = new HomopolarModel(branch);

        // default initialization if no homopolar values available
        homopolarExtension.ro = r / AdmittanceConstants.COEF_XO_XD;
        homopolarExtension.xo = x / AdmittanceConstants.COEF_XO_XD;

        homopolarExtension.gom = gPi1 * AdmittanceConstants.COEF_XO_XD; //TODO : adapt
        homopolarExtension.bom = bPi1 * AdmittanceConstants.COEF_XO_XD;  //TODO : adapt

        if (branch.getBranchType() == LfBranch.BranchType.LINE) {
            // branch is a line and homopolar data available
            ScLine scLine = (ScLine) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            if (scLine != null) {
                double epsilon = 0.0000001;
                double r0 = scLine.getRo();
                double x0 = scLine.getXo();

                // We propose to scale b and g accordingly with x / x0 and r / r0
                // this assumption could be changed if not relevant
                double gCoeff = 1.;
                double bCoeff = 1.;
                if (Math.abs(x0) > epsilon) {
                    bCoeff = x / x0;
                }
                if (Math.abs(r0) > epsilon) {
                    gCoeff = r / r0;
                }

                homopolarExtension.ro = r0;
                homopolarExtension.xo = x0;
                homopolarExtension.gom = gPi1 * gCoeff; // check if acceptable approach
                homopolarExtension.bom = bPi1 * bCoeff;
            }
        } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_2) {
            // branch is a 2 windings transformer and homopolar data available
            ScTransfo2W scTransfo = (ScTransfo2W) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            if (scTransfo != null) {

                double kT = 1.0;
                if (branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM) != null) {
                    kT = (Double) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
                }

                double ro = scTransfo.getRo();
                double xo = scTransfo.getXo();

                double rok = ro * kT;
                double xok = xo * kT;

                // We propose to scale b and g accordingly with x / x0 and r / r0
                // this assumption could be changed if not relevant
                double epsilon = 0.0000001;
                double gCoeff = 1.;
                double bCoeff = 1.;
                if (Math.abs(xo) > epsilon) {
                    bCoeff = x / xo;
                }
                if (Math.abs(ro) > epsilon) {
                    gCoeff = r / ro;
                }

                homopolarExtension.ro = rok + scTransfo.getR1Ground() + scTransfo.getR2Ground(); // we assume by construction that if side is not grounded then rGround = 0
                homopolarExtension.xo = xok + scTransfo.getX1Ground() + scTransfo.getX2Ground();
                homopolarExtension.gom = gPi1 * gCoeff / kT; //TODO : adapt
                homopolarExtension.bom = bPi1 * bCoeff / kT;  //TODO : adapt

                homopolarExtension.leg1ConnectionType = scTransfo.getLeg1ConnectionType();
                homopolarExtension.leg2ConnectionType = scTransfo.getLeg2ConnectionType();
            }
        } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2
                || branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
            // branch is leg1 of a 3 windings transformer and homopolar data available
            ScTransfo3W scTransfo = (ScTransfo3W) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT);
            ScTransfo3wKt scTransfoKt = (ScTransfo3wKt) branch.getProperty(ShortCircuitExtensions.PROPERTY_SHORT_CIRCUIT_NORM);
            if (scTransfoKt != null && scTransfo != null) {
                double ro;
                double xo;
                double kTro;
                double kTxo;
                if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_1) {
                    ro = scTransfo.getLeg1().getRo();
                    xo = scTransfo.getLeg1().getXo();
                    kTro = scTransfoKt.getLeg1().getkTro();
                    kTxo = scTransfoKt.getLeg1().getkTxo();
                    homopolarExtension.leg1ConnectionType = scTransfo.getLeg1().getLegConnectionType();
                    homopolarExtension.freeFluxes = scTransfo.getLeg1().isFreeFluxes();
                } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_2) {
                    ro = scTransfo.getLeg2().getRo();
                    xo = scTransfo.getLeg2().getXo();
                    kTro = scTransfoKt.getLeg2().getkTro();
                    kTxo = scTransfoKt.getLeg2().getkTxo();
                    homopolarExtension.leg1ConnectionType = scTransfo.getLeg2().getLegConnectionType();
                    homopolarExtension.freeFluxes = scTransfo.getLeg2().isFreeFluxes();
                } else if (branch.getBranchType() == LfBranch.BranchType.TRANSFO_3_LEG_3) {
                    ro = scTransfo.getLeg3().getRo();
                    xo = scTransfo.getLeg3().getXo();
                    kTro = scTransfoKt.getLeg3().getkTro();
                    kTxo = scTransfoKt.getLeg3().getkTxo();
                    homopolarExtension.leg1ConnectionType = scTransfo.getLeg3().getLegConnectionType();
                    homopolarExtension.freeFluxes = scTransfo.getLeg3().isFreeFluxes();
                } else {
                    throw new IllegalArgumentException("Branch " + branch.getId() + " has unknown 3-winding leg number");
                }

                // We propose to scale b and g accordingly with x / x0 and r / r0
                // this assumption could be changed if not relevant
                double epsilon = 0.0000001;
                double gCoeff = 1.;
                double bCoeff = 1.;
                if (Math.abs(xo) > epsilon) {
                    bCoeff = x / xo;
                }
                if (Math.abs(ro) > epsilon) {
                    gCoeff = r / ro;
                }

                homopolarExtension.ro = ro * kTro;
                homopolarExtension.xo = xo * kTxo;
                homopolarExtension.gom = gPi1 * gCoeff; // adapt if needed
                homopolarExtension.bom = bPi1 * bCoeff; // adapt if needed
            }
        } else {
            throw new IllegalArgumentException("Branch '" + branch.getId() + "' has a not yet supported type");
        }

        return homopolarExtension;
    }

    public DenseMatrix computeHomopolarAdmittanceMatrix() {
        DenseMatrix mo = new DenseMatrix(4, 4);

        var piModel = branch.getPiModel();
        double rho = piModel.getR1();
        double alpha = piModel.getA1();
        double cosA = Math.cos(Math.toRadians(alpha));
        double sinA = Math.sin(Math.toRadians(alpha));
        double cos2A = Math.cos(Math.toRadians(2 * alpha));
        double sin2A = Math.sin(Math.toRadians(2 * alpha));

        double infiniteImpedanceAdmittance = AdmittanceConstants.INFINITE_IMPEDANCE_ADMITTANCE_VALUE;

        // if the free fluxes option is false, we suppose that if Yom given in input is zero, then Zom = is zero  : TODO : see if there is a more robust way to handle this
        // if the free fluxes option is true, Zom is infinite and Yom is then considered as zero
        double rm = 0.;
        double xm = 0.;
        if (bom != 0. || gom != 0.) {
            rm = gom / (bom * bom + gom * gom);
            xm = -bom / (bom * bom + gom * gom);
        }

        // we suppose that zob = zoa = Zo / 2  : this approximation could be questioned if necessary
        double roa = ro / 2.;
        double xoa = xo / 2.;
        double rob = ro / 2.;
        double xob = xo / 2.;

        // we suppose that all impedance and admittance terms of the homopolar extension are per-unitized on Sbase = 100 MVA and Vnom = Vnom on B side
        if (leg1ConnectionType == WindingConnectionType.Y && leg2ConnectionType == WindingConnectionType.Y
                || leg1ConnectionType == WindingConnectionType.Y && leg2ConnectionType == WindingConnectionType.DELTA
                || leg1ConnectionType == WindingConnectionType.DELTA && leg2ConnectionType == WindingConnectionType.Y
                || leg1ConnectionType == WindingConnectionType.DELTA && leg2ConnectionType == WindingConnectionType.DELTA
                || leg1ConnectionType == WindingConnectionType.Y_GROUNDED && leg2ConnectionType == WindingConnectionType.Y && freeFluxes
                || leg1ConnectionType == WindingConnectionType.Y && leg2ConnectionType == WindingConnectionType.Y_GROUNDED && freeFluxes) {
            // homopolar admittance matrix is zero-Matrix
            mo.set(0, 0, infiniteImpedanceAdmittance);
            mo.set(1, 1, infiniteImpedanceAdmittance);
            mo.set(2, 2, infiniteImpedanceAdmittance);
            mo.set(3, 3, infiniteImpedanceAdmittance);

        } else if (leg1ConnectionType == WindingConnectionType.Y_GROUNDED && leg2ConnectionType == WindingConnectionType.Y) {
            // we suppose that Zoa = Zo given in input for the transformer
            // we suppose that if Yom given in input is zero, then Zom = is zero : if we want to model an open circuit, then set free fluxes to true

            // we have yo11 = 1 / ( 3Zga(pu) + (Zoa(pu)+ Zom(pu))/(rho*e(jAlpha))Â² )
            // and yo12 = yo22 = yo21 = 0.
            // 3Zga(pu) + Zoa(pu)/(rho*e(jAlpha))Â² + Zom/(rho*e(jAlpha))Â² = 3*rg + 1/rhoÂ²*((roa+rom)cos2A-(xoa+xom)sin2A) + j(3*xg + 1/rhoÂ²*((xoa+xom)cos2A+(roa+rom)sin2A) )
            double req = 3 * rga + 1 / (rho * rho) * ((ro + rm) * cos2A - (xo + xm) * sin2A);
            double xeq = 3 * xga + 1 / (rho * rho) * ((xo + xm) * cos2A + (ro + rm) * sin2A);
            double bo11 = -xeq / (xeq * xeq + req * req);
            double go11 = req / (xeq * xeq + req * req);
            mo.set(0, 0, go11);
            mo.set(0, 1, -bo11);
            mo.set(1, 0, bo11);
            mo.set(1, 1, go11);
            mo.set(2, 2, infiniteImpedanceAdmittance);
            mo.set(3, 3, infiniteImpedanceAdmittance);
        } else if (leg1ConnectionType == WindingConnectionType.Y && leg2ConnectionType == WindingConnectionType.Y_GROUNDED) {
            // we suppose that zob = Zo given in input for the transformer
            // we suppose that if Yom given in input is zero, then Zom = is zero : if we want to model an open circuit, then set free fluxes to true

            // we have yo22 = 1 / ( 3Zga(pu) + Zob(pu) + Zom(pu) )
            // and yo12 = yo11 = yo21 = 0.
            // 3Zgb(pu) + Zob(pu) + Zom = 3*rg + rob + rom + j(3*xg + xob + xom )
            double req = 3 * rgb + ro + rm;
            double xeq = 3 * xgb + xo + xm;
            double bo22 = -xeq / (xeq * xeq + req * req);
            double go22 = req / (xeq * xeq + req * req);
            mo.set(0, 0, infiniteImpedanceAdmittance);
            mo.set(1, 1, infiniteImpedanceAdmittance);
            mo.set(2, 2, go22);
            mo.set(2, 3, -bo22);
            mo.set(3, 2, bo22);
            mo.set(3, 3, go22);
        } else if (leg1ConnectionType == WindingConnectionType.Y_GROUNDED && leg2ConnectionType == WindingConnectionType.DELTA) {

            // we suppose that if Yom given in input is zero, then Zom = is zero : if we want to model an open circuit, then set free fluxes to true

            // we have yo11 = 1 / ( 3Zga(pu) + (Zoa(pu) + 1 / (1/Zom + 1/Zob))/(rho*e(jAlpha))Â² )
            // and yo12 = yo22 = yo21 = 0.
            // using Ztmp = Zoa(pu) + 1 / (Yom + 1/Zob)
            // 3Zga(pu) + (Zoa(pu) + 1 / (Yom + 1/Zob))/(rho*e(jAlpha))Â² = 3*rg + 1/rhoÂ²*((rtmp)cos2A-(xtmp)sin2A) + j(3*xg + 1/rhoÂ²*((xtmp)cos2A+(rtmp)sin2A) )
            double bob = -xob / (rob * rob + xob * xob);
            double gob = rob / (rob * rob + xob * xob);
            double bombob = bom + bob;
            double gomgob = gom + gob;
            double rtmp = roa + gomgob / (gomgob * gomgob + bombob * bombob);
            double xtmp = xoa - bombob / (gomgob * gomgob + bombob * bombob);
            double req = 3 * rga + 1 / (rho * rho) * (rtmp * cos2A - xtmp * sin2A);
            double xeq = 3 * xga + 1 / (rho * rho) * (xtmp * cos2A + rtmp * sin2A);
            double bo11 = -xeq / (xeq * xeq + req * req);
            double go11 = req / (xeq * xeq + req * req);

            if (freeFluxes) {
                // we have Zm = infinity : yo11 = 1 / ( 3Zga(pu) + (Zoa(pu) + 1/Zob(pu))/(rho*e(jAlpha))Â² )
                rtmp = roa + rob;
                xtmp = xoa + xob;
                req = 3 * rga + 1 / (rho * rho) * (rtmp * cos2A - xtmp * sin2A);
                xeq = 3 * xga + 1 / (rho * rho) * (xtmp * cos2A + rtmp * sin2A);
                bo11 = -xeq / (xeq * xeq + req * req);
                go11 = req / (xeq * xeq + req * req);
            }

            mo.set(0, 0, go11);
            mo.set(0, 1, -bo11);
            mo.set(1, 0, bo11);
            mo.set(1, 1, go11);
            mo.set(2, 2, infiniteImpedanceAdmittance);
            mo.set(3, 3, infiniteImpedanceAdmittance);

        } else if (leg1ConnectionType == WindingConnectionType.DELTA && leg2ConnectionType == WindingConnectionType.Y_GROUNDED) {

            // we have yo22 = 1 / ( 3Zga(pu) + Zob(pu) + 1/(1/Zom(pu)+1/Zoa(pu)) )
            // and yo12 = yo11 = yo21 = 0.
            //  3Zgb(pu) + Zob(pu) + Zom = 3*rg + rob + rom + j(3*xg + xob + xom )
            double boa = -xoa / (roa * roa + xoa * xoa);
            double goa = roa / (roa * roa + xoa * xoa);
            double bomboa = bom + boa;
            double gomgoa = gom + goa;

            double req = 3 * rgb + rob + gomgoa / (gomgoa * gomgoa + bomboa * bomboa);
            double xeq = 3 * xgb + xob - bomboa / (gomgoa * gomgoa + bomboa * bomboa);

            double bo22 = -xeq / (xeq * xeq + req * req);
            double go22 = req / (xeq * xeq + req * req);

            if (freeFluxes) {
                // we have Zm = infinity : yo22 = 1 / ( 3Zga(pu) + Zob(pu) + Zoa(pu) )
                // and yo12 = yo11 = yo21 = 0.

                req = 3 * rgb + rob + roa;
                xeq = 3 * xgb + xob + xoa;

                bo22 = -xeq / (xeq * xeq + req * req);
                go22 = req / (xeq * xeq + req * req);
            }

            mo.set(0, 0, infiniteImpedanceAdmittance);
            mo.set(1, 1, infiniteImpedanceAdmittance);
            mo.set(2, 2, go22);
            mo.set(2, 3, -bo22);
            mo.set(3, 2, bo22);
            mo.set(3, 3, go22);

        } else if (leg1ConnectionType == WindingConnectionType.Y_GROUNDED && leg2ConnectionType == WindingConnectionType.Y_GROUNDED) {

            double go11;
            double bo11;
            double go12;
            double bo12;
            double go22;
            double bo22;

            if (!freeFluxes) {
                // Case where fluxes are forced, meaning that Zm is not ignored (and could be zero with a direct connection to ground)
                //
                //  k = rho*e(jAlpha))
                //
                // [Ia]                   1                             [ Zom+Zob+3Zgs    -Zom/k        ] [Va]
                // [  ] = ------------------------------------------  * [                               ] [  ]
                // [Ib]   (Zom/kÂ²+3Zga+Zoa/kÂ²)(Zom+Zob+3Zgb)-(Zom/k)Â²   [   -Zom/k   Zom/kÂ²+3Zga+Zoa/kÂ² ] [Vb]
                // [  ]                                                 [                               ] [  ]
                //
                // Zc = Zom/kÂ²+3Zga+Zoa/kÂ²
                // Zd = Zom+Zob+3Zgb
                // Ze = Zom/k
                //
                // we suppose that if Yom given in input is zero, then Zom = is zero : if we want to model an open circuit, then set free fluxes to true

                double rc = 1 / (rho * rho) * (cos2A * (rm + roa) + sin2A * (xm + xoa)) + 3 * rga;
                double xc = 1 / (rho * rho) * (cos2A * (xm + xoa) - sin2A * (rm + roa)) + 3 * xga;
                double rd = rm + rob + 3 * rgb;
                double xd = xm + xob + 3 * xgb;
                double re = (rm * cosA + xm * sinA) / rho;
                double xe = (xm * cosA - rm * sinA) / rho;

                // this gives :
                // [Ia]         1         [ Zd -Ze ] [Va]
                // [  ] = ------------- * [        ] [  ]
                // [Ib]   Zc * Zd - ZeÂ²   [-Ze  Zc ] [Vb]
                // [  ]                   [        ] [  ]
                //
                // We set Z2denom = Zc * Zd - ZeÂ²
                double r2denom = rc * rd - xc * xd - re * re + xe * xe;
                double x2demon = rc * xd + xc * rd - 2 * re * xe;
                double g2demon = r2denom / (r2denom * r2denom + x2demon * x2demon);
                double b2demon = -x2demon / (r2denom * r2denom + x2demon * x2demon);
                go11 = g2demon * rd - b2demon * xd;
                bo11 = b2demon * rd + g2demon * xd;
                go12 = -g2demon * re + b2demon * xe;
                bo12 = -b2demon * re - g2demon * xe;
                go22 = g2demon * rc - b2demon * xc;
                bo22 = b2demon * rc + g2demon * xc;

            } else {
                //
                // Zm = infinity
                //  k = rho*e(jAlpha))
                //
                // [Ia]                   1               [    1    -1/k  ] [Va]
                // [  ] = ---------------------------   * [               ] [  ]
                // [Ib]   3Zga+Zoa/kÂ²+Zob/kÂ²+3Zgb/kÂ²)     [   -1/k   1/kÂ² ] [Vb]
                // [  ]                                   [               ] [  ]
                //
                // Zc = 3Zga+Zoa/kÂ²+Zob/kÂ²+3Zgb/kÂ²)
                // Zd = Zom+Zob+3Zgb
                // Ze = Zom/k

                double rc = 1 / (rho * rho) * (cos2A * (rob + roa + 3 * rgb) + sin2A * (xoa + xob + 3 * xgb)) + 3 * rga;
                double xc = 1 / (rho * rho) * (cos2A * (xob + xoa + 3 * xgb) - sin2A * (rob + roa + 3 * rgb)) + 3 * xga;

                //double re = (rm * cosA + xm * sinA) / rho;
                //double xe = (xm * cosA - rm * sinA) / rho;

                // this gives :
                // [Ia]         1         [ 1   -1/k ] [Va]
                // [  ] = ------------- * [          ] [  ]
                // [Ib]         Zc        [-1/k  1/kÂ²] [Vb]
                // [  ]                   [          ] [  ]
                //
                // We set Z2denom = Zc * Zd - ZeÂ²
                double gcdemon = rc / (rc * rc + xc * xc);
                double bcdemon = -xc / (rc * rc + xc * xc);

                go11 = gcdemon;
                bo11 = bcdemon;
                go12 = -(gcdemon * cosA + bcdemon * sinA) / rho;
                bo12 = -(bcdemon * cosA - gcdemon * sinA) / rho;
                go22 = (gcdemon * cos2A + bcdemon * sin2A) / (rho * rho);
                bo22 = (bcdemon * cos2A - gcdemon * sin2A) / (rho * rho);
            }
            mo.set(0, 0, go11);
            mo.set(0, 1, -bo11);
            mo.set(1, 0, bo11);
            mo.set(1, 1, go11);

            mo.set(2, 2, go22);
            mo.set(2, 3, -bo22);
            mo.set(3, 2, bo22);
            mo.set(3, 3, go22);

            mo.set(0, 2, go12);
            mo.set(0, 3, -bo12);
            mo.set(1, 2, bo12);
            mo.set(1, 3, go12);

            mo.set(2, 0, go12);
            mo.set(2, 1, -bo12);
            mo.set(3, 0, bo12);
            mo.set(3, 1, go12);
        } else {
            throw new IllegalArgumentException("Branch " + branch.getId() + " configuration is not supported yet : " + leg1ConnectionType + " --- " + leg2ConnectionType);
        }

        return mo;
    }
}
