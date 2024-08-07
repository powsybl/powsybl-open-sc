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
import com.powsybl.iidm.network.extensions.WindingConnectionType;

import java.util.Objects;

import static com.powsybl.sc.extensions.FortescueConstants.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class TwoWindingsTransformerFortescueAdder extends AbstractExtensionAdder<TwoWindingsTransformer, TwoWindingsTransformerFortescue> {

    private boolean isPartOfGeneratingUnit = false;
    private double coeffRo = DEFAULT_COEFF_RO;
    private double coeffXo = DEFAULT_COEFF_XO;
    private double ro = 0.;
    private double xo = 0.;
    private boolean freeFluxes = DEFAULT_FREE_FLUXES;
    private WindingConnectionType leg1ConnectionType = DEFAULT_LEG1_CONNECTION_TYPE;
    private WindingConnectionType leg2ConnectionType = DEFAULT_LEG2_CONNECTION_TYPE;
    private double r1Ground = 0.;
    private double x1Ground = 0.;
    private double r2Ground = 0.;
    private double x2Ground = 0.;

    public TwoWindingsTransformerFortescueAdder(TwoWindingsTransformer twt) {
        super(twt);
    }

    @Override
    public Class<? super TwoWindingsTransformerFortescue> getExtensionClass() {
        return TwoWindingsTransformerFortescue.class;
    }

    @Override
    protected TwoWindingsTransformerFortescue createExtension(TwoWindingsTransformer twt) {
        return new TwoWindingsTransformerFortescue(twt, isPartOfGeneratingUnit, ro, xo, freeFluxes, leg1ConnectionType, leg2ConnectionType, r1Ground, x1Ground, r2Ground, x2Ground);
    }

    public TwoWindingsTransformerFortescueAdder withIsPartOfGeneratingUnit(boolean isPartOfGeneratingUnit) {
        this.isPartOfGeneratingUnit = isPartOfGeneratingUnit;
        return this;
    }

    public TwoWindingsTransformerFortescueAdder withCoeffRo(double coeffRo) {
        this.coeffRo = coeffRo;
        return this;
    }

    public TwoWindingsTransformerFortescueAdder withCoeffXo(double coeffXo) {
        this.coeffXo = coeffXo;
        return this;
    }

    public TwoWindingsTransformerFortescueAdder withRo(double ro) {
        this.ro = ro;
        return this;
    }

    public TwoWindingsTransformerFortescueAdder withXo(double xo) {
        this.xo = xo;
        return this;
    }

    public TwoWindingsTransformerFortescueAdder withFreeFluxes(boolean freeFluxes) {
        this.freeFluxes = freeFluxes;
        return this;
    }

    public TwoWindingsTransformerFortescueAdder withLeg1ConnectionType(WindingConnectionType leg1ConnectionType) {
        this.leg1ConnectionType = Objects.requireNonNull(leg1ConnectionType);
        return this;
    }

    public TwoWindingsTransformerFortescueAdder withLeg2ConnectionType(WindingConnectionType leg2ConnectionType) {
        this.leg2ConnectionType = Objects.requireNonNull(leg2ConnectionType);
        return this;
    }
}
