package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Generator;

import static com.powsybl.sc.extensions.FortescueConstants.*;

public class GeneratorFortescueAdder2 extends AbstractExtensionAdder<Generator, GeneratorFortescue2> {

    private GeneratorFortescue2.GeneratorType generatorType = DEFAULT_GENERATOR_FORTESCUE_TYPE;

    public GeneratorFortescueAdder2(Generator generator) {
        super(generator);
    }

    @Override
    public Class<? super GeneratorFortescue2> getExtensionClass() {
        return GeneratorFortescue2.class;
    }

    @Override
    protected GeneratorFortescue2 createExtension(Generator generator) {
        return new GeneratorFortescue2(generator, generatorType);
    }

    public GeneratorFortescueAdder2 withGeneratorType(GeneratorFortescue2.GeneratorType generatorType) {
        this.generatorType = generatorType;
        return this;
    }
}
