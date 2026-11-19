# Using the engines directly

The engines give access to features that are not available through the `ShortCircuitAnalysis` API yet:
phase-to-phase faults, phase-to-phase to ground faults, faults on two buses at the same time, systematic analysis, and
the detailed per-unit results (sequence currents and voltages, Thevenin impedances, peak current).

## Running an engine

```java
ShortCircuitParameters parameters = new ShortCircuitParameters()
        .setStudyType(StudyType.SUB_TRANSIENT)
        .setWithVoltageResult(true);
parameters.addExtension(OpenShortCircuitParameters.class, new OpenShortCircuitParameters());

// Phase-to-phase fault on bus "B1", without fault impedance
ShortCircuitFault fault = new ShortCircuitFault("B1", "F1", "B1",
        new ShortCircuitFaultImpedance(Complex.ZERO), ShortCircuitFault.ShortCircuitType.BIPHASED);

ShortCircuitUnbalancedEngine engine = new ShortCircuitUnbalancedEngine(network, parameters,
        List.of(fault), new ShortCircuitNormNone());
engine.run();

ShortCircuitResult result = engine.getResultsPerFault().get(fault);
Complex ik = result.getIk(); // in kA
```

## Fault types

| `ShortCircuitType` | Engine | Fault |
|---|---|---|
| `TRIPHASED_GROUND` | `ShortCircuitBalancedEngine` | Three-phase fault |
| `MONOPHASED` | `ShortCircuitUnbalancedEngine` | Single-phase to ground fault |
| `BIPHASED` | `ShortCircuitUnbalancedEngine` | Phase-to-phase fault |
| `BIPHASED_GROUND` | `ShortCircuitUnbalancedEngine` | Phase-to-phase to ground fault |
| `BIPHASED_COMMON_SUPPORT` | `ShortCircuitUnbalancedEngine` | Two phases of two different buses in contact (`C1_A2`, `C1_B2` or `C1_C2`) |

A fault on two buses is created with the constructor that takes a second bus and a `ShortCircuitBiphasedType`.

## Systematic analysis

With `OpenShortCircuitParameters.setShortCircuitAnalysisType(ShortCircuitAnalysisType.SYSTEMATIC)`, the engine ignores
the faults given in input and computes a fault on every bus of the bus-breaker view. The voltage results are disabled in
this mode.

## Thevenin equivalent

`TheveninEquivalent` computes the Thevenin impedances of a list of buses, without fault calculation:

```java
TheveninEquivalent thevenin = new TheveninEquivalent(network, List.of(new CalculationLocation("B1")), parameters);
thevenin.run();
```
