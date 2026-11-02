# Faults and results

## Faults

| PowSyBl fault | Fault type | Computation |
|---|---|---|
| `BusFault` | `THREE_PHASE` | Balanced three-phase fault to ground on a bus of the bus-breaker view. |
| `BusFault` | `SINGLE_PHASE` | Unbalanced single-phase fault to ground on a bus of the bus-breaker view. |
| `BranchFault` | `THREE_PHASE` or `SINGLE_PHASE` | Fault located on a branch, at `proportionalLocation` percent (0 to 100) of its length, starting from side 1. |

Faults of any other type, or on an element that is not a bus or a branch, are ignored with a warning.

The fault impedance to ground is built from `rToGround` and `xToGround`, in Ohm:
- `SERIES` connection: $Z_f = r + jx$
- `PARALLEL` connection: $Z_f = \frac{r \cdot jx}{r + jx}$

It is converted to per-unit with the nominal voltage of the faulted bus.

Phase-to-phase faults, phase-to-phase to ground faults and faults on two buses at the same time (biphased common
support) are implemented in the engines, but are not yet reachable through the `ShortCircuitAnalysis` API.
See [Advanced programming](../advanced_programming/index.md).

## Results

Each fault gives a `MagnitudeFaultResult`.

| Result | Unit | Content |
|---|---|---|
| `current` | A | Magnitude of the initial symmetrical short-circuit current $I''_k$. |
| `voltage` | kV | Magnitude of the direct-sequence voltage at the fault location. |
| `feederResults` | A | Current of each feeder (branch side, generator, load, shunt) of every bus of the network. Only for three-phase faults, and only if `withVoltageResult` is `true`, because the feeder currents are computed from the bus voltages. |
| `shortCircuitBusResults` | kV | Initial and final voltage, and relative voltage drop, of the buses of the inner zone. Only if `withVoltageResult` is `true`, and only for the buses whose drop reaches `minVoltageDropProportionalThreshold`. |

For unbalanced faults, the bus results give the direct-sequence voltage of each bus, not the voltage of the faulted
phase.

When `initialVoltageProfileMode` is `PREVIOUS_VALUE`, the feeder currents include the pre-fault current of the feeder.
