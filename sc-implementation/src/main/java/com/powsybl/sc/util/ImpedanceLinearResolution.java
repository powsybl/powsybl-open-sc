/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util;

import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.extensions.FrequencyType;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.Pair;

import java.util.*;

import static com.powsybl.sc.extensions.ShortCircuitConstants.IMPEDANCE_CONSISTENCY_TOLERANCE_PU;
import static com.powsybl.sc.util.ShortCircuitHelper.getOrDefaultOpenScParameters;

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
    private final Network network;

    private final LfNetwork lfNetwork;

    private final List<CalculationLocation> calculationLocations; // calculation locations where only one bus is required in input

    private final AdmittanceType admittanceType;

    private final ShortCircuitParameters parameters;

    private final List<Complex> initialVoltages;

    public final Map<LfBus, ImpedanceLinearResolutionResult> results = new HashMap<>();

    public ImpedanceLinearResolution(Network network, LfNetwork lfNetwork, List<CalculationLocation> calculationLocations,
                                     List<Complex> initialVoltages,
                                     AdmittanceType admittanceType,
                                     ShortCircuitParameters parameters) {
        this.network = Objects.requireNonNull(network);
        this.lfNetwork = Objects.requireNonNull(lfNetwork);
        this.calculationLocations = calculationLocations;
        this.initialVoltages = initialVoltages;
        this.admittanceType = admittanceType;
        this.parameters = Objects.requireNonNull(parameters);
    }

    public void run() {
        OpenShortCircuitParameters openScParameters = getOrDefaultOpenScParameters(parameters);
        AdmittanceEquationSystemCreator equationSystemCreator = new AdmittanceEquationSystemCreator(network, lfNetwork, initialVoltages,
                admittanceType, parameters, openScParameters);

        FeedersAtNetwork equationsSystemFeeders = new FeedersAtNetwork();
        EquationSystem<VariableType, EquationType> equationSystem
                = equationSystemCreator.create(equationsSystemFeeders, FrequencyType.FREQ_50_HZ);

        // feeders at 20Hz are not used, but required by the equation system creation
        EquationSystem<VariableType, EquationType> equationSystem20hz
                = equationSystemCreator.create(new FeedersAtNetwork(), FrequencyType.FREQ_20_HZ);

        List<LfBus> inputBuses = new ArrayList<>();
        List<Pair<LfBus, LfBus>> busPairList = new ArrayList<>();
        collectInputBuses(inputBuses, busPairList);

        try (AdmittanceMatrix yd = new AdmittanceMatrix(equationSystem, openScParameters.getMatrixFactory(), lfNetwork);
             AdmittanceMatrix yd20hz = new AdmittanceMatrix(equationSystem20hz, openScParameters.getMatrixFactory(), lfNetwork)) {

            // zfromLu = inv(Y) * [En], computed for each frequency
            DenseMatrix zfromLu = solveExtraction(yd, inputBuses);
            DenseMatrix zfromLu20Hz = solveExtraction(yd20hz, inputBuses);

            for (int numBusFault = 0; numBusFault < inputBuses.size(); numBusFault++) {
                LfBus lfBus = inputBuses.get(numBusFault);
                Complex eth = initialVoltages.get(lfBus.getNum());

                // The diagonal blocks of tEn * inv(Y) * En are the Thevenin impedances at each corresponding bus
                Complex zth = extractImpedance(yd, zfromLu, lfBus, numBusFault, lfBus);
                Complex zth20Hz = extractImpedance(yd20hz, zfromLu20Hz, lfBus, numBusFault, lfBus);

                ImpedanceLinearResolutionResult res = new ImpedanceLinearResolutionResult(lfBus, eth, zth, zth20Hz);

                //step 4 : add deltaVoltage vectors if required
                //extract values at the faulting bus that will be used to compute the post-fault voltage delta at bus

                // [ V ] = -inv(Y) * M * [ Icc ] + [ V(init) ]
                // Where is the short circuit current vector [Icc] = [ 0 ; 0 .... 0 ; icc ; 0 .... 0 ; 0 ] with non-zero term corresponds to faulted node nf
                // Where [V] is the resulting voltage profile avec short circuit, [V] = [ V1 ; V2 ; ..... ; Vk ; .... ; Vm ]
                // Vk = z(k,nf) . icc
                // we need then to extract z(k,nf) if we want to compute updated Vk from computed icc value
                int yRow1x = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YR);
                int yRow1y = yd.getRowBus(lfBus.getNum(), EquationType.BUS_YI);
                Complex zknf = new Complex(zfromLu.get(yRow1x, 2 * numBusFault), zfromLu.get(yRow1y, 2 * numBusFault));
                res.updateZknf(zknf);

                // handle biphased common support faults extra data
                for (Pair<LfBus, LfBus> busPair : busPairList) {
                    if (busPair.getKey() == lfBus) {
                        // lfbus is also the first bus for a biphased common support, we store as an extension necessary additional data for the linear resolution post-processing
                        LfBus bus2 = busPair.getValue();
                        int numBus2Fault = inputBuses.indexOf(bus2); // column of the extraction matrix of bus2
                        if (numBus2Fault < 0) {
                            throw new IllegalArgumentException(" Second bus = " + bus2.getId() + " of bi-phased or branch fault : not found in the extraction matrix");
                        }

                        ImpedanceUpperTriangle zT = extractSecondBusImpedances(yd, zfromLu, lfBus, numBusFault, bus2, numBus2Fault);
                        ImpedanceUpperTriangle zTAt20Hz = extractSecondBusImpedances(yd20hz, zfromLu20Hz, lfBus, numBusFault, bus2, numBus2Fault);

                        Complex eth2 = initialVoltages.get(bus2.getNum());
                        res.addTwoBusResult(bus2, eth2, zT, zTAt20Hz, numBus2Fault);
                    }
                }

                //if required, do the same for all buses from the grid
                if (parameters.isWithVoltageResult()) {
                    // This equivalent to store  inv(Y)*[En]
                    res.updateWithVoltagesdelta(yd, zfromLu, numBusFault, equationsSystemFeeders);
                    if (res.getTwoBusResults() != null) {
                        // update for each biphased common support fault
                        for (TwoBusImpedanceLinearResolutionResult twoBusResultPart : res.getTwoBusResults()) {
                            twoBusResultPart.updateWithVoltagesdelta2(yd, zfromLu);
                        }
                    }
                }
                this.results.put(lfBus, res);
            }
        }
    }

    private void collectInputBuses(List<LfBus> inputBuses, List<Pair<LfBus, LfBus>> busPairList) {
        for (CalculationLocation locationInfo : calculationLocations) {
            LfBus bus1 = getLfBusFromIidmBranch(locationInfo.getIidmBusInfo().getKey(), locationInfo.getIidmBusInfo().getValue(), lfNetwork);
            if (bus1 != null) {
                if (!inputBuses.contains(bus1)) {
                    inputBuses.add(bus1);
                }
                locationInfo.setLfBusInfo(bus1.getId());
            }
            boolean needsSecondBus = locationInfo.getLocationType() == CalculationLocation.LocationType.LINE
                    || locationInfo.getLocationType() == CalculationLocation.LocationType.BIPHASED_COMMON_SUPPORT;
            if (needsSecondBus) {
                LfBus bus2 = getLfBusFromIidmBranch(locationInfo.getIidmBus2Info().getKey(), locationInfo.getIidmBus2Info().getValue(), lfNetwork);
                if (bus2 != null) {
                    if (!inputBuses.contains(bus2)) {
                        inputBuses.add(bus2);
                    }
                    locationInfo.setLfBus2Info(bus2.getId());
                    if (bus1 != null) {
                        busPairList.add(new Pair<>(bus1, bus2));
                    }
                }
            }
        }
    }

    private static DenseMatrix solveExtraction(AdmittanceMatrix y, List<LfBus> inputBuses) {
        // Build of the structure of the extraction matrices
        //               <------------------->  N
        //          ^ [ .....   0    0   ..... ]
        //          | [         0    0         ]
        //          | [                        ]     M = y.getRowCount()
        // [En] = M | [         1    0         ]     N = 2 * inputBuses.size()
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
        DenseMatrix en = new DenseMatrix(y.getRowCount(), 2 * inputBuses.size());
        for (int numBusFault = 0; numBusFault < inputBuses.size(); numBusFault++) {
            int numBus = inputBuses.get(numBusFault).getNum();
            // [En_x][i,1]= 1 if i = yRowRth and 0 else
            // [En_y][i,1]= 1 if i = yRowXth and 0 else
            en.add(y.getRowBus(numBus, EquationType.BUS_YR), 2 * numBusFault, 1.0);
            en.add(y.getRowBus(numBus, EquationType.BUS_YI), 2 * numBusFault + 1, 1.0);
        }
        y.solveTransposed(en);
        return en;
    }

    private static Complex extractImpedance(AdmittanceMatrix y, DenseMatrix zfromLu, LfBus bus, int numColBus, LfBus colBus) {
        int yColx = y.getColBus(bus.getNum(), VariableType.BUS_VR);
        int yColy = y.getColBus(bus.getNum(), VariableType.BUS_VI);
        Complex z = new Complex(zfromLu.get(yColx, 2 * numColBus), -zfromLu.get(yColx, 2 * numColBus + 1));
        Complex zBis = new Complex(zfromLu.get(yColy, 2 * numColBus + 1), zfromLu.get(yColy, 2 * numColBus));
        checkMatrixExtractionConsistency(z, zBis, bus, colBus);
        return z;
    }

    private static ImpedanceUpperTriangle extractSecondBusImpedances(AdmittanceMatrix y, DenseMatrix zfromLu,
                                                                     LfBus bus1, int numBus1Fault,
                                                                     LfBus bus2, int numBus2Fault) {
        Complex z12 = extractImpedance(y, zfromLu, bus1, numBus2Fault, bus2);
        Complex z21 = extractImpedance(y, zfromLu, bus2, numBus1Fault, bus1);
        Complex z22 = extractImpedance(y, zfromLu, bus2, numBus2Fault, bus2);
        return new ImpedanceUpperTriangle(z12, z21, z22);
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

    public static void checkMatrixExtractionConsistency(Complex z1, Complex z2, LfBus lfBus1, LfBus lfBus2) {

        String bus1Id = lfBus1.getId();
        String bus2Id = lfBus2.getId();

        if (Math.abs(z1.getReal() - z2.getReal()) > IMPEDANCE_CONSISTENCY_TOLERANCE_PU) {
            throw new IllegalArgumentException(
                    "Impedance block values rth : z11 and Z22 of nodes Bus1 = {" + bus1Id + "} and Bus2 = {" + bus2Id +
                            "} have inconsitant values z11= " + z1.getReal() + " y1i2i=" + z2.getReal());
        }

        if (Math.abs(z2.getImaginary() - z1.getImaginary()) > IMPEDANCE_CONSISTENCY_TOLERANCE_PU) {
            throw new IllegalArgumentException(
                    "Impedance block values xth : z12 and Z21 of nodes Bus1 = {" + bus1Id + "} and Bus2 = {" + bus2Id +
                            "} have inconsitant values z12= " + z1.getImaginary() + " z21=" + z2.getImaginary());
        }
    }
}
