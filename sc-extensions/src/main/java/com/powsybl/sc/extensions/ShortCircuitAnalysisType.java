package com.powsybl.sc.extensions;

public enum ShortCircuitAnalysisType {
    SELECTIVE, // short circuit analysis for List<ShortCircuitFault> faults in input
    SYSTEMATIC // short circuit analysis for all buses of input grid
}
