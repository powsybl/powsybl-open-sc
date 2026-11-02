![PowSyBl Logo](_static/logos/logo_lfe_powsybl.svg)
# Powsybl Open Short-Circuit

## PowSyBl vs PowSyBl Open Short-Circuit

PowSyBl Open Short-Circuit provides an open-source implementation of the
[ShortCircuitAnalysis API from PowSyBl Core](inv:powsyblcore:*:*#simulation/shortcircuit/index).

It is written in Java and relies on [PowSyBl Open Load Flow](inv:openloadflow:*:*#index) for the network model
(buses, branches, generators, loads) and for the optional pre-fault load flow. The admittance matrix is factorized with a
sparse LU decomposition based on [KLU](http://faculty.cse.tamu.edu/davis/suitesparse.html) by default.

### Features

- Balanced faults (three-phase) and unbalanced faults (single-phase to ground) on buses and on branches,
  with a fault impedance to ground.
- Phase-to-phase faults, phase-to-phase to ground faults and systematic analysis on all the buses of the network,
  available through the [engines](advanced_programming/using_the_engines.md).
- Subtransient and transient studies.
- Direct, inverse and homopolar (zero-sequence) network models, using the Fortescue transformation for unbalanced faults.
- Fault current and voltage, feeder contributions and post-fault bus voltages.
- Modelling options for the admittance matrix: branch resistances and capacitances, loads, shunt compensators,
  tap changers at neutral position, and per-zone generator selection.
- Optional correction factors from the IEC 60909 standard.
- Import of short-circuit data from CGMES.

```{toctree}
---
maxdepth: 2
hidden: true
---
shortcircuit/index.md
detailed_models_description/index.md
advanced_programming/index.md
```
