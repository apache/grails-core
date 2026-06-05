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
package org.grails.web.gsp.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import static org.grails.web.gsp.observation.GroovyPageObservationDocumentation.LowCardinalityKeyNames;

/**
 * Default {@link GroovyPageObservationConvention}.
 *
 * <p>Names the observation {@code gsp.view} and attaches the {@code gsp.view} (view URI)
 * and {@code error} (exception simple name, or {@code "none"}) low-cardinality key values.</p>
 *
 * @author Grails
 * @since 8.0
 */
public class DefaultGroovyPageObservationConvention implements GroovyPageObservationConvention {

    private static final String DEFAULT_NAME = "gsp.view";

    private static final KeyValue ERROR_NONE = LowCardinalityKeyNames.ERROR.withValue("none");

    private static final String VIEW_UNKNOWN = "unknown";

    private final String name;

    public DefaultGroovyPageObservationConvention() {
        this(DEFAULT_NAME);
    }

    public DefaultGroovyPageObservationConvention(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getContextualName(GroovyPageObservationContext context) {
        return "gsp.view " + viewUri(context);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(GroovyPageObservationContext context) {
        return KeyValues.of(view(context), error(context));
    }

    protected KeyValue view(GroovyPageObservationContext context) {
        return LowCardinalityKeyNames.VIEW.withValue(viewUri(context));
    }

    protected KeyValue error(GroovyPageObservationContext context) {
        Throwable error = context.getError();
        return (error != null)
                ? LowCardinalityKeyNames.ERROR.withValue(error.getClass().getSimpleName())
                : ERROR_NONE;
    }

    private static String viewUri(GroovyPageObservationContext context) {
        String uri = context.getViewUri();
        return (uri != null && !uri.isEmpty()) ? uri : VIEW_UNKNOWN;
    }
}
