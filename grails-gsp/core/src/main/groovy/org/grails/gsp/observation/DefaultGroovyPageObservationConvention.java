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
package org.grails.gsp.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import static org.grails.gsp.observation.GroovyPageObservationDocumentation.LowCardinalityKeyNames;

/**
 * Default {@link GroovyPageObservationConvention}.
 *
 * <p>Names the observation as configured ({@code gsp.view} / {@code gsp.template} / {@code gsp.layout})
 * and attaches the {@code gsp.name} (rendered resource) and {@code error} (exception simple name, or
 * {@code "none"}) low-cardinality key values.</p>
 *
 * @author Grails
 * @since 8.0
 */
public class DefaultGroovyPageObservationConvention implements GroovyPageObservationConvention {

    private static final KeyValue ERROR_NONE = LowCardinalityKeyNames.ERROR.withValue("none");

    private static final String UNKNOWN = "unknown";

    private static final String DEFAULT_NAME = "gsp";

    private final String name;

    /**
     * Creates a convention with the generic {@code "gsp"} name. Exists so this class can satisfy
     * {@link io.micrometer.observation.docs.ObservationDocumentation#getDefaultConvention()} via
     * reflection; instrumentation sites always pass an explicitly-named instance.
     */
    public DefaultGroovyPageObservationConvention() {
        this(DEFAULT_NAME);
    }

    /**
     * @param name the observation name (e.g. {@code gsp.view}, {@code gsp.template}, {@code gsp.layout})
     */
    public DefaultGroovyPageObservationConvention(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getContextualName(GroovyPageObservationContext context) {
        return this.name + " " + resource(context);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(GroovyPageObservationContext context) {
        return KeyValues.of(name(context), error(context));
    }

    protected KeyValue name(GroovyPageObservationContext context) {
        return LowCardinalityKeyNames.NAME.withValue(resource(context));
    }

    protected KeyValue error(GroovyPageObservationContext context) {
        Throwable error = context.getError();
        return (error != null) ?
                LowCardinalityKeyNames.ERROR.withValue(error.getClass().getSimpleName()) :
                ERROR_NONE;
    }

    private static String resource(GroovyPageObservationContext context) {
        String resource = context.getResource();
        return (resource != null && !resource.isEmpty()) ? resource : UNKNOWN;
    }
}
