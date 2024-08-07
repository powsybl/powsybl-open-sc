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

        private double xEq12;
        private double xEq21;
        private double rEq11;
        private double rEq22;

        private double ethx; //real part of Thevenin voltage
        private double ethy; //imaginary part of Thevenin voltage

        private DenseMatrix enBus;

        private Map<Integer, DenseMatrix> dE; // the key stores the number of the bus, the value stores the resolved value [Res] = inv(Y)*[En], with n of vector [En] corresponding to the studied short circuit fault and values at lines of [Res] corresponding real and imaginary parts at bus in key

        private FeedersAtNetwork eqSysFeeders;

        private List<ImpedanceLinearResolutionResultBiphased> biphasedResultsAtBus; // we store here all necessary information for all biphased common ground faults with first bus equal to LfBus = bus

        public class ImpedanceLinearResolutionResultBiphased {

            private LfBus bus2;

            private int numBus2Fault; // stored to easily access the extraction vector at bus2 to get the full voltage export if required

            private double v2x;
            private double v2y;

            private double z22txx; //additional impedance matrix terms to keep as they are needed for biphased common support faults
            private double z22txy;

            private double z21txx;
            private double z21txy;

            private double z12txx;
            private double z12txy;

            private Map<Integer, DenseMatrix> dE2; // store necessary data to compute voltage delta of the full grid for a common support biphased fault
            // the key stores the number of the bus2 for a biphased common support fault, the value stores the resolved value [Res] = inv(Y)*[En], with n of vector [En] corresponding to the studied short circuit fault and values at lines of [Res] corresponding real and imaginary parts at bus2 in key

            ImpedanceLinearResolutionResultBiphased(LfBus bus2, double v2x, double v2y,
                                                    double z22txx, double z22txy,
                                                    double z21txx, double z21txy,
                                                    double z12txx, double z12txy, int numBus2Fault) {
                this.bus2 = bus2;

                this.numBus2Fault = numBus2Fault;

                this.v2x = v2x;
                this.v2y = v2y;

                this.z22txx = z22txx;
                this.z22txy = z22txy;

                this.z21txx = z21txx;
                this.z21txy = z21txy;

                this.z12txx = z12txx;
                this.z12txy = z12txy;

            }

            public void updateWithVoltagesdelta2(AdmittanceMatrix y, DenseMatrix dEn) {
                dE2 = y.getDeltaV(dEn, numBus2Fault);
                //eqSysFeeders = feeders; // TODO : check if feeder are necessary for v2 : contains necessary data to update the contribution of feeders for each shortcircuit
            }

            public LfBus getBus2() {
                return bus2;
            }

            public double getZ12txx() {
                return z12txx;
            }

            public double getZ12txy() {
                return z12txy;
            }

            public double getZ21txx() {
                return z21txx;
            }

            public double getZ21txy() {
                return z21txy;
            }

            public double getZ22txx() {
                return z22txx;
            }

            public double getZ22txy() {
                return z22txy;
            }

            public double getV2x() {
                return v2x;
            }

            public double getV2y() {
                return v2y;
            }

            public Map<Integer, DenseMatrix> getDv2() {
                return dE2;
            }
        }

        ImpedanceLinearResolutionResult(LfBus bus, double rEq11, double rEq22, double xEq12, double xEq21, double ethx, double ethy) {
            this.bus = bus;
            this.rEq11 = rEq11;
            this.rEq22 = rEq22;
            this.xEq12 = xEq12;
            this.xEq21 = xEq21;

            this.ethx = ethx;
            this.ethy = ethy;

            checkResults();
        }

        public LfBus getBus() {
            return bus;
        }

        public double getXthz12() {
            return xEq12;
        }

        public double getRthz11() {
            return rEq11;
        }

        public double getEthr() {
            return ethx;
        }

        public double getEthi() {
            return ethy;
        }

        public Map<Integer, DenseMatrix> getDv() {
            return dE;
        }

        public DenseMatrix getEnBus() {
            return enBus;
        }

        public FeedersAtNetwork getEqSysFeeders() {
            return eqSysFeeders;
        }

        public List<ImpedanceLinearResolutionResultBiphased> getBiphasedResultsAtBus() {
            return biphasedResultsAtBus;
        }

        private void checkResults() {
            checkBlocConsistency(rEq11, rEq22, -xEq12, xEq21, bus);
        }

        public void updateEnBus(double enBus11, double enBus12, double enBus21, double enBus22) {
            enBus = parameters.getMatrixFactory().create(2, 2, 4).toDense();
            enBus.add(0, 0, enBus11);
            enBus.add(0, 1, enBus12);
            enBus.add(1, 0, enBus21);
            enBus.add(1, 1, enBus22);
        }

        public void updateWithVoltagesdelta(AdmittanceMatrix y, DenseMatrix dEn, int numDef, FeedersAtNetwork feeders) {
            dE = y.getDeltaV(dEn, numDef);
            eqSysFeeders = feeders; // contains necessary data to update the contribution of feeders for each short circuit
        }

        public void printResult() {
            System.out.println(" Zth(" + bus.getId() + ") = ");
            System.out.println(" [ rth=" + rEq11 + "  -xth=" + -xEq12 + "]");
            System.out.println(" [ xth=" + xEq21 + "   rth=" + rEq22 + "]");

            if (parameters.isVoltageUpdate()) {
                /*for (Map.Entry<Integer, Double> b : dvr1.entrySet()) {
                    int busi = b.getKey();
                    double dv = b.getValue();
                    System.out.println(" busNum[" + busi + "] : dvr = " +  dv);
                    System.out.println(" busNum[" + busi + "] : dvi = " +  dvi1.get(busi));
                }*/
            }
        }

        public void addBiphasedResult(LfBus bus2, double initV2x, double initV2y, double z22txx, double z22txy,
                                      double z21txx, double z21txy,
                                      double z12txx, double z12txy, int numBus2Fault) {
            // numBus2Fault is store to easily get the extraction vector for the second bus, in order to compute the full voltage export if required
            ImpedanceLinearResolutionResult.ImpedanceLinearResolutionResultBiphased biphasedResult = new ImpedanceLinearResolutionResult.ImpedanceLinearResolutionResultBiphased(bus2, initV2x, initV2y,
                    z22txx, z22txy, z21txx, z21txy, z12txx, z12txy, numBus2Fault);

            if (biphasedResultsAtBus == null) {
                biphasedResultsAtBus = new ArrayList<>();
            }
            biphasedResultsAtBus.add(biphasedResult);
        }
    }

    public static void checkBlocConsistency(double rEq11, double rEq22, double minusXEq12, double xEq21, LfBus lfBus1, LfBus lfBus2) {
        double epsilon = 0.00001;
        double xEq12 = -minusXEq12;

        String bus1Id = lfBus1.getId();
        String bus2Id = lfBus2.getId();

        if (Math.abs(rEq11 - rEq22) > epsilon) {
            throw new IllegalArgumentException("Impedance block values rth : z11 and Z22 of nodes Bus1 = {" + bus1Id + "} and Bus2 = {" + bus2Id + "} have inconsitant values z11= " + rEq11 + " y1i2i=" + rEq22);
        }

        if (Math.abs(xEq12 - xEq21) > epsilon) {
            throw new IllegalArgumentException("Impedance block values xth : z12 and Z21 of nodes Bus1 = {" + bus1Id + "} and Bus2 = {" + bus2Id + "} have inconsitant values z12= " + xEq12 + " z21=" + xEq21);
        }
    }

    public static void checkBlocConsistency(double rEq11, double rEq22, double minusXEq12, double xEq21, LfBus lfBus1) {
        // case of diagonal block
        checkBlocConsistency(rEq11, rEq22, minusXEq12, xEq21, lfBus1, lfBus1);
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

            double ethx = 1.0;
            double ethy = 0.0;
            numBusFault = 0;
            for (LfBus lfBus : inputBusses) {

                int yRow1x = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YR);
                int yRow1y = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YI);

                if (parameters.getTheveninVoltageProfileType() == AdmittanceEquationSystem.AdmittanceVoltageProfileType.CALCULATED) {
                    ethx = lfBus.getV() * Math.cos(lfBus.getAngle());
                    ethy = lfBus.getV() * Math.sin(lfBus.getAngle());
                }

                //this is equivalent to get the diagonal blocks of tEn * inv(Y) * En but taking advantage of the sparsity of tEn
                ImpedanceLinearResolutionResult res = new ImpedanceLinearResolutionResult(lfBus,
                        en.get(tEn2Col.get(2 * numBusFault), 2 * numBusFault),
                        en.get(tEn2Col.get(1 + 2 * numBusFault), 1 + 2 * numBusFault),
                        -en.get(tEn2Col.get(2 * numBusFault), 1 + 2 * numBusFault),
                        en.get(tEn2Col.get(1 + 2 * numBusFault), 2 * numBusFault),
                        ethx,
                        ethy);

                //step 4 : add deltaVoltage vectors if required
                //extract values at the faulting bus that will be used to compute the post-fault voltage delta at bus
                // This equivalent to compute  t[En]*inv(Y)*[En] in :
                // [ Vx ]                        [ Ix ]           [ Vx_init ]
                // [ Vy ] = -t[En]*inv(Y)*[En] * [ Iy ] + t[En] * [ Vy_init ]
                double enBusxx = en.get(yRow1x, 2 * numBusFault);
                double enBusyx = en.get(yRow1x, 2 * numBusFault + 1);
                double enBusxy = en.get(yRow1y, 2 * numBusFault);
                double enBusyy = en.get(yRow1y, 2 * numBusFault + 1);

                res.updateEnBus(enBusxx, enBusyx, enBusxy, enBusyy);

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

                        double enZ22txx = en.get(yCol2x, 2 * numBus2Fault);
                        double enZ22tyx = en.get(yCol2y, 2 * numBus2Fault);
                        double enZ22txy = en.get(yCol2x, 2 * numBus2Fault + 1);
                        double enZ22tyy = en.get(yCol2y, 2 * numBus2Fault + 1);

                        double enZ21txx = en.get(yCol2x, 2 * numBusFault);
                        double enZ21tyx = en.get(yCol2y, 2 * numBusFault);
                        double enZ21txy = en.get(yCol2x, 2 * numBusFault + 1);
                        double enZ21tyy = en.get(yCol2y, 2 * numBusFault + 1);

                        double enZ12txx = en.get(yCol1x, 2 * numBus2Fault);
                        double enZ12tyx = en.get(yCol1y, 2 * numBus2Fault);
                        double enZ12txy = en.get(yCol1x, 2 * numBus2Fault + 1);
                        double enZ12tyy = en.get(yCol1y, 2 * numBus2Fault + 1);

                        // By construction we have for each block
                        //
                        // Zij = [ rij  -xij ]
                        //       [ xij   rij ]
                        //
                        // We need to check consistency of terms for each block
                        checkBlocConsistency(enZ22txx, enZ22tyy, enZ22txy, enZ22tyx, lfBus, bus2);
                        checkBlocConsistency(enZ21txx, enZ21tyy, enZ21txy, enZ21tyx, lfBus, bus2);
                        checkBlocConsistency(enZ12txx, enZ12tyy, enZ12txy, enZ12tyx, lfBus, bus2);

                        double eth2x = 1.0;
                        double eth2y = 0.;
                        if (parameters.getTheveninVoltageProfileType() == AdmittanceEquationSystem.AdmittanceVoltageProfileType.CALCULATED) {
                            eth2x = bus2.getV() * Math.cos(lfBus.getAngle());
                            eth2y = bus2.getV() * Math.sin(lfBus.getAngle());
                        }

                        res.addBiphasedResult(bus2, eth2x, eth2y, enZ22txx, enZ22txy, enZ21txx, enZ21txy, enZ12txx, enZ12txy, numBus2Fault);
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
