# Calculation method

## Initial voltages

The pre-fault voltage $V_{init}$ of each bus is given by `initialVoltageProfileMode`: 1 pu, a value per voltage range,
or the voltage of the network state.

## Admittance matrix

The admittance matrix $Y$ links the bus voltages to the injected currents: $I = Y \cdot V$. It is built with the
[network models](network_models.md): the pi model of the branches, and the shunt admittances of the generators, loads and
shunt compensators.

Complex values are written in cartesian coordinates, so each bus has two rows (real and imaginary parts) and two
columns. Two matrices are built:
- the direct-sequence matrix $Y_d$, also used for the inverse sequence;
- the homopolar matrix $Y_0$, only for unbalanced faults.

## Thevenin impedances

The matrix is factorized once with a LU decomposition. For each faulted bus $f$, the column $f$ of the impedance matrix
$Z = Y^{-1}$ is obtained by solving $Y \cdot z = e_f$, where $e_f$ is the unit vector of bus $f$. The inverse of $Y$
is never computed.

The diagonal term $Z_{ff}$ is the Thevenin impedance of the network seen from the fault. The other terms $Z_{kf}$ give
the voltage variation of every bus $k$ when a current is injected at $f$.

## Fault current

For a three-phase fault with a fault impedance $Z_f$:

$$
I_d = \frac{V_{init,f}}{Z_{d,ff} + Z_f}
\qquad
I''_k = c \cdot |I_d| \cdot I_{base}
$$

For a single-phase fault, the direct, inverse and homopolar currents have the same magnitude:

$$
|I_d| = |I_i| = |I_0| = \frac{|V_{init,f}|}{|2 Z_{d,ff} + Z_{0,ff} + 3 Z_f|}
\qquad
I''_{k1} = 3 \cdot c \cdot |I_d| \cdot I_{base}
$$

The fault is modelled on phase c, so the three sequence currents only differ by a rotation of $\pm 120°$.

In both cases, $c$ is the voltage factor of the norm and $I_{base} = \frac{S_B}{\sqrt{3} U_{nom}}$ (in kA).

## Post-fault voltages

When `withVoltageResult` is `true`, the voltage variation of every bus $k$ is computed for each sequence:

$$
\Delta V_{d,k} = -Z_{d,kf} \cdot I_d
\qquad
\Delta V_{i,k} = -Z_{d,kf} \cdot I_i
\qquad
\Delta V_{0,k} = -Z_{0,kf} \cdot I_0
$$

The post-fault direct-sequence voltage is $V_{d,k} = V_{init,k} + \Delta V_{d,k}$, and the inverse and homopolar
voltages are equal to their variation.

## Feeder currents

The current of each branch is computed from the voltage variations at its two ends and the branch admittance:

$$
\begin{bmatrix} \Delta I_1 \\ \Delta I_2 \end{bmatrix}
=
\begin{bmatrix} y_{11} & y_{12} \\ y_{21} & y_{22} \end{bmatrix}
\begin{bmatrix} \Delta V_1 \\ \Delta V_2 \end{bmatrix}
$$

The branch admittance is the same as in the admittance matrix, so it follows the modelling options. The current of
generators, loads and shunts is their admittance multiplied by the voltage variation of their bus.
