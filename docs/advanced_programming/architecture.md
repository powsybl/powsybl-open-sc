# Architecture

## Modules

| Module | Content |
|---|---|
| `sc-extensions` | IIDM extensions for short-circuit data (`GeneratorShortCircuit2`, `GeneratorFortescueType`, `LoadShortCircuit`), the `OpenShortCircuitParameters` extension and the shared constants. |
| `sc-implementation` | The `ShortCircuitAnalysisProvider` implementation, the engines, the fault calculators and the admittance matrix. |
| `sc-cgmes` | The CGMES import post-processor that creates the short-circuit extensions. |

## Main classes

The `sc-implementation` module has two packages.

`com.powsybl.sc.implementation` contains the analysis:
- `OpenShortCircuitProvider`: entry point of the `ShortCircuitAnalysis` API. It checks the parameters, converts the
  PowSyBl faults into `ShortCircuitFault`, runs the engines and converts their results into `FaultResult`.
- `ShortCircuitBalancedEngine` and `ShortCircuitUnbalancedEngine`, which extend `AbstractShortCircuitEngine`: they load
  the network, solve the linear systems and build one `ShortCircuitResult` per fault.
- `MonophasedShortCircuitCalculator`, `BiphasedShortCircuitCalculator`, `BiphasedGroundShortCircuitCalculator` and
  `BiphasedCommonSupportShortCircuitCalculator`: they compute the sequence currents and voltages of an unbalanced fault
  from the Thevenin impedances.
- `ShortCircuitNorm`, with `ShortCircuitNormNone` and `ShortCircuitNormIec`: voltage factors and correction factors.
- `ShortCircuitResult`: currents, voltages and feeder results of one fault, in per-unit.

`com.powsybl.sc.util` contains the linear algebra:
- `AdmittanceEquationSystem`: builds the equations $I = Y \cdot V$ from the network, with the admittance terms
  `AdmittanceEquationTermX1`, `X2`, `Y1`, `Y2` and `AdmittanceEquationTermShunt`.
- `BranchAdmittance`: admittance of a branch, shared by the admittance matrix and the feeder currents.
- `AdmittanceMatrix`: the matrix built from the equation system, and its LU decomposition.
- `ImpedanceLinearResolution`: computes the Thevenin impedances $Z_{ff}$ and $Z_{kf}$ of the faulted buses.
- `TheveninEquivalent`: computes the Thevenin equivalent of a list of buses, without fault calculation.
- `ShortCircuitHelper`: inner zone, generator selection, initial voltages.
- `extensions`: the short-circuit data attached to the Open Load Flow network (`ScGenerator`, `ScLoad`, `ScLine`,
  `HomopolarModel`, ...), filled by `ShortCircuitExtensions` from the IIDM extensions.

## Data flow

1. `OpenShortCircuitProvider.run` checks the parameters and builds the list of `ShortCircuitFault`.
2. The engine loads the `LfNetwork`, attaches the short-circuit data of each equipment, and computes the initial voltages.
3. `ImpedanceLinearResolution` builds the admittance matrices (direct, and homopolar for unbalanced faults; at 50 Hz and
   20 Hz), factorizes them and extracts the Thevenin impedances.
4. The engine computes the fault currents, and the bus voltages and feeder currents if `withVoltageResult` is `true`.
5. The provider converts the per-unit results into `MagnitudeFaultResult`, in A and kV.

## Adding a parameter

1. Add the field, its default value and its getter and setter to `OpenShortCircuitParameters`. Setters return `this`
   and check their input.
2. Read it where it is needed, through `ShortCircuitHelper.getOrDefaultOpenScParameters(parameters)`.
3. If the option changes the network model, add it to the consistency check of `OpenShortCircuitProvider`, which warns
   when it is used with `PREVIOUS_VALUE` initial voltages.
4. Add tests in `ShortCircuitParametersTest`, and document it in [Parameters](../shortcircuit/parameters.md).
