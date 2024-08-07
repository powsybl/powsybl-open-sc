/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.TwoWindingsTransformer;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class TwoWindingsTransformerFortescuePartOfGu extends AbstractExtension<TwoWindingsTransformer> {

    public static final String NAME = "twoWindingsTransformerShortCircuit";

    private boolean isPartOfGeneratingUnit;

    @Override
    public String getName() {
        return NAME;
    }

    public TwoWindingsTransformerFortescuePartOfGu(TwoWindingsTransformer twt, boolean isPartOfGeneratingUnit) {
        super(twt);
        this.isPartOfGeneratingUnit = isPartOfGeneratingUnit;
    }

    public void setPartOfGeneratingUnit(boolean partOfGeneratingUnit) {
        isPartOfGeneratingUnit = partOfGeneratingUnit;
    }

    public boolean isPartOfGeneratingUnit() {
        return isPartOfGeneratingUnit;
    }

}
