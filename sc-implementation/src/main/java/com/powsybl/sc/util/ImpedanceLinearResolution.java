/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ImpedanceLinearResolution {

    // This class is used to resolve problems with a similar structure
    // [ Vof ] = -tM * inv(Yo) * M * [ Iof ]
    // [ Vdf ] = -tM * inv(Yd) * M * [ Idf ] + tM * [ V(init) ]
    // [ Vif ] = -tM * inv(Yd) * M * [ Iif ]

    //
    // [ Vx ]                        [ Ix ]           [ Vx_init ]
    // [ Vy ] = -t[En]*inv(Y)*[En] * [ Iy ] + t[En] * [ Vy_init ]
    //

    private final LfNetwork network;

    private final ImpedanceLinearResolutionParameters parameters;

    public final List<ImpedanceLinearResolutionResult> results = new ArrayList<>();

    public ImpedanceLinearResolution(LfNetwork network, ImpedanceLinearResolutionParameters parameters) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public class ImpedanceLinearResolutionResult {

        private LfBus bus;
        private Complex zthEq;
        private Complex eth; // Thevenin voltage

        // zknf corresponds to extracted impedance term z(k,nf) from inv(Y) to be able to compute Vk = z(k,nf).icc.
        // k is the bus index of the voltage Vk we want to compute and nf is the index of faulted bus
        private Complex zknf;

        // This is a map to easily access the impedance terms resulting from inv(Y), extracting only useful z(i,j) terms
        // the key stores the number of the bus k at which we want to compute the voltage from formula Vk = z(k,nf).icc, the value stores the resolved value [Res] = inv(Y)*[En],
        // with n of vector [En] corresponding to the studied short circuit fault and values at lines of [Res] corresponding real and imaginary parts at bus in key
        private Map<Integer, Complex> busToZknf;

        private FeedersAtNetwork eqSysFeeders;

        private List<ImpedanceLinearResolutionResultBiphased> biphasedResultsAtBus; // we store here all necessary information for all biphased common ground faults with first bus equal to LfBus = bus

        public class ImpedanceLinearResolutionResultBiphased {

            private LfBus bus2;

            private int numBus2Fault; // stored to easily access the extraction vector at bus2 to get the full voltage export if required

            private Complex v2;

            private Complex z22; //additional impedance matrix terms to keep as they are needed for biphased common support faults
            private Complex z21;
            private Complex z12;

            // This map is similar to busToZknf map, applied for bus2 k
            // store necessary data to compute voltage delta of the full grid for a common support biphased fault
            // the key stores the number of the bus2 for a biphased common support fault, the value stores the resolved value [Res] = inv(Y)*[En],
            // with n of vector [En] corresponding to the studied short circuit fault and values at lines of [Res] corresponding real and imaginary parts at bus2 in key
            private Map<Integer, Complex> bus2ToZknf;

            ImpedanceLinearResolutionResultBiphased(LfBus bus2, Complex v2, Complex z22, Complex z21, Complex z12, int numBus2Fault) {
                this.bus2 = bus2;

                this.numBus2Fault = numBus2Fault;

                this.v2 = v2;

                this.z22 = z22;
                this.z21 = z21;
                this.z12 = z12;
            }

            public void updateWithVoltagesdelta2(AdmittanceMatrix y, DenseMatrix dEn) {
                bus2ToZknf = y.getDeltaV(dEn, numBus2Fault);
                //eqSysFeeders = feeders; // TODO : check if feeder are necessary for v2 : contains necessary data to update the contribution of feeders for each shortcircuit
            }

            public LfBus getBus2() {
                return bus2;
            }

            public Complex getZ12() {
                return z12;
            }

            public Complex getZ21() {
                return z21;
            }

            public Complex getZ22() {
                return z22;
            }

            public Complex getV2() {
                return v2;
            }

            public Map<Integer, Complex> getBus2ToZknf() {
                return bus2ToZknf;
            }
        }

        ImpedanceLinearResolutionResult(LfBus bus, Complex zth, Complex eth) {
            this.bus = bus;
            this.zthEq = zth;
            this.eth = eth;
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

        public Map<Integer, Complex> getBusToZknf() {
            return busToZknf;
        }

        public Complex getZknf() {
            return zknf;
        }

        public FeedersAtNetwork getEqSysFeeders() {
            return eqSysFeeders;
        }

        public List<ImpedanceLinearResolutionResultBiphased> getBiphasedResultsAtBus() {
            return biphasedResultsAtBus;
        }

        public void updatezknf(Complex enBus) {
            this.zknf = enBus;
        }

        public void updateWithVoltagesdelta(AdmittanceMatrix y, DenseMatrix dEn, int numDef, FeedersAtNetwork feeders) {
            busToZknf = y.getDeltaV(dEn, numDef);
            eqSysFeeders = feeders; // contains necessary data to update the contribution of feeders for each short circuit
        }

        public void printResult() {
            System.out.println(" Zth(" + bus.getId() + ") = " + zthEq);

            if (parameters.isVoltageUpdate()) {
                /*for (Map.Entry<Integer, Double> b : dvr1.entrySet()) {
                    int busi = b.getKey();
                    double dv = b.getValue();
                    System.out.println(" busNum[" + busi + "] : dvr = " +  dv);
                    System.out.println(" busNum[" + busi + "] : dvi = " +  dvi1.get(busi));
                }*/
            }
        }

        public void addBiphasedResult(LfBus bus2, Complex initV2, Complex z22, Complex z21, Complex z12, int numBus2Fault) {
            // numBus2Fault is store to easily get the extraction vector for the second bus, in order to compute the full voltage export if required
            ImpedanceLinearResolutionResult.ImpedanceLinearResolutionResultBiphased biphasedResult = new ImpedanceLinearResolutionResult.ImpedanceLinearResolutionResultBiphased(bus2, initV2,
                    z22, z21, z12, numBus2Fault);

            if (biphasedResultsAtBus == null) {
                biphasedResultsAtBus = new ArrayList<>();
            }
            biphasedResultsAtBus.add(biphasedResult);
        }
    }

    public static void checkMatrixExtractionConsistency(Complex z1, Complex z2, LfBus lfBus1, LfBus lfBus2) {
        double epsilon = 0.00001;

        String bus1Id = lfBus1.getId();
        String bus2Id = lfBus2.getId();

        if (Math.abs(z1.getReal() - z2.getReal()) > epsilon) {
            throw new IllegalArgumentException("Impedance block values rth : z11 and Z22 of nodes Bus1 = {" + bus1Id + "} and Bus2 = {" + bus2Id + "} have inconsitant values z11= " + z1.getReal() + " y1i2i=" + z2.getReal());
        }

        if (Math.abs(z2.getImaginary() - z1.getImaginary()) > epsilon) {
            throw new IllegalArgumentException("Impedance block values xth : z12 and Z21 of nodes Bus1 = {" + bus1Id + "} and Bus2 = {" + bus2Id + "} have inconsitant values z12= " + z1.getImaginary() + " z21=" + z2.getImaginary());
        }
    }

    public static void checkMatrixExtractionConsistency(Complex z1, Complex z2, LfBus lfBus1) {
        // case of diagonal matrix terms
        checkMatrixExtractionConsistency(z1, z2, lfBus1, lfBus1);
    }

    public static LfBus getLfBusFromIidmBranch(String iidmBranchId, int branchSide, LfNetwork lfNetwork) {
        LfBus bus = null;
        for (LfBranch lfBranch : lfNetwork.getBranches()) {
            String branchId = lfBranch.getId();
            LfBranch.BranchType lfType = lfBranch.getBranchType();

            if (lfType == LfBranch.BranchType.LINE || lfType == LfBranch.BranchType.TRANSFO_2) {
                if (iidmBranchId.equals(branchId)) {
                    if (branchSide == 1) {
                        bus = lfBranch.getBus1();

                    } else {
                        bus = lfBranch.getBus2();
                    }
                    break;
                }
            } else if (lfType == LfBranch.BranchType.TRANSFO_3_LEG_1) {
                String legId = iidmBranchId + "_leg_1";
                if (legId.equals(branchId) && branchSide == 1) {
                    // assumption made: side 2 bus is always the star bus of the T3W
                    bus = lfBranch.getBus1();
                    break;
                }

            } else if (lfType == LfBranch.BranchType.TRANSFO_3_LEG_2) {
                String legId = iidmBranchId + "_leg_2";
                if (legId.equals(branchId) && branchSide == 2) {
                    // assumption made: side 2 bus is always the star bus of the T3W
                    bus = lfBranch.getBus1();
                    break;
                }

            } else if (lfType == LfBranch.BranchType.TRANSFO_3_LEG_3) {
                String legId = iidmBranchId + "_leg_3";
                if (legId.equals(branchId) && branchSide == 3) {
                    // assumption made: side 2 bus is always the star bus of the T3W
                    bus = lfBranch.getBus1();
                    break;
                }
            }
        }
        return bus;

    }

    public void run() {

        FeedersAtNetwork equationsSystemFeeders = new FeedersAtNetwork();
        EquationSystem<VariableType, EquationType> equationSystem
                = AdmittanceEquationSystem.create(network, new VariableSet<>(), parameters.getAdmittanceType(), parameters.getTheveninVoltageProfileType(), parameters.getTheveninPeriodType(), parameters.isTheveninIgnoreShunts(), equationsSystemFeeders, parameters.getAcLoadFlowParameters());

        //Get bus by voltage level
        List<LfBus> inputBusses = new ArrayList<>();
        for (CalculationLocation faultBranchLocationInfo : parameters.getCalculationLocations()) {
            String iidmBranchId = faultBranchLocationInfo.getIidmBusInfo().getKey();
            int branchSide = faultBranchLocationInfo.getIidmBusInfo().getValue();

            LfBus bus = getLfBusFromIidmBranch(iidmBranchId, branchSide, network);
            if (bus != null) {
                inputBusses.add(bus);
                faultBranchLocationInfo.setLfBusInfo(bus.getId());
            }
        }

        // case it is a biphased common support input, supposing that the number of such input contingencies is low
        List<Pair<LfBus, LfBus>> biphasedinputBusses = new ArrayList<>();
        if (parameters.getBiphasedCalculationLocations() != null) {
            for (CalculationLocation biphasedFaultBranchLocationInfo : parameters.getBiphasedCalculationLocations()) {

                String iidmBranchId = biphasedFaultBranchLocationInfo.getIidmBusInfo().getKey();
                int branchSide = biphasedFaultBranchLocationInfo.getIidmBusInfo().getValue();

                String iidmBranch2Id = biphasedFaultBranchLocationInfo.getIidmBus2Info().getKey();
                int branch2Side = biphasedFaultBranchLocationInfo.getIidmBus2Info().getValue();

                LfBus bus1 = getLfBusFromIidmBranch(iidmBranchId, branchSide, network);
                LfBus bus2 = getLfBusFromIidmBranch(iidmBranch2Id, branch2Side, network);

                if (bus1 != null && bus2 != null) {
                    Pair<LfBus, LfBus> bussesPair = new Pair<>(bus1, bus2);
                    biphasedinputBusses.add(bussesPair);
                    biphasedFaultBranchLocationInfo.setLfBusInfo(bus1.getId());
                    biphasedFaultBranchLocationInfo.setLfBus2Info(bus2.getId());
                }
            }
        }

        // Addition of biphased faults in the inputBusses
        for (Pair<LfBus, LfBus> pairBusses : biphasedinputBusses) {
            LfBus bus1 = pairBusses.getKey();
            LfBus bus2 = pairBusses.getValue();
            if (!inputBusses.contains(bus1)) {
                inputBusses.add(bus1);
            }
            if (!inputBusses.contains(bus2)) {
                inputBusses.add(bus2);
            }
        }

        // Build of the structure of the extraction matrices
        //               <------------------->  N
        //          ^ [ .....   0    0   ..... ]
        //          | [         0    0         ]
        //          | [                        ]     M = y.getRowCount()
        // [En] = M | [         1    0         ]     N = 2 * inputBusses.size()
        //          | [         0    1         ]
        //          | [                        ]
        //          | [         0    0         ]
        //          - [ .....   0    0   ......]
        //                      ^    ^
        //                  En_x_k   |
        //                        En_y_k
        //
        //  - En_x_k is the vector t[ 0 0 ... 0 0 1 0 0 0 ... 0 0 ] where 1 corresponds to the line/column of the bus k where the real part of Z matrix is modelled
        //  - En_y_k is the vector t[ 0 0 ... 0 0 0 1 0 0 ... 0 0 ] where 1 corresponds to the line/column of the bus k where the imaginary part of Z matrix is modelled

        // Step 1 : build the extraction vectors
        try (AdmittanceMatrix yd = new AdmittanceMatrix(equationSystem, parameters.getMatrixFactory(), network)) {

            DenseMatrix en = new DenseMatrix(yd.getRowCount(), 2 * inputBusses.size());
            List<Integer> tEn2Col = new ArrayList<>();

            int numBusFault = 0;
            for (LfBus lfBus : inputBusses) {

                int yRowx = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YR);
                int yColx = yd.getColBus(lfBus.getNum(), VariableType.BUS_VR);
                int yRowy = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YI);
                int yColy = yd.getColBus(lfBus.getNum(), VariableType.BUS_VI);

                //Step 2: fill the extraction matrices based on each extraction vector
                // [tEn_x][1,j]= 1 if j = yColRth and 0 else
                // [tEn_y][1,j]= 1 if j = yColXth and 0 else
                //tEn.add(2 * numBusFault, yColx, 1.0);
                //tEn.add(2 * numBusFault + 1, yColy, 1.0);

                //the extraction matrix tEn is replaced by a list to directly get the elements rth and xth in inv(Y) * En as tEn is very sparse
                tEn2Col.add(yColx);
                tEn2Col.add(yColy);

                // [En_x][i,1]= 1 if i = yRowRth and 0 else
                // [En_y][i,1]= 1 if i = yRowXth and 0 else
                en.add(yRowx, 2 * numBusFault, 1.0);
                en.add(yRowy, 2 * numBusFault + 1, 1.0);

                numBusFault++;
            }

            //Step 3 : use the LU inversion of Y to get Rth and Xth
            yd.solveTransposed(en);

            // Each diagonal bloc of tEn * inv(Y) * En is:
            //     [Zkk] = [ r -x ]
            //             [ x  r ]

            //DenseMatrix z = (DenseMatrix) tEn.times(en);

            Complex eth = new Complex(1.0);

            numBusFault = 0;
            for (LfBus lfBus : inputBusses) {

                int yRow1x = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YR);
                int yRow1y = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YI);

                if (parameters.getTheveninVoltageProfileType() == AdmittanceEquationSystem.AdmittanceVoltageProfileType.CALCULATED) {
                    eth = new Complex(lfBus.getV() * Math.cos(lfBus.getAngle()), lfBus.getV() * Math.sin(lfBus.getAngle()));
                }

                // This is equivalent to get the diagonal blocks of tEn * inv(Y) * En but taking advantage of the sparsity of tEn
                // The diagonal terms of the impedance matrix are the Thevenin impedance at each corresponding bus
                Complex zth = new Complex(en.get(tEn2Col.get(2 * numBusFault), 2 * numBusFault), -en.get(tEn2Col.get(2 * numBusFault), 1 + 2 * numBusFault));
                Complex zthBis = new Complex(en.get(tEn2Col.get(1 + 2 * numBusFault), 1 + 2 * numBusFault), en.get(tEn2Col.get(1 + 2 * numBusFault), 2 * numBusFault));
                //     [Zth_kk] = [ rth -xth ] --> Zth
                //                [ xth  rth ] --> Zth_Bis
                checkMatrixExtractionConsistency(zth, zthBis, lfBus);

                ImpedanceLinearResolutionResult res = new ImpedanceLinearResolutionResult(lfBus, zth, eth);

                //step 4 : add deltaVoltage vectors if required
                //extract values at the faulting bus that will be used to compute the post-fault voltage delta at bus

                // [ V ] = -inv(Y) * M * [ Icc ] + [ V(init) ]
                // Where is the short circuit current vector [Icc] = [ 0 ; 0 .... 0 ; icc ; 0 .... 0 ; 0 ] with non zero term corresponds to faulted node nf
                // Where [V] is the resulting voltage profile avec short circuit, [V] = [ V1 ; V2 ; ..... ; Vk ; .... ; Vm ]
                // Vk = z(k,nf) . icc
                // we need then to extract z(k,nf) if we want to compute updated Vk from computed icc value

                //double enBusxx = en.get(yRow1x, 2 * numBusFault);
                //double enBusyx = en.get(yRow1x, 2 * numBusFault + 1);
                //double enBusxy = en.get(yRow1y, 2 * numBusFault);
                //double enBusyy = en.get(yRow1y, 2 * numBusFault + 1);

                Complex zknf = new Complex(en.get(yRow1x, 2 * numBusFault), en.get(yRow1y, 2 * numBusFault));

                res.updatezknf(zknf);

                // handle biphased common support faults extra data
                for (Pair<LfBus, LfBus> pairBusses : biphasedinputBusses) {
                    LfBus bus1 = pairBusses.getKey();
                    if (bus1 == lfBus) {
                        // lfbus is also the first bus for a biphased common support, we store as an extension necessary additional data for the linear resolution post-processing
                        LfBus bus2 = pairBusses.getValue();
                        int yCol1x = yd.getColBus(lfBus.getNum(), VariableType.BUS_VR);
                        int yCol1y = yd.getColBus(lfBus.getNum(), VariableType.BUS_VI);
                        int yCol2x = yd.getColBus(bus2.getNum(), VariableType.BUS_VR);
                        int yCol2y = yd.getColBus(bus2.getNum(), VariableType.BUS_VI);

                        int numBus2Fault = 0; // get the right column of extraction matrix of bus2
                        boolean bus2found = false;
                        for (LfBus lfBus2 : inputBusses) {
                            if (lfBus2 == bus2) {
                                bus2found = true;
                                break;
                            }
                            numBus2Fault++;
                        }

                        if (!bus2found) {
                            throw new IllegalArgumentException(" Biphased fault second bus = " + bus2.getId() + " : not found in the extraction matrix");
                        }

                        Complex z22 = new Complex(en.get(yCol2x, 2 * numBus2Fault), -en.get(yCol2x, 2 * numBus2Fault + 1));
                        Complex z22bis = new Complex(en.get(yCol2y, 2 * numBus2Fault + 1), en.get(yCol2y, 2 * numBus2Fault));

                        Complex z21 = new Complex(en.get(yCol2x, 2 * numBusFault), -en.get(yCol2x, 2 * numBusFault + 1));
                        Complex z21bis = new Complex(en.get(yCol2y, 2 * numBusFault + 1), en.get(yCol2y, 2 * numBusFault));

                        Complex z12 = new Complex(en.get(yCol1x, 2 * numBus2Fault), -en.get(yCol1x, 2 * numBus2Fault + 1));
                        Complex z12bis = new Complex(en.get(yCol1y, 2 * numBus2Fault + 1), en.get(yCol1y, 2 * numBus2Fault));

                        // By construction we have for each block
                        //
                        // Zij = [ rij  -xij ] --> enZ
                        //       [ xij   rij ] --> enZbis
                        //
                        // We need to check consistency of terms enZ and enZbis for each block
                        checkMatrixExtractionConsistency(z22, z22bis, lfBus, bus2);
                        checkMatrixExtractionConsistency(z21, z21bis, lfBus, bus2);
                        checkMatrixExtractionConsistency(z12, z12bis, lfBus, bus2);

                        Complex eth2 = new Complex(1.0);
                        if (parameters.getTheveninVoltageProfileType() == AdmittanceEquationSystem.AdmittanceVoltageProfileType.CALCULATED) {
                            eth2 = new Complex(bus2.getV() * Math.cos(lfBus.getAngle()), bus2.getV() * Math.sin(lfBus.getAngle()));
                        }

                        res.addBiphasedResult(bus2, eth2, z22, z21, z12, numBus2Fault);
                    }
                }

                //if required, do the same for all busses from the grid
                if (parameters.isVoltageUpdate()) {
                    // This equivalent to store  inv(Y)*[En]
                    res.updateWithVoltagesdelta(yd, en, numBusFault, equationsSystemFeeders);
                    if (res.biphasedResultsAtBus != null) {
                        // update for each biphased common support fault
                        for (ImpedanceLinearResolutionResult.ImpedanceLinearResolutionResultBiphased biphasedResultPart : res.biphasedResultsAtBus) {
                            biphasedResultPart.updateWithVoltagesdelta2(yd, en);
                        }
                    }
                }

                //res.printResult();

                this.results.add(res);
                numBusFault++;
            }
        }
    }

}
