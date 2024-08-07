/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.TwoWindingsTransformer;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class TwoWindingsTransformerFortescuePartOfGuAdder extends AbstractExtensionAdder<TwoWindingsTransformer, TwoWindingsTransformerFortescuePartOfGu> {

    private boolean isPartOfGeneratingUnit = false;

    public TwoWindingsTransformerFortescuePartOfGuAdder(TwoWindingsTransformer twt) {
        super(twt);
    }

    @Override
    public Class<? super TwoWindingsTransformerFortescuePartOfGu> getExtensionClass() {
        return TwoWindingsTransformerFortescuePartOfGu.class;
    }

    @Override
    protected TwoWindingsTransformerFortescuePartOfGu createExtension(TwoWindingsTransformer twt) {
        return new TwoWindingsTransformerFortescuePartOfGu(twt, isPartOfGeneratingUnit);
    }

    public TwoWindingsTransformerFortescuePartOfGuAdder withIsPartOfGeneratingUnit(boolean isPartOfGeneratingUnit) {
        this.isPartOfGeneratingUnit = isPartOfGeneratingUnit;
        return this;
    }
}
