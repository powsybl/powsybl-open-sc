package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Generator;

public class GeneratorFortescue2 extends AbstractExtension<Generator> {

    public static final String NAME = "generatorFortescue";

    public enum GeneratorType {
        UNKNOWN,
        ROTATING_MACHINE,
        FEEDER;
    }

    private final GeneratorType generatorType;

    @Override
    public String getName() {
        return NAME;
    }

    public GeneratorFortescue2(Generator generator, GeneratorType generatorType) {
        super(generator);
        this.generatorType = generatorType;

    }

    public GeneratorType getGeneratorType() {
        return generatorType;
    }
}
