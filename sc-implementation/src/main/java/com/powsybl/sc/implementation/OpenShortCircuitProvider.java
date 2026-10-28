/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.google.auto.service.AutoService;
import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.sc.util.CalculationLocation;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sc.util.Feeder;
import com.powsybl.sc.extensions.OpenShortCircuitParameters;
import com.powsybl.sc.util.FeedersAtBusResult;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.shortcircuit.*;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.PerUnit.SB;
import static com.powsybl.sc.util.ShortCircuitHelper.isLfBusInInnerZone;
import static org.apache.commons.math3.complex.ComplexUtils.polar2Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(ShortCircuitAnalysisProvider.class)
public class OpenShortCircuitProvider implements ShortCircuitAnalysisProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenShortCircuitProvider.class);

    private final MatrixFactory matrixFactory;

    public OpenShortCircuitProvider() {
        this(new SparseMatrixFactory());
    }

    public OpenShortCircuitProvider(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    @Override
    public String getName() {
        return "OpenShortCircuit";
    }

    @Override
    public String getVersion() {
        return "0.1";
    }

    @Override
    public CompletableFuture<ShortCircuitAnalysisResult> run(Network network, List<Fault> faults, ShortCircuitParameters parameters, ComputationManager computationManager, List<FaultParameters> faultParameters) {

        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);
        checkShortCircuitParametersConsistency(network, parameters);
        Stopwatch stopwatch = Stopwatch.createStarted();

        // building of fault lists
        List<ShortCircuitFault> faultsList = new ArrayList<>();
        Map<ShortCircuitFault, Fault> scFaultToFault = new HashMap<>(); // for now, we use this map to get the correspondence between short circuit provider and internal modeling of fault

        Pair<Boolean, Boolean> faultTypes = buildFaultLists(network, faults, faultsList, scFaultToFault, parameters);
        boolean existBalancedFaults = faultTypes.getKey();
        boolean existUnbalancedFaults = faultTypes.getValue();

        // Selective or Systematic short circuit analysis
        ShortCircuitEngineParameters.AnalysisType at = ShortCircuitEngineParameters.AnalysisType.SELECTIVE;

        ShortCircuitNorm shortCircuitNorm = new ShortCircuitNormNone();

        ShortCircuitEngineParameters scbParameters = new ShortCircuitEngineParameters(matrixFactory, at, faultsList, parameters, shortCircuitNorm);

        // lists to store the results
        List<FaultResult> faultResults = new ArrayList<>();

        if (existBalancedFaults) {
            runBalancedAnalysis(network, scbParameters, scFaultToFault, faultResults);
        }

        if (existUnbalancedFaults) {
            runUnbalancedAnalysis(network, scbParameters, scFaultToFault, faultResults);
        }

        LOGGER.info("Short circuit calculation done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return CompletableFuture.completedFuture(new ShortCircuitAnalysisResult(faultResults));
    }

    public void runUnbalancedAnalysis(Network network, ShortCircuitEngineParameters scbParameters, Map<ShortCircuitFault, Fault> scFaultToFault, List<FaultResult> faultResults) {
        ShortCircuitUnbalancedEngine scuEngine = new ShortCircuitUnbalancedEngine(network, scbParameters);
        scuEngine.run();

        // the results per faults might be inconsistent if many buses per voltage level
        // TODO : see how this could be improved by allowing results per electrical bus on the short circuit provider
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> scResult : scuEngine.resultsPerFault.entrySet()) {
            ShortCircuitFault scFault = scResult.getKey();

            double iccMagnitude = scResult.getValue().getIk().abs() * 1000.; // kA to A conversion
            double voltage = scResult.getValue().getVd().abs();

            Fault fault = scFaultToFault.get(scFault);

            List<FeederResult> feederResults = new ArrayList<>();
            List<LimitViolation> limitViolations = new ArrayList<>();
            List<ShortCircuitBusResults> busResults = new ArrayList<>();
            MagnitudeFaultResult magnitudeFaultResult = new MagnitudeFaultResult(fault, 0., feederResults, limitViolations, iccMagnitude, voltage, busResults, Duration.ZERO, FaultResult.Status.SUCCESS);
            faultResults.add(magnitudeFaultResult);
        }
    }

    public void runBalancedAnalysis(Network network, ShortCircuitEngineParameters scbParameters, Map<ShortCircuitFault, Fault> scFaultToFault, List<FaultResult> faultResults) {
        ShortCircuitBalancedEngine scbEngine = new ShortCircuitBalancedEngine(network, scbParameters);
        scbEngine.run();

        // the results per faults might be inconsistent if many buses per voltage level
        // TODO : see how this could be improved by allowing results per electrical bus on the short circuit provider
        for (Map.Entry<ShortCircuitFault, ShortCircuitResult> scFaultResult : scbEngine.resultsPerFault.entrySet()) {
            ShortCircuitFault scFault = scFaultResult.getKey();
            ShortCircuitResult scResult = scFaultResult.getValue();
            Fault fault = scFaultToFault.get(scFault);

            double iccMagnitude = scResult.getIk().abs() * 1000.; // kA to A conversion
            double voltage = scResult.getVd().abs() * scResult.getLfBus().getNominalV();

            List<FeederResult> feederResultsProvider = new ArrayList<>();
            fillFeederResults(network, scbParameters, feederResultsProvider, scResult);

            List<LimitViolation> limitViolations = new ArrayList<>();

            List<ShortCircuitBusResults> busResults = new ArrayList<>();
            if (scbParameters.isVoltageUpdate()) {
                fillBusResults(network, scbEngine.getFirstLfNetwork(), busResults, scResult, scbEngine.getInitialVoltages(),
                        scbParameters.getMinVoltageDropProportional(), scbParameters.getInnerZoneBusIds());
            }
            MagnitudeFaultResult magnitudeFaultResult = new MagnitudeFaultResult(fault, 0., feederResultsProvider, limitViolations, iccMagnitude, voltage, busResults, Duration.ZERO, FaultResult.Status.SUCCESS);
            faultResults.add(magnitudeFaultResult);
        }
    }

    private void fillFeederResults(Network network, ShortCircuitEngineParameters scbParameters, List<FeederResult> feederResultsProvider, ShortCircuitResult scResult) {
        for (Map.Entry<LfBus, FeedersAtBusResult> busAndFeedersAtBusResult : scResult.getFeedersResultDirect().entrySet()) {
            LfBus lfBus = busAndFeedersAtBusResult.getKey();
            FeedersAtBusResult feedersAtBusResult = busAndFeedersAtBusResult.getValue();
            for (com.powsybl.sc.util.FeederResult feederResult : feedersAtBusResult.getBusFeedersResult()) {

                // 1000: kA to A
                double unitToAmpere = SB * 1000 / Math.sqrt(3) / lfBus.getNominalV();
                Complex iInitial = Complex.ZERO;
                if (scbParameters.getVoltageProfileType() == InitialVoltageProfileMode.PREVIOUS_VALUE) {
                    iInitial = feederResult.getFeeder().getInitialCurrentContribution(network).multiply(unitToAmpere);
                    if (iInitial.isNaN() || iInitial.isInfinite()) {
                        LOGGER.warn("Initial current for feeder '{}' on bus '{}' is NaN/Infinite, defaulting to 0.",
                                feederResult.getFeeder().getId(), lfBus.getId());
                        iInitial = Complex.ZERO;
                    }
                }

                Complex iCont = feederResult.getIContribution().multiply(unitToAmpere);
                Complex iTotal;
                if (feederResult.getFeeder().getFeederType() == Feeder.FeederType.BRANCH) {
                    iTotal = iInitial.add(iCont);
                } else {
                    iTotal = iInitial.subtract(iCont);
                }

                double magnitude = iTotal.abs();
                String feederId = feederResult.getFeeder().getId();
                MagnitudeFeederResult magnitudeFeederResult = new MagnitudeFeederResult(feederId, magnitude, feederResult.getFeeder().getSide());
                feederResultsProvider.add(magnitudeFeederResult);
            }
        }
    }

    private void fillBusResults(Network network, LfNetwork lfNetwork, List<ShortCircuitBusResults> busResults, ShortCircuitResult scResult, List<Complex> initialVoltages, double minVoltDropProportional, Set<String> innerZonBusIds) {
        List<FortescueValue> dVoltages = scResult.getBusNum2Dv();
        for (LfBus lfBus : lfNetwork.getBuses()) {
            if (isLfBusInInnerZone(lfBus, network, innerZonBusIds)) {
                int num = lfBus.getNum();
                Complex initialVpu = initialVoltages.get(num);
                Complex dVpu = polar2Complex(dVoltages.get(num).getPositiveMagnitude(), dVoltages.get(num).getPositiveAngle());
                double voltageDropProportional = dVpu.abs() / initialVpu.abs();
                Complex finalVpu = initialVpu.add(dVpu);

                if (voltageDropProportional >= minVoltDropProportional) {
                    String voltLevelId = lfBus.getVoltageLevelId();
                    String busId = lfBus.getId();
                    double nominalV = lfBus.getNominalV();
                    double initialV = nominalV * initialVpu.abs();
                    double finalV = nominalV * finalVpu.abs();
                    MagnitudeShortCircuitBusResults result = new MagnitudeShortCircuitBusResults(voltLevelId, busId, initialV, finalV, voltageDropProportional);
                    busResults.add(result);
                }
            }
        }
    }

    public Pair<Boolean, Boolean> buildFaultLists(Network network, List<Fault> faults, List<ShortCircuitFault> balancedFaultsList,
                                                  Map<ShortCircuitFault, Fault> scFaultToFault, ShortCircuitParameters parameters) {
        boolean existBalancedFaults = false;
        boolean existUnbalancedFaults = false;

        for (Fault fault : faults) {
            String elementId = fault.getElementId();
            Bus bus = network.getBusBreakerView().getBus(elementId);

            Set<String> innerZoneBusIds = parameters.getExtension(OpenShortCircuitParameters.class).getInnerZoneBusIds();

            if (!innerZoneBusIds.isEmpty() && !innerZoneBusIds.contains(bus.getId())) {
                continue;
            }
            ShortCircuitFault.ShortCircuitType scType = ShortCircuitFault.ShortCircuitType.TRIPHASED_GROUND; // Default type

            if (fault.getFaultType() == Fault.FaultType.SINGLE_PHASE) {
                existUnbalancedFaults = true;
                scType = ShortCircuitFault.ShortCircuitType.MONOPHASED;
            } else if (fault.getFaultType() == Fault.FaultType.THREE_PHASE) {
                existBalancedFaults = true;
            } else {
                LOGGER.warn("Short circuit of unknown type, fault: {} is ignored", fault.getId());
                continue;
            }

            fillFaultLists(fault, network, scType, balancedFaultsList, scFaultToFault);
        }

        return new Pair<>(existBalancedFaults, existUnbalancedFaults);
    }

    private void checkForBranchNominalVoltageCoherency(Network network, String busId1, String busId2) {
        // TODO: to remove when different nominal voltages at branch sides are correctly handled
        Bus bus1 = network.getBusBreakerView().getBus(busId1);
        Bus bus2 = network.getBusBreakerView().getBus(busId2);

        double nominalV1 = bus1.getVoltageLevel().getNominalV();
        double nominalV2 = bus2.getVoltageLevel().getNominalV();

        if (nominalV1 != nominalV2) {
            LOGGER.warn("Default on a Branch connecting two Buses of different nominal voltages: A fictitious ideal transformer is added at side 1 of the branch");
        }
    }

    private void fillFaultLists(Fault fault, Network network, ShortCircuitFault.ShortCircuitType scType, List<ShortCircuitFault> balancedFaultsList, Map<ShortCircuitFault, Fault> scFaultToFault) {
        double rFault = fault.getRToGround();
        double xFault = fault.getXToGround();
        Complex zFaultToGround;
        if (fault.getConnectionType() == Fault.ConnectionType.SERIES) {
            // Zf = r + jx
            zFaultToGround = new Complex(rFault, xFault);
        } else {
            // Zf = (r*jx)/(r+jx) for ConnectionType.PARALLEL
            zFaultToGround = new Complex(rFault, 0)
                    .multiply(new Complex(0, xFault))
                    .divide(new Complex(rFault, xFault));
        }

        ShortCircuitFault sc;
        // TODO : see how to get lfBus from iidm Bus
        String elementId = fault.getElementId();

        if (fault instanceof BranchFault branchFault) { // Branch fault
            Pair<String, String> branchBusIds = getBranchBusIdsFromElementId(elementId, fault.getId(), network);

            if (branchBusIds == null) {
                return;
            }

            CalculationLocation location = new CalculationLocation(branchBusIds.getKey(), branchBusIds.getValue(), branchFault.getProportionalLocation());
            // Convert fault impedance from Ohm into PerUnit
            Complex zFaultToGroundPerUnit = zFaultToGround.divide(new Complex(getZPerUnitForBranch(fault, network), 0));
            ShortCircuitFaultImpedance scz = new ShortCircuitFaultImpedance(zFaultToGroundPerUnit);
            sc = new ShortCircuitFault(location, branchFault.getId(), elementId, scz, scType);
            checkForBranchNominalVoltageCoherency(network, branchBusIds.getKey(), branchBusIds.getValue());
        } else { //Bus fault
            String busId = getBusId(elementId, fault.getId(), network);

            if (busId == null) {
                return;
            }
            // Convert fault impedance from Ohm into PerUnit
            Complex zFaultToGroundPerUnit = zFaultToGround.divide(new Complex(getZPerUnitForBus(fault, network), 0));
            ShortCircuitFaultImpedance scz = new ShortCircuitFaultImpedance(zFaultToGroundPerUnit);
            sc = new ShortCircuitFault(busId, fault.getId(), elementId, scz, scType);
        }

        balancedFaultsList.add(sc);
        scFaultToFault.put(sc, fault);
    }

    private static Pair<String, String> getBranchBusIdsFromElementId(String elementId, String faultId, Network network) {
        Identifiable<?> element = network.getIdentifiable(elementId);

        if (element == null) {
            LOGGER.warn("Element '{}' not found in the network. Fault '{}' is ignored.", elementId, faultId);
            return null;
        }

        if (!(element instanceof Branch<?> branch)) {
            LOGGER.warn("Element '{}' is not a branch. Fault '{}' is ignored.", elementId, faultId);
            return null;
        }

        Bus bus1 = branch.getTerminal1().getBusBreakerView().getBus();
        Bus bus2 = branch.getTerminal2().getBusBreakerView().getBus();

        if (bus1 == null) {
            LOGGER.warn(
                    "Terminal1 of branch '{}' is not connected to a bus. Fault '{}' is ignored.",
                    elementId, faultId);
            return null;
        }

        if (bus2 == null) {
            LOGGER.warn(
                    "Terminal2 of branch '{}' is not connected to a bus. Fault '{}' is ignored.",
                    elementId, faultId);
            return null;
        }

        return new Pair<>(bus1.getId(), bus2.getId());
    }

    private static String getBusId(String elementId, String faultId, Network network) {
        Identifiable<?> element = network.getIdentifiable(elementId);

        if (element == null) {
            LOGGER.warn("Element '{}' not found in network. Fault '{}' is ignored.", elementId, faultId);
            return null;
        }

        if (element instanceof Bus bus) {
            return bus.getId();
        }

        LOGGER.warn("'{}' is not the id of a bus. Fault '{}' is ignored.", elementId, faultId);
        return null;
    }

    private double getZPerUnitForBus(Fault fault, Network network) {
        String elementId = fault.getElementId();
        Bus bus = network.getBusBreakerView().getBus(elementId);
        double vNomVl = bus.getVoltageLevel().getNominalV();
        return vNomVl * vNomVl / SB;
    }

    private double getZPerUnitForBranch(Fault fault, Network network) {
        String elementId = fault.getElementId();
        Branch<?> branch = network.getBranch(elementId);
        double vNomVl2 = branch.getTerminal2().getVoltageLevel().getNominalV();
        return vNomVl2 * vNomVl2 / SB; // Fix me: What if vNomVl2 != vNomVl1?
    }

    private static void checkShortCircuitParametersConsistency(Network network, ShortCircuitParameters parameters) {
        getOrCreateOpenScParameters(parameters);
        cleanInnerZone(network, parameters);
        List<String> inconsistentOptions = findInconsistentOptions(parameters);
        if (!inconsistentOptions.isEmpty()) {
            LOGGER.warn("Initial voltage profile mode is PREVIOUS_VALUE, but {} {}: the initial voltages come from a load flow "
                            + "computed with the complete network model, so they may not be consistent with the admittance matrix.",
                    inconsistentOptions, inconsistentOptions.size() > 1 ? "are set" : "is set");
        }
    }

    static void getOrCreateOpenScParameters(ShortCircuitParameters parameters) {
        if (parameters.getExtension(OpenShortCircuitParameters.class) == null) {
            parameters.addExtension(OpenShortCircuitParameters.class, new OpenShortCircuitParameters());
        }
    }

    private static void cleanInnerZone(Network network, ShortCircuitParameters parameters) {
        OpenShortCircuitParameters openScParameters = parameters.getExtension(OpenShortCircuitParameters.class);
        Set<String> innerZoneBusIds = openScParameters.getInnerZoneBusIds();
        if (innerZoneBusIds.isEmpty()) {
            return;
        }

        Map<Boolean, Set<String>> busIdsByValidity = innerZoneBusIds.stream()
                .collect(Collectors.partitioningBy(busId -> network.getBusBreakerView().getBus(busId) != null, Collectors.toSet()));
        Set<String> validBusIds = busIdsByValidity.get(true);
        Set<String> invalidBusIds = busIdsByValidity.get(false);

        if (invalidBusIds.isEmpty()) {
            return;
        }
        if (validBusIds.isEmpty()) {
            throw new PowsyblException("None of the inner zone buses exist in the network: " + invalidBusIds);
        }
        LOGGER.warn("Inner zone buses {} not found in the network, they are ignored", invalidBusIds);
        openScParameters.setInnerZoneBusIds(validBusIds);
    }

    static List<String> findInconsistentOptions(ShortCircuitParameters parameters) {
        if (parameters.getInitialVoltageProfileMode() != InitialVoltageProfileMode.PREVIOUS_VALUE) {
            return List.of();
        }
        OpenShortCircuitParameters openScParameters = parameters.getExtension(OpenShortCircuitParameters.class);
        List<String> inconsistentOptions = new ArrayList<>();
        if (!parameters.isWithLoads()) {
            inconsistentOptions.add("withLoads=false");
        }
        if (!parameters.isWithShuntCompensators()) {
            inconsistentOptions.add("withShuntCompensators=false");
        }
        if (parameters.isWithNeutralPosition()) {
            inconsistentOptions.add("withNeutralPosition=true");
        }
        if (!openScParameters.isWithResistances()) {
            inconsistentOptions.add("withResistances=false");
        }
        if (!openScParameters.isWithCapacities()) {
            inconsistentOptions.add("withCapacities=false");
        }
        return inconsistentOptions;
    }
}
