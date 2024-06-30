package com.powsybl.sc.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Generator;

@AutoService(ExtensionAdderProvider.class)
public class GeneratorFortescueAdderImplProvider2 implements ExtensionAdderProvider<Generator, GeneratorFortescue2, GeneratorFortescueAdder2> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return GeneratorFortescue2.NAME;
    }

    @Override
    public Class<GeneratorFortescueAdder2> getAdderClass() {
        return GeneratorFortescueAdder2.class;
    }

    @Override
    public GeneratorFortescueAdder2 newAdder(Generator generator) {
        return new GeneratorFortescueAdder2(generator);
    }
}
