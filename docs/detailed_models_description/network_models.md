# Network models

The network is loaded with the [Open Load Flow network model](inv:openloadflow:*:*#index): buses of the bus view,
branches with a pi model, generators, loads and shunts. All the values are then expressed in per-unit, with a base power
$S_B = 100$ MVA and the nominal voltage of each bus as base voltage.

The short-circuit data of each equipment is read from IIDM extensions. When an extension is missing, a default value is
used.

## Generators

| Data | Source | Default |
|---|---|---|
| Transient reactance $X'_d$ (Ohm) | `GeneratorShortCircuit.directTransX` | 20 Ohm |
| Subtransient reactance $X''_d$ (Ohm) | `GeneratorShortCircuit.directSubtransX` | 20 Ohm |
| Step-up transformer reactance (Ohm) | `GeneratorShortCircuit.stepUpTransformerX` | 0 Ohm |
| Transient and subtransient resistances (Ohm) | `GeneratorShortCircuit2.transRd`, `GeneratorShortCircuit2.subTransRd` | 0 Ohm |
| Grounding and homopolar impedance | `GeneratorFortescue.grounded`, `rz`, `xz` | $Z_0 = R''_d + jX''_d$ |

In the direct and inverse sequences, a generator is modelled as a shunt admittance at its bus:

$$
Y_{gen} = \frac{1}{K_G \cdot (Z_d + Z_{step-up})}
$$

converted to per-unit with the nominal voltage of the bus, where $Z_d$ is the subtransient impedance for a `SUB_TRANSIENT` study and the transient impedance otherwise, and $K_G$ is
the correction factor of the norm (1 without norm). In the homopolar sequence, a generator is only modelled if it is
grounded, with its homopolar impedance.

Only the generators kept by the [generator selection](../shortcircuit/parameters.md#inner-zone-and-generator-selection)
are modelled.

## Loads

If a load has a `LoadShortCircuit` extension, its impedance comes from the extension: a constant impedance load, or an
asynchronous machine defined by its rated data. Otherwise, the load is modelled as a constant impedance computed from
its active and reactive power at nominal voltage:

$$
Z_{load} = \frac{U_{nom}^2}{|S|^2} \cdot (P_0 + jQ_0)
$$

All the loads of a bus are aggregated into one shunt admittance. Loads are not modelled if `withLoads` is `false`.

## Shunt compensators

Shunt compensators are modelled with their susceptance, if `withShuntCompensators` is `true`.

## Lines

In the direct and inverse sequences, a line is modelled with its pi model. The series resistance is set to 0 if
`withResistances` is `false`, and the shunt admittances are removed if `withCapacities` is `false`.

In the homopolar sequence, the series impedance comes from the `LineFortescue` extension (`rz`, `xz`). Without it, the
direct-sequence impedance is used.

## Transformers

Two and three windings transformers are modelled with their pi model, at the current tap position, or at the neutral
position if `withNeutralPosition` is `true`.

The homopolar model comes from the `TwoWindingsTransformerFortescue` and `ThreeWindingsTransformerFortescue`
extensions: homopolar impedances, winding connection types and grounding, and free fluxes. Without them, the
direct-sequence impedance and the default connection types of the `FortescueConstants` of PowSyBl Core are used.

## 20 Hz equivalents

A second admittance matrix is built with the reactances scaled to 20 Hz ($X_{20Hz} = \frac{20}{50} X$). It gives the
Thevenin impedance at 20 Hz of each faulted bus. It is used to compute the peak current factor $\kappa$ with the
equivalent frequency method (method C) of IEC 60909. The peak current is available in the engine results
(`ShortCircuitResult.getIpeakc()`), but is not returned by the `ShortCircuitAnalysis` API yet.

## IEC 60909 correction factors

When the analysis runs with `ShortCircuitNormIec`:
- the voltage factor $c_{max}$ depends on the nominal voltage of the faulted bus: 1.05 or 1.10 below 1 kV, 1.10 above;
- the impedances of generators and transformers are multiplied by the correction factors $K_G$ and $K_T$, computed from
  their rated data.

Without norm (`ShortCircuitNormNone`, the default), $c_{max} = 1$ and all the correction factors are equal to 1.

## CGMES import

The `powsybl-open-sc-cgmes` module adds a CGMES import post-processor named `ShortCircuit`. It reads the short-circuit
data of the `SynchronousMachine` and `ExternalNetworkInjection` objects, and creates the generator extensions described
above.
