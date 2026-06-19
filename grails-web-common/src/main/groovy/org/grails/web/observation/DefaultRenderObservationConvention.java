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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import static org.grails.web.observation.GrailsObservationDocumentation.RenderLowCardinalityKeyNames;

/**
 * Default {@link RenderObservationConvention}.
 *
 * @author Apache Grails
 * @since 8.0.0
 */
public class DefaultRenderObservationConvention implements RenderObservationConvention {

    private static final String NAME = "grails.render";

    private static final String NONE = "none";

    private static final KeyValue ERROR_NONE = RenderLowCardinalityKeyNames.ERROR.withValue("none");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getContextualName(RenderObservationContext context) {
        return NAME + " " + view(context);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(RenderObservationContext context) {
        return KeyValues.of(
                RenderLowCardinalityKeyNames.VIEW.withValue(view(context)),
                error(context));
    }

    protected KeyValue error(RenderObservationContext context) {
        Throwable error = context.getError();
        return (error != null) ?
                RenderLowCardinalityKeyNames.ERROR.withValue(error.getClass().getSimpleName()) :
                ERROR_NONE;
    }

    private static String view(RenderObservationContext context) {
        String view = context.getView();
        return (view != null && !view.isEmpty()) ? view : NONE;
    }
}
