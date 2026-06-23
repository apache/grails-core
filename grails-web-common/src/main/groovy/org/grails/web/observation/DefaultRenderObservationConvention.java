/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.web.observation;

import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;

import org.grails.observation.GrailsObservationConvention;
import org.grails.observation.GrailsObservationKeyNames;

import static org.grails.web.observation.GrailsObservationDocumentation.RenderLowCardinalityKeyNames;

/**
 * Default {@link RenderObservationConvention}.
 *
 * @since 8.0
 */
public class DefaultRenderObservationConvention extends GrailsObservationConvention<RenderObservationContext>
        implements RenderObservationConvention {

    private static final String NAME = "grails.render";

    public DefaultRenderObservationConvention() {
        super(RenderObservationContext.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getContextualName(RenderObservationContext context) {
        return NAME + " " + orElse(context.getView(), NONE);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(RenderObservationContext context) {
        return KeyValues.of(
                RenderLowCardinalityKeyNames.VIEW.withValue(orElse(context.getView(), NONE)),
                error(context));
    }

    @Override
    protected KeyName errorKeyName() {
        return GrailsObservationKeyNames.ERROR;
    }
}
