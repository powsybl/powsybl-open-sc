# Parameters

The short-circuit analysis is configured with the generic `ShortCircuitParameters` of PowSyBl Core, described in the
[PowSyBl Core documentation](inv:powsyblcore:*:*#simulation/shortcircuit/index), and with the OpenSC specific
`OpenShortCircuitParameters` extension.

If the extension is not set, it is created with its default values when the analysis starts.

## Generic parameters

| Parameter | Default | Used | Effect in OpenSC |
|---|---|---|---|
| `studyType` | `TRANSIENT` | yes | `SUB_TRANSIENT` uses the subtransient impedance of the generators, other values use the transient impedance. |
| `withVoltageResult` | `true` | yes | Computes the post-fault voltage of every bus, and returns the bus results. |
| `minVoltageDropProportionalThreshold` | `0` | yes | Bus results are only returned for buses whose voltage drop, relative to the initial voltage, is at least this value. |
| `withLoads` | `true` | yes | If `false`, loads are not modelled in the admittance matrix. |
| `withShuntCompensators` | `true` | yes | If `false`, shunt compensators are not modelled in the admittance matrix. |
| `withNeutralPosition` | `false` | yes | If `true`, the ratio and impedance of transformers are taken at the neutral tap position. |
| `initialVoltageProfileMode` | `NOMINAL` | yes | `NOMINAL`: 1 pu on every bus. `CONFIGURED`: voltage given by `voltageRanges`. `PREVIOUS_VALUE`: voltage of the network state. |
| `voltageRanges` | none | yes | Used with `initialVoltageProfileMode = CONFIGURED`. A bus outside every range is initialized at 1 pu. |
| `withFeederResult` | `true` | no | Feeder results depend on `withVoltageResult`. |
| `withLimitViolations` | `true` | no | Limit violations are never computed. |
| `withFortescueResult` | `true` | no | Results are always given as magnitudes. |
| `withVSCConverterStations` | `true` | no | Ignored. |
| `subTransientCoefficient` | `0.7` | no | Ignored: the subtransient reactance comes from the generator extensions, it is never derived from the transient one. |
| `detailedReport` | `true` | no | Ignored. |

The default values are those of PowSyBl Core.

## OpenShortCircuitParameters

| Parameter | Default | Effect |
|---|---|---|
| `withResistances` | `true` | If `false`, the series resistance of every branch is set to 0 in the admittance matrix. |
| `withCapacities` | `true` | If `false`, the shunt admittances at both ends of every branch (G1, B1, G2, B2) are ignored. |
| `innerZoneBusIds` | empty | Ids of the bus-breaker view buses that form the inner zone. If empty, the whole network is the inner zone. |
| `innerZoneGeneratorSelectionMode` | `ALL` | Selection of the generators of the inner zone: `ALL` or `ABOVE_POWER_THRESHOLD`. |
| `outerZoneGeneratorSelectionMode` | `ABOVE_POWER_THRESHOLD` | Selection of the generators outside the inner zone. |
| `innerZoneGeneratorPowerThreshold` | `0` (MW) | With `ABOVE_POWER_THRESHOLD`, a generator of the inner zone contributes only if its \|P\| is strictly above this value. |
| `outerZoneGeneratorPowerThreshold` | `0` (MW) | Same threshold, for the generators outside the inner zone. |
| `matrixFactory` | `SparseMatrixFactory` | Matrix implementation used to build and factorize the admittance matrix. `DenseMatrixFactory` avoids the native solver, but should only be used on small networks. |
| `shortCircuitAnalysisType` | `SELECTIVE` | `SELECTIVE` computes the faults given in input, `SYSTEMATIC` computes a fault on every bus of the network. |
| `loadFlowParameters` | `new LoadFlowParameters()` | Load flow parameters used to build the network model, and to run the load flow when one is needed. |

### Inner zone and generator selection

The inner zone is the part of the network that is studied. It changes the analysis in three ways:
- only the faults located on a bus of the inner zone are computed;
- bus results are only returned for buses of the inner zone;
- the generators of the inner zone and of the outer zone can be selected with different rules.

In `ABOVE_POWER_THRESHOLD` mode, a generator is considered as running, and contributes to the fault current, only if
the absolute value of its active power is strictly above the threshold. Synchronous condensers always contribute,
whatever their active power. Generators that are not selected are removed from the admittance matrix.

## Validation

- Inner zone bus ids that don't exist in the network are ignored, with a warning. If none of them exist, the analysis
  fails with a `PowsyblException`.
- Power thresholds must be positive. A negative or NaN value throws an `IllegalArgumentException`.
- If no generator contributes to the fault current once the selection is applied, the analysis fails with a
  `PowsyblException`.
- With `initialVoltageProfileMode = PREVIOUS_VALUE`, a warning is logged if `withLoads = false`,
  `withShuntCompensators = false`, `withNeutralPosition = true`, `withResistances = false` or `withCapacities = false`:
  the initial voltages then come from a network model that is different from the admittance matrix.
