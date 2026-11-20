/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.sc.extensions.*;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class ReferenceNetwork {

    private ReferenceNetwork() {
    }

    public static Network createShortCircuitReference() {
        Network network = Network.create("ShortCircuitReference", "reference");

        // This is an 8 bus grid used as a reference in many paper to get the equivalent Thevenin impedance for a balanced short circuit
        // Sbase = 15 MVA
        double sbase = 15.;
        //
        // Bus1 Vnom = 115 kV
        double bus1Vnom = 115.;
        //
        // Utility at bus 1: Sutility=1500 MVA, X/R = 15, Xpu = Sbase/Sutility which gives Zpu = 0.0007+j0.01
        // Z=VnomBus1Â²/Sbase * Zpu = 0.617167 + j8.81667 ohms which gives yeq= 0.007901-j0.112868 and then Peq = 104.488 MW and Qeq = 1492.69 MVAR
        double xdUtility = 0.01 * bus1Vnom * bus1Vnom / sbase; //8.81667;
        double rdUtility = 0.0007 * bus1Vnom * bus1Vnom / sbase; //0.617167;
        //
        // Bus2 Vnom = 13.8 kV
        double bus2Vnom = 13.8;
        //
        // Transformer T1 (bus1 - bus2) : ST1 = 15 MVA and X/R = 20 and XT1% = 7%
        // |Zpu| = XT1%/100 * (Sbase/ST1) which gives : Zpu = 0.0035 + j0.0699 which gives Z = Zpu * Zbase2 = 0.044436+j0.88745 ohms
        double xT1 = 0.0699 * bus2Vnom * bus2Vnom / sbase; //0.88745;
        double rT1 = 0.0035 * bus2Vnom * bus2Vnom / sbase; //0.044436;
        //
        // Motor M1 cable (bus2 - bus3) : Zpu = 0.0008 + j0.0003 which and Z = 0.00977+j0.003809 ohms
        double xl23 = 0.0003 * bus2Vnom * bus2Vnom / sbase; //0.003809;
        double rl23 = 0.0008 * bus2Vnom * bus2Vnom / sbase; //0.00977;
        //
        // Motor M1 at node bus3: synchronous machine, SM1=4000 HP, Xd'%=15% and X/R=28.9
        // Xd'pu = Xd'%/100*Sbase/(SM1*0.8) then Zpu = 0.0251+j0.703 then Z = 0.31867+j8.92529
        double xM1 = 0.703 * bus2Vnom * bus2Vnom / sbase; //8.92529;
        double rM1 = 0.0251 * bus2Vnom * bus2Vnom / sbase; //0.31867;
        //
        // Cable bus2 - bus 4: Z = 0.0182+j0.01077 then Zpu = 0.001451+j0.000848
        double xl24 = 0.000848 * bus2Vnom * bus2Vnom / sbase; //0.01077;
        double rl24 = 0.001451 * bus2Vnom * bus2Vnom / sbase; //0.0182;
        //
        // bus5 Vnom=2.4 kV
        double bus5Vnom = 2.4;
        //
        // Transformer T2 (bus4 - bus5): ST2 = 3.75 MVA , X/R = 11 and XT2% = 5.5%
        // |Zpu| = XT2%/100 * (Sbase/ST2) which gives : Zpu = 0.0199+j0.2191 then Z = Zpu * Zbase5 = 0.007642+j0.08134
        double xT2 = 0.2191 * bus5Vnom * bus5Vnom / sbase; //0.08134;
        double rT2 = 0.0199 * bus5Vnom * bus5Vnom / sbase; //0.007642;
        //
        // Motor M2 at node bus5: induction machine, SM1=500 HP, Xd'%=16.7% and X/R=19.3
        // Xd'pu = Xd'%/100*Sbase/(SM2*0.95) then Zpu = 0.3331+j6.3284 then Z = 0.12791+j2.43011
        double xM2 = 6.3284 * bus5Vnom * bus5Vnom / sbase; //2.43011;
        double rM2 = 0.3331 * bus5Vnom * bus5Vnom / sbase; //0.12791;
        //
        // Motor M3 at node bus5: induction machine, SM1=2000 HP, Xd'%=16.7% and X/R=30
        // Xd'pu = Xd'%/100*Sbase/(SM3*0.9) then Zpu = 0.0449+j1.3917 then Z = 0.017242+j0.534413
        double xM3 = 1.3917 * bus5Vnom * bus5Vnom / sbase; //0.534413;
        double rM3 = 0.0449 * bus5Vnom * bus5Vnom / sbase; //0.017242;
        //
        // Cable bus2 - bus6: Zpu = 0.0049+j0.0007 and Z = 0.06228+j0.00944
        double xl26 = 0.0007 * bus2Vnom * bus2Vnom / sbase; //0.00944;
        double rl26 = 0.0049 * bus2Vnom * bus2Vnom / sbase; //0.06228;
        //
        // bus7 Vnom=0.277 kV
        double bus7Vnom = 0.277;
        //
        // Transformer T3 (bus6 - bus7): ST3 = 1.5 MVA, X/R = 6.5 and XT3% = 5.75%
        // |Zpu| = XT3%/100 * (Sbase/ST3) which gives : Zpu = 0.0874+j0.5683 then Z = Zpu * Zbase7 = 0.000447+j0.002907
        double xT3 = 0.5683 * bus7Vnom * bus7Vnom / sbase; //0.002907;
        double rT3 = 0.0874 * bus7Vnom * bus7Vnom / sbase; //0.000447;
        //
        // Motor M4 at bus7: Zpu = 0.446924+j*2.3148 and Z = 0.002286+j0.011841
        double xM4SubTrans = 2.3148 * bus7Vnom * bus7Vnom / sbase; //0.011841;
        double rM4SubTrans = 0.446924 * bus7Vnom * bus7Vnom / sbase; //0.002286;
        double xM4Trans = 9.3985 * bus7Vnom * bus7Vnom / sbase;
        double rM4Trans = 1.3614 * bus7Vnom * bus7Vnom / sbase;

        //
        // Cable bus7 - bus8: Zpu = 0.8691+j0.6966 and Z = 0.01335+j0.0107
        double xl78 = 0.6966 * bus7Vnom * bus7Vnom / sbase; //0.0107;
        double rl78 = 0.8691 * bus7Vnom * bus7Vnom / sbase; //0.01335;

        Substation frSubstation = network.newSubstation().setId("S1")
                .setCountry(Country.FR).add();
        VoltageLevel vl1 = addNewVoltageLevel(frSubstation, "VL_1", bus1Vnom);
        Bus bus1 = addNewBus(vl1, "B1", bus1Vnom, 0);

        VoltageLevel vl2 = addNewVoltageLevel(frSubstation, "VL_2", bus2Vnom);
        Bus bus2 = addNewBus(vl2, "B2", bus2Vnom, 0);

        VoltageLevel vl3 = addNewVoltageLevel(frSubstation, "VL_3", bus2Vnom);
        Bus bus3 = addNewBus(vl3, "B3", bus2Vnom, 0);

        VoltageLevel vl4 = addNewVoltageLevel(frSubstation, "VL_4", bus2Vnom);
        Bus bus4 = addNewBus(vl4, "B4", bus2Vnom, 0);

        VoltageLevel vl5 = addNewVoltageLevel(frSubstation, "VL_5", bus5Vnom);
        Bus bus5 = addNewBus(vl5, "B5", bus5Vnom, 0);

        VoltageLevel vl6 = addNewVoltageLevel(frSubstation, "VL_6", bus2Vnom);
        Bus bus6 = addNewBus(vl6, "B6", bus2Vnom, 0);

        VoltageLevel vl7 = addNewVoltageLevel(frSubstation, "VL_7", bus7Vnom);
        Bus bus7 = addNewBus(vl7, "B7", bus7Vnom, 0);

        VoltageLevel vl8 = addNewVoltageLevel(frSubstation, "VL_8", bus7Vnom);
        Bus bus8 = addNewBus(vl8, "B8", bus7Vnom, 0);

        Generator utility = addNewGenerator(vl1, bus1, "Utility", 0.0, 100, 0, 0, bus1Vnom, true);

        utility.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xdUtility)
                .withDirectTransX(xdUtility) //put in per unit Sbase = 100. to be compatible with the per unit of the rest of the grid
                .withStepUpTransformerX(0.)
                .add();

        Generator m1 = addNewGenerator(vl3, bus3, "M1", -100.0, 0, 0, 0, bus2Vnom, false);

        m1.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM1)
                .withDirectTransX(xM1 * 1.5)
                .withStepUpTransformerX(0.)
                .add();

        Generator m2 = addNewGenerator(vl5, bus5, "M2", -100.0, 0, 0, 0, bus5Vnom, false);

        m2.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM2)
                .withDirectTransX(xM2 / 0.4)
                .withStepUpTransformerX(0.)
                .add();

        Generator m3 = addNewGenerator(vl5, bus5, "M3", -100.0, 0, 0, 0, bus7Vnom, false);

        m3.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM3)
                .withDirectTransX(xM3 * 1.5)
                .withStepUpTransformerX(0.)
                .add();

        Generator m4 = addNewGenerator(vl7, bus7, "M4", -100.0, 0, 0, 0, bus7Vnom, false);

        m4.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xM4SubTrans)
                .withDirectTransX(xM4Trans)
                .withStepUpTransformerX(0.)
                .add();

        addNewLine(network, "B2_B3", vl2, vl3, bus2, bus3,
                rl23, xl23);

        addNewLine(network, "B2_B4", vl2, vl4, bus2, bus4,
                rl24, xl24);

        addNewLine(network, "B2_B6", vl2, vl6, bus2, bus6,
                rl26, xl26);

        addNewLine(network, "B7_B8", vl7, vl8, bus7, bus8,
                rl78, xl78);

        frSubstation.newTwoWindingsTransformer()
                .setId("T1")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setRatedU2(bus2Vnom)
                .setR(rT1)
                .setX(xT1)
                .setG(0.0D)
                .setB(0.0D).add();
        frSubstation.newTwoWindingsTransformer()
                .setId("T2")
                .setVoltageLevel1(vl4.getId())
                .setBus1(bus4.getId())
                .setConnectableBus1(bus4.getId())
                .setRatedU1(bus2Vnom)
                .setVoltageLevel2(vl5.getId())
                .setBus2(bus5.getId())
                .setConnectableBus2(bus5.getId())
                .setRatedU2(bus5Vnom)
                .setR(rT2)
                .setX(xT2)
                .setG(0.0D)
                .setB(0.0D)
                .add();
        frSubstation.newTwoWindingsTransformer()
                .setId("T3")
                .setVoltageLevel1(vl6.getId())
                .setBus1(bus6.getId())
                .setConnectableBus1(bus6.getId())
                .setRatedU1(bus2Vnom)
                .setVoltageLevel2(vl7.getId())
                .setBus2(bus7.getId())
                .setConnectableBus2(bus7.getId())
                .setRatedU2(bus7Vnom)
                .setR(rT3)
                .setX(xT3)
                .setG(0.0D)
                .setB(0.0D)
                .add();

        //map to store the real part of the transient impedance
        utility.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rdUtility)
                .withSubTransRd(rdUtility)
                .add();
        m1.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM1 * 1.5)
                .withSubTransRd(rM1)
                .add();
        m2.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM2 / 0.4)
                .withSubTransRd(rM2)
                .add();
        m3.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM3 * 1.5)
                .withSubTransRd(rM3)
                .add();
        m4.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rM4Trans)
                .withSubTransRd(rM4SubTrans)
                .add();

        return network;
    }

    public static Network create6NodesIec9094() {
        Network network = Network.create("ShortCircuit_IEC_3.1", "IEC_3.1");
        // This is a reference grid composed of 6 nodes with 20 kV and 400V nominal voltages

        double bus1Vnom = 20.; // 20 kV
        double bus2Vnom = 0.4; // 400 V

        double ratedV2 = 0.41; // 410 V

        double kT1 = 0.975;
        double xT1 = 0.010312 * kT1;
        double rT1 = 0.002753 * kT1;
        double coeffRoT1 = 1.;
        double coeffXoT1 = 0.95;

        double kT2 = 0.975;
        double xT2 = 0.016100 * kT2;
        double rT2 = 0.004833 * kT2;
        double coeffRoT2 = 1.;
        double coeffXoT2 = 0.95;

        double xL2 = 0.000136;
        double rL2 = 0.000416;
        double coeffRoL2 = 4.23;
        double coeffXoL2 = 1.21;

        double xL1 = 0.000395;
        double rL1 = 0.000385;
        double coeffRoL1 = 3.7;
        double coeffXoL1 = 1.81;

        double xL3 = 0.001740;
        double rL3 = 0.005420;
        double coeffRoL3 = 3.;
        double coeffXoL3 = 4.46;

        double xL4 = 0.01485;
        double rL4 = 0.01850;
        double coeffRoL4 = 2.;
        double coeffXoL4 = 3.;

        // we model the network feeder through a generator of type FEEDER (no IEC correction factor kG)
        // Given the formula: |Zfeeder| = c.Un/(sqrt(3).Ik".rho²) at Vnom = 0.41 kV
        // Then Xfeeder = |Zfeeder| * 0.995 and Rfeeder = 0.1 * Xfeeder
        // Impedance to be applied at feeder : Zfeeder = 0.126115 +j1.26353 ohms at 20 kV (or Zfeeder = 0.053 +j0.531 mohms at 0.41kV as given in the example with rho)
        double rFeeder = 0.126115;
        double xFeeder = 1.26353;

        Substation frSubstation = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = addNewVoltageLevel(frSubstation, "VL_1", bus1Vnom);
        Bus bus1 = addNewBus(vl1, "B1", bus1Vnom, 0);

        VoltageLevel vl2 = addNewVoltageLevel(frSubstation, "VL_2", bus2Vnom);
        Bus bus2 = addNewBus(vl2, "B2", bus2Vnom, 0);

        VoltageLevel vl3 = addNewVoltageLevel(frSubstation, "VL_3", bus2Vnom);
        Bus bus3 = addNewBus(vl3, "B3", bus2Vnom, 0);

        VoltageLevel vl4 = addNewVoltageLevel(frSubstation, "VL_4", bus2Vnom);
        Bus bus4 = addNewBus(vl4, "B4", bus2Vnom, 0);

        VoltageLevel vl5 = addNewVoltageLevel(frSubstation, "VL_5", bus2Vnom);
        Bus bus5 = addNewBus(vl5, "B5", bus2Vnom, 0);

        VoltageLevel vl6 = addNewVoltageLevel(frSubstation, "VL_6", bus2Vnom);
        Bus bus6 = addNewBus(vl6, "B6", bus2Vnom, 0);

        Generator feeder = vl1.newGenerator()
                .setId("FEEDER")
                .setBus(bus1.getId())
                .setConnectableBus(bus1.getId())
                .setMinP(0.0)
                .setMaxP(100.)
                .setTargetP(0.)
                .setLocalTargetQ(0.)
                .setLocalTargetV(vl1.getNominalV())
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE)
                    .withRegulating(false)
                    .add()
                .add();
        feeder.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xFeeder)
                .withDirectTransX(xFeeder)
                .withStepUpTransformerX(0.)
                .add();
        feeder.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rFeeder)
                .withSubTransRd(rFeeder)
                .add();
        feeder.newExtension(GeneratorFortescueAdder.class)
                .withGrounded(false)
                .add();
        feeder.newExtension(GeneratorFortescueTypeAdder.class)
                .withGeneratorType(GeneratorFortescueType.GeneratorType.FEEDER)
                .add();

        var t1 = frSubstation.newTwoWindingsTransformer()
                .setId("T1")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setRatedU2(ratedV2)
                .setR(rT1)
                .setX(xT1)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(630.)
                .add();
        var t2 = frSubstation.newTwoWindingsTransformer()
                .setId("T2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setRatedU2(ratedV2)
                .setR(rT2)
                .setX(xT2)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(400.)
                .add();

        Line l2 = addNewLine(network, "L2_B2_B4", vl2, vl4, bus2, bus4,
                rL2, xL2);

        Line l1 = addNewLine(network, "L1_B3_B4", vl3, vl4, bus3, bus4,
                rL1, xL1);

        Line l3 = addNewLine(network, "L3_B4_B5", vl4, vl5, bus4, bus5,
                rL3, xL3);

        Line l4 = addNewLine(network, "L4_B5_B6", vl5, vl6, bus5, bus6,
                rL4, xL4);

        //additional data

        l1.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL1 * rL1)
                .withXz(coeffXoL1 * xL1)
                .add();
        l2.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL2 * rL2)
                .withXz(coeffXoL2 * xL2)
                .add();
        l3.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL3 * rL3)
                .withXz(coeffXoL3 * xL3)
                .add();
        l4.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL4 * rL4)
                .withXz(coeffXoL4 * xL4)
                .add();

        t1.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(coeffRoT1 * rT1)
                .withXz(coeffXoT1 * xT1)
                .withConnectionType1(WindingConnectionType.DELTA)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .add();
        t2.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(coeffRoT2 * rT2)
                .withXz(coeffXoT2 * xT2)
                .withConnectionType1(WindingConnectionType.DELTA)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .add();

        return network;
    }

    public static Network create8NodesIEC9094() {
        //This example grid represents the high-voltage testnetwork 380kV / 110kV / 30 kV / 10 kV with 8 bus bars
        Network network = Network.create("ShortCircuit_IEC_3.1", "IEC_3.1");

        double vEhv = 380.; // Vnom at bus 1 is 380 kV
        double vHv = 110.; // high voltage = 110 kV
        double vMv = 30.; // medium voltage is 30 kV
        double vLv = 10.; // low voltage is 10 kV

        double xG1 = 26.336676;
        double rG1 = 0.498795;
        double coeffRoG1 = 0.439059 / rG1; // grounded = true
        double xo1ground = 66.;
        double coeffXoG1 = (13.340874 + xo1ground) / xG1;

        double xG2 = 35.340713;
        double rG2 = 1.203944;

        double rG3 = 0.01779;
        double xG3 = 1.089623;

        double xM1 = 3.414968;
        double rM1 = 0.341497;

        double xM2 = 4.121368;
        double rM2 = 0.412137;

        double xL1 = 7.8;
        double rL1 = 2.4;
        double coeffRoL1 = 6.4 / rL1;
        double coeffXoL1 = 25.2 / xL1;

        double xL2 = 3.9;
        double rL2 = 1.2;
        double coeffRoL2 = 3.2 / rL2;
        double coeffXoL2 = 12.6 / xL2;

        double xL3 = 0.975; // TODO : check if this 2 times or only once
        double rL3 = 0.3;
        double coeffRoL3 = 1.3 / rL3;
        double coeffXoL3 = 4.65 / xL3;

        double xL4 = 3.88;
        double rL4 = 0.96;
        double coeffRoL4 = 2.2 / rL4;
        double coeffXoL4 = 11. / xL4;

        double xL5 = 5.79;
        double rL5 = 1.8;
        double coeffRoL5 = 3.3 / rL5;
        double coeffXoL5 = 16.5 / xL5;

        double xL6 = 0.086;
        double rL6 = 0.082;
        double coeffRoL6 = 1.0; // not used
        double coeffXoL6 = 1.0; // not used

        double rho52 = 115 * 115 / (10.5 * 10.5); // Unlike presented in the doc, rT5 and xT5 are expressed on the HV side, we need to divide by rho5Â²
        double rT5 = 2.046454 / rho52;
        double xT5 = 49.072241 / rho52;

        double rT6 = rT5;
        double xT6 = xT5;

        double rhoB2 = 1. / (120. * 120.);
        double rT3a = 0.045714 * rhoB2;
        double xT3a = 8.0969989 * rhoB2;
        double rT3b = 0.053563 * rhoB2;
        double xT3b = -0.079062 * rhoB2;
        double rT3c = 0.408560 * rhoB2;
        double xT3c = 20.292035 * rhoB2;

        double coeffRoT4 = 0.107281 / (rT3b + rT3c) * rhoB2;
        double coeffXoT4 = 18.195035 / (xT3b + xT3c) * rhoB2;

        Substation frSubstation = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = addNewVoltageLevel(frSubstation, "VL_1", vEhv);
        Bus bus1 = addNewBus(vl1, "B1", vEhv, 0);

        VoltageLevel vl2 = addNewVoltageLevel(frSubstation, "VL_2", vHv);
        Bus bus2 = addNewBus(vl2, "B2", vHv, 0);

        VoltageLevel vl8 = addNewVoltageLevel(frSubstation, "VL_8", vMv);
        Bus bus8 = addNewBus(vl8, "B8", vMv, 0);

        VoltageLevel vl9 = addNewVoltageLevel(frSubstation, "VL_9", vMv);
        Bus bus9 = addNewBus(vl9, "B9", vMv, 0);

        VoltageLevel vl3 = addNewVoltageLevel(frSubstation, "VL_3", vHv);
        Bus bus3 = addNewBus(vl3, "B3", vHv, 0);

        VoltageLevel vl4 = addNewVoltageLevel(frSubstation, "VL_4", vHv);
        Bus bus4 = addNewBus(vl4, "B4", vHv, 0);

        VoltageLevel vl5 = addNewVoltageLevel(frSubstation, "VL_5", vHv);
        Bus bus5 = addNewBus(vl5, "B5", vHv, 0);

        VoltageLevel vl6 = addNewVoltageLevel(frSubstation, "VL_6", vLv);
        Bus bus6 = addNewBus(vl6, "B6", vLv, 0);

        VoltageLevel vl7 = addNewVoltageLevel(frSubstation, "VL_7", vLv);
        Bus bus7 = addNewBus(vl7, "B7", vLv, 0);

        // Feeders :
        // Same as previous IEC example : we model the feeder through a load: TODO : check if relevant for all IEC examples
        double pEquivalentFeeder1 = 2262.42;  //Impedance to be applied at feeder : Zfeeder = 0.631933 +j6.319335 ohms
        double qEquivalentFeeder1 = 22624.3; //using formula P(MW) = Re(Z) * |V|Â² / |Z|Â² and Q(MVA) = Im(Z) * |V|Â² / |Z|Â²

        double pEquivalentFeeder2 = 275.753;
        double qEquivalentFeeder2 = 2757.53;
        double coeffF2Ro = 6.6;
        double coeffF2Xo = 3.3;

        vl1.newLoad()
                .setId("LOAD_FEEDER1") // we try to model the feeder through a load that will be transformed into an impedance
                .setBus(bus1.getId())
                .setP0(pEquivalentFeeder1)
                .setQ0(qEquivalentFeeder1)
                .add();

        // This feeder is changed into a machine because it is not yet possible to model a grounded feeder with a load
        /* vl5.newLoad()
                .setId("LOAD_FEEDER2") // we try to model the feeder through a load that will be transformed into an impedance
                .setBus(bus5.getId())
                .setP0(pEquivalentFeeder2)
                .setQ0(qEquivalentFeeder2)
                .add();*/

        // TODO : add table to store coeffs for homopolar values
        double xFeeder2 = 4.344543;
        double rFeeder2 = 0.434454;
        Generator q2 = addNewScGenerator(vl5, bus5, "Q2", 0.0, 100, vHv, xFeeder2, rFeeder2);

        // Generating units :
        Generator g1 = addNewScGenerator(vl4, bus4, "G1", 0.0, 100, vHv, xG1, rG1);
        g1.setRatedS(150);
        addNewScGenerator(vl3, bus3, "G2", 0.0, 100, vHv, xG2, rG2);
        addNewScGenerator(vl6, bus6, "G3", 0.0, 100, vLv, xG3, rG3);

        // Machines :
        // They are considered as non-grounded generating units
        addNewScGenerator(vl7, bus7, "M1", -100.0, 0, vLv, xM1, rM1);
        addNewScGenerator(vl7, bus7, "M2", -100.0, 0, vLv, xM2, rM2);

        addNewFortescueLine(network, "L1_B2_B3", vl2, vl3, bus2, bus3, rL1, xL1, coeffRoL1, coeffXoL1);
        addNewFortescueLine(network, "L2_B3_B4", vl3, vl4, bus3, bus4, rL2, xL2, coeffRoL2, coeffXoL2);
        addNewFortescueLine(network, "L3_B2_B5", vl2, vl5, bus2, bus5, rL3, xL3, coeffRoL3, coeffXoL3);
        addNewFortescueLine(network, "L4_B5_B3", vl5, vl3, bus5, bus3, rL4, xL4, coeffRoL4, coeffXoL4);
        addNewFortescueLine(network, "L5_B5_B4", vl5, vl4, bus5, bus4, rL5, xL5, coeffRoL5, coeffXoL5);
        addNewFortescueLine(network, "L6_B6_B7", vl6, vl7, bus6, bus7, rL6, xL6, coeffRoL6, coeffXoL6);

        addNewTwoWindingsTransformer(frSubstation, "T5", vl5, bus5, 115., vl6, bus6, 10.5,
                rT5, xT5, 31.5, rT5, xT5, WindingConnectionType.Y, WindingConnectionType.Y);
        addNewTwoWindingsTransformer(frSubstation, "T6", vl5, bus5, 115., vl6, bus6, 10.5,
                rT6, xT6, 31.5, rT6, xT6, WindingConnectionType.Y, WindingConnectionType.Y_GROUNDED);

        ThreeWindingsTransformerAdder t3Adder = frSubstation.newThreeWindingsTransformer().setId("T3").setRatedU0(1.0D);
        addNewLeg(t3Adder.newLeg1(), rT3a, xT3a, 400.0D, 100.0D, vl1, bus1);
        addNewLeg(t3Adder.newLeg2(), rT3b, xT3b, 120.0D, 100.0D, vl2, bus2);
        addNewLeg(t3Adder.newLeg3(), rT3c, xT3c, 30., 100.0D, vl8, bus8);
        ThreeWindingsTransformer twt3 = t3Adder.add();

        // T4 legs have no rated power
        ThreeWindingsTransformerAdder t4Adder = frSubstation.newThreeWindingsTransformer().setId("T4").setRatedU0(1.0D);
        addNewLeg(t4Adder.newLeg1(), rT3a, xT3a, 400.0D, Double.NaN, vl1, bus1);
        addNewLeg(t4Adder.newLeg2(), rT3b, xT3b, 120.0D, Double.NaN, vl2, bus2);
        addNewLeg(t4Adder.newLeg3(), rT3c, xT3c, 30., Double.NaN, vl9, bus9);
        ThreeWindingsTransformer twt4 = t4Adder.add();

        //additional data
        // Machines :
        g1.newExtension(GeneratorFortescueAdder.class)
                .withGrounded(true)
                .withXz(xG1 * coeffXoG1)
                .withRz(rG1 * coeffRoG1)
                .add();
        // extension used to get the default generator type
        g1.newExtension(GeneratorFortescueTypeAdder.class)
                .add();

        q2.newExtension(GeneratorFortescueAdder.class)
                .withGrounded(true)
                .withRz(rFeeder2 * coeffF2Ro)
                .withXz(xFeeder2 * coeffF2Xo)
                .add();
        // extension used to get the default generator type
        q2.newExtension(GeneratorFortescueTypeAdder.class)
                .add();

        // transformers :
        twt3.newExtension(ThreeWindingsTransformerFortescueAdder.class)
                .leg1().withConnectionType(WindingConnectionType.Y_GROUNDED)
                .leg1().withXz(twt3.getLeg1().getX())
                .leg1().withRz(twt3.getLeg1().getR())
                .leg2().withConnectionType(WindingConnectionType.Y)
                .leg2().withXz(twt3.getLeg2().getX())
                .leg2().withRz(twt3.getLeg2().getR())
                .leg3().withConnectionType(WindingConnectionType.DELTA)
                .leg3().withXz(twt3.getLeg3().getX())
                .leg3().withRz(twt3.getLeg3().getR())
                .add();

        twt4.newExtension(ThreeWindingsTransformerFortescueAdder.class)
                .leg1().withConnectionType(WindingConnectionType.Y)
                .leg1().withXz(twt4.getLeg1().getX())
                .leg1().withRz(twt4.getLeg1().getR())
                .leg1().withFreeFluxes(true)
                .leg2().withConnectionType(WindingConnectionType.Y_GROUNDED)
                .leg2().withXz(twt4.getLeg2().getX() * coeffXoT4)
                .leg2().withRz(twt4.getLeg2().getR() * coeffRoT4)
                .leg2().withFreeFluxes(true)
                .leg3().withConnectionType(WindingConnectionType.DELTA)
                .leg3().withXz(twt4.getLeg3().getX() * coeffXoT4)
                .leg3().withRz(twt4.getLeg3().getR() * coeffRoT4)
                .leg3().withFreeFluxes(true)
                .add();

        return network;
    }

    public static Network create6NodeIec9094Plus() {
        // This is the same grid as the 6 nodes but with additional nodes created
        // to split lines L2 (Bus 2 and Bus 4) and L4 (Bus 5 and Bus 6) into 2 pieces
        // to show feasability of short circuits in the middle of a line
        Network network = Network.create("ShortCircuit_IEC_3.1", "IEC_3.1");

        double bus1Vnom = 20.; // 20 kV
        double bus2Vnom = 0.4; // 400 V

        double ratedV2 = 0.41; // 410 V

        double kT1 = 0.975;
        double xT1 = 0.010312 * kT1;
        double rT1 = 0.002753 * kT1;
        double coeffRoT1 = 1.;
        double coeffXoT1 = 0.95;

        double kT2 = 0.975;
        double xT2 = 0.016100 * kT2;
        double rT2 = 0.004833 * kT2;
        double coeffRoT2 = 1.;
        double coeffXoT2 = 0.95;

        double xL2 = 0.000136;
        double rL2 = 0.000416;
        double coeffRoL2 = 4.23;
        double coeffXoL2 = 1.21;

        double xL1 = 0.000395;
        double rL1 = 0.000385;
        double coeffRoL1 = 3.7;
        double coeffXoL1 = 1.81;

        double xL3 = 0.001740;
        double rL3 = 0.005420;
        double coeffRoL3 = 3.;
        double coeffXoL3 = 4.46;

        double xL4 = 0.01485;
        double rL4 = 0.01850;
        double coeffRoL4 = 2.;
        double coeffXoL4 = 3.;

        // we model the network feeder through a generator of type FEEDER (no IEC correction factor kG)
        // Given the formula: |Zfeeder| = c.Un/(sqrt(3).Ik".rho²) at Vnom = 0.41 kV
        // Then Xfeeder = |Zfeeder| * 0.995 and Rfeeder = 0.1 * Xfeeder
        // Impedance to be applied at feeder : Zfeeder = 0.126115 +j1.26353 ohms at 20 kV (or Zfeeder = 0.053 +j0.531 mohms at 0.41kV as given in the example with rho)
        double rFeeder = 0.126115;
        double xFeeder = 1.26353;

        Substation frSubstation = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = addNewVoltageLevel(frSubstation, "VL_1", bus1Vnom);
        Bus bus1 = addNewBus(vl1, "B1", bus2Vnom, 0);

        VoltageLevel vl2 = addNewVoltageLevel(frSubstation, "VL_2", bus2Vnom);
        Bus bus2 = addNewBus(vl2, "B2", bus2Vnom, 0);

        VoltageLevel vl3 = addNewVoltageLevel(frSubstation, "VL_3", bus2Vnom);
        Bus bus3 = addNewBus(vl3, "B3", bus2Vnom, 0);

        VoltageLevel vl4 = addNewVoltageLevel(frSubstation, "VL_4", bus2Vnom);
        Bus bus4 = addNewBus(vl4, "B4", bus2Vnom, 0);

        VoltageLevel vl25 = addNewVoltageLevel(frSubstation, "VL_25", bus2Vnom);
        Bus bus25 = addNewBus(vl25, "B25", bus2Vnom, 0);

        VoltageLevel vl5 = addNewVoltageLevel(frSubstation, "VL_5", bus2Vnom);
        Bus bus5 = addNewBus(vl5, "B5", bus2Vnom, 0);

        // we create a node in between to verify formula of short circuit in the middle of a line
        VoltageLevel vl55 = addNewVoltageLevel(frSubstation, "VL_55", bus2Vnom);
        Bus bus55 = addNewBus(vl55, "B55", bus2Vnom, 0);

        VoltageLevel vl6 = addNewVoltageLevel(frSubstation, "VL_6", bus2Vnom);
        Bus bus6 = addNewBus(vl6, "B6", bus2Vnom, 0);

        Generator feeder = vl1.newGenerator()
                .setId("FEEDER")
                .setBus(bus1.getId())
                .setConnectableBus(bus1.getId())
                .setMinP(0.0)
                .setMaxP(100.)
                .setTargetP(0.)
                .setLocalTargetQ(0.)
                .setLocalTargetV(vl1.getNominalV())
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE)
                    .withRegulating(false)
                    .add()
                .add();
        feeder.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(xFeeder)
                .withDirectTransX(xFeeder)
                .withStepUpTransformerX(0.)
                .add();
        feeder.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(rFeeder)
                .withSubTransRd(rFeeder)
                .add();
        feeder.newExtension(GeneratorFortescueAdder.class)
                .withGrounded(false)
                .add();
        feeder.newExtension(GeneratorFortescueTypeAdder.class)
                .withGeneratorType(GeneratorFortescueType.GeneratorType.FEEDER)
                .add();

        var t1 = frSubstation.newTwoWindingsTransformer()
                .setId("T1")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl3.getId())
                .setBus2(bus3.getId())
                .setConnectableBus2(bus3.getId())
                .setRatedU2(ratedV2)
                .setR(rT1)
                .setX(xT1)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(630.)
                .add();
        var t2 = frSubstation.newTwoWindingsTransformer()
                .setId("T2")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(bus1Vnom)
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setRatedU2(ratedV2)
                .setR(rT2)
                .setX(xT2)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(400.)
                .add();

        Line l25 = addNewLine(network, "L2_B2_B25", vl2, vl25, bus2, bus25,
                rL2 * 0.3, xL2 * 0.3);

        Line l54 = addNewLine(network, "L2_B25_B4", vl25, vl4, bus25, bus4,
                rL2 * 0.7, xL2 * 0.7);

        Line l1 = addNewLine(network, "L1_B3_B4", vl3, vl4, bus3, bus4,
                rL1, xL1);

        Line l3 = addNewLine(network, "L3_B4_B5", vl4, vl5, bus4, bus5,
                rL3, xL3);

        Line l455 = addNewLine(network, "L455_B5_B55", vl5, vl55, bus5, bus55,
                rL4 * 0.3, xL4 * 0.3);

        Line l456 = addNewLine(network, "L4_B55_B6", vl55, vl6, bus55, bus6,
                rL4 * 0.7, xL4 * 0.7);
        //additional data

        l1.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL1 * rL1)
                .withXz(coeffXoL1 * xL1)
                .add();
        l25.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL2 * rL2 * 0.3)
                .withXz(coeffXoL2 * xL2 * 0.3)
                .add();
        l54.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL2 * rL2 * 0.7)
                .withXz(coeffXoL2 * xL2 * 0.7)
                .add();
        l3.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL3 * rL3)
                .withXz(coeffXoL3 * xL3)
                .add();
        /*l4.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL4 * rL4)
                .withXz(coeffXoL4 * xL4)
                .add();*/
        l455.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL4 * rL4 * 0.3)
                .withXz(coeffXoL4 * xL4 * 0.3)
                .add();
        l456.newExtension(LineFortescueAdder.class)
                .withRz(coeffRoL4 * rL4 * 0.7)
                .withXz(coeffXoL4 * xL4 * 0.7)
                .add();

        t1.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(coeffRoT1 * rT1)
                .withXz(coeffXoT1 * xT1)
                .withConnectionType1(WindingConnectionType.DELTA)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .add();
        t2.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(coeffRoT2 * rT2)
                .withXz(coeffXoT2 * xT2)
                .withConnectionType1(WindingConnectionType.DELTA)
                .withConnectionType2(WindingConnectionType.Y_GROUNDED)
                .add();

        return network;
    }

    private static Generator addNewScGenerator(VoltageLevel voltageLevel, Bus bus, String id,
                                               double minP, double maxP, double targetV, double x, double r) {
        Generator generator = addNewGenerator(voltageLevel, bus, id, minP, maxP, 0, 0, targetV, false);
        generator.newExtension(GeneratorShortCircuitAdder.class)
                .withDirectSubtransX(x)
                .withDirectTransX(x)
                .withStepUpTransformerX(0.)
                .add();
        generator.newExtension(GeneratorShortCircuitAdder2.class)
                .withTransRd(r)
                .add();
        return generator;
    }

    private static Line addNewFortescueLine(Network network, String id, VoltageLevel vl1, VoltageLevel vl2,
                                            Bus bus1, Bus bus2, double r, double x, double coeffRo, double coeffXo) {
        Line line = addNewLine(network, id, vl1, vl2, bus1, bus2, r, x);
        line.newExtension(LineFortescueAdder.class)
                .withRz(coeffRo * r)
                .withXz(coeffXo * x)
                .add();
        return line;
    }

    private static void addNewTwoWindingsTransformer(Substation substation, String id,
                                                     VoltageLevel vl1, Bus bus1, double ratedU1,
                                                     VoltageLevel vl2, Bus bus2, double ratedU2,
                                                     double r, double x, double ratedS, double rz, double xz,
                                                     WindingConnectionType connectionType1,
                                                     WindingConnectionType connectionType2) {
        TwoWindingsTransformer twt = substation.newTwoWindingsTransformer()
                .setId(id)
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setRatedU1(ratedU1)
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setRatedU2(ratedU2)
                .setR(r)
                .setX(x)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedS(ratedS)
                .add();
        twt.newExtension(TwoWindingsTransformerFortescueAdder.class)
                .withRz(rz)
                .withXz(xz)
                .withConnectionType1(connectionType1)
                .withConnectionType2(connectionType2)
                .add();
    }

    private static void addNewLeg(ThreeWindingsTransformerAdder.LegAdder leg, double r, double x,
                                  double ratedU, double ratedS, VoltageLevel voltageLevel, Bus bus) {
        leg.setR(r)
                .setX(x)
                .setG(0.0D)
                .setB(0.0D)
                .setRatedU(ratedU)
                .setRatedS(ratedS)
                .setVoltageLevel(voltageLevel.getId())
                .setBus(bus.getId())
                .add();
    }

    private static VoltageLevel addNewVoltageLevel(Substation substation, String id, double nominalV) {
        return substation.newVoltageLevel()
                .setId(id)
                .setNominalV(nominalV)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(2 * nominalV)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
    }

    private static Bus addNewBus(VoltageLevel voltageLevel, String id, double voltage, double angle) {
        Bus bus = voltageLevel.getBusBreakerView().newBus()
                .setId(id)
                .add();
        bus.setV(voltage).setAngle(angle);
        return bus;
    }

    private static Line addNewLine(Network network, String id, VoltageLevel vl1, VoltageLevel vl2,
                                   Bus bus1, Bus bus2, double r, double x) {
        return addNewLine(network, id, vl1, vl2, bus1, bus2, r, x, 0, 0, 0, 0);
    }

    private static Line addNewLine(Network network, String id, VoltageLevel vl1, VoltageLevel vl2,
                                Bus bus1, Bus bus2, double r, double x, double g1, double b1, double g2, double b2) {
        return network.newLine()
                .setId(id)
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setConnectableBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setConnectableBus2(bus2.getId())
                .setR(r)
                .setX(x)
                .setG1(g1)
                .setB1(b1)
                .setG2(g2)
                .setB2(b2)
                .add();
    }

    private static Generator addNewGenerator(VoltageLevel voltageLevel, Bus bus, String id,
                                             double minP, double maxP, double targetP, double targetQ, double targetV, boolean isRegulatorOn) {
        return voltageLevel.newGenerator() //alternative modelling
                .setId(id)
                .setBus(bus.getId())
                .setMinP(minP)
                .setMaxP(maxP)
                .setTargetP(targetP)
                .setLocalTargetQ(targetQ)
                .setLocalTargetV(targetV)
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE)
                    .withRegulating(isRegulatorOn)
                    .add()
                .add();
    }
}
