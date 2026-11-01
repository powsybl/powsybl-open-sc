package com.powsybl.sc.util;

public enum AdmittanceType {
    ADM_INJ, // all external nodal injections that does not come from branches are considered as current injectors (including shunts elements)
    ADM_SHUNT, // all external  nodal injections that does not come from branches are considered as current injectors (but not shunt elements)
    ADM_ADMIT, // all external  nodal injections are transformed into passive shunt elements included in the Y matrix (then [Ie] should be [0])
    ADM_THEVENIN, // used to compute the Zth Thevenin Equivalent: shunts remain shunts, synchronous machines are transformed into X" equivalent shunts, remaining injections are transformed into passive shunt elements included in the Y matrix
    ADM_THEVENIN_HOMOPOLAR // used to compute the homopolar admittance matrix for unbalanced short circuits
}
