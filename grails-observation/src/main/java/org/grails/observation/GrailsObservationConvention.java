/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * Base {@link ObservationConvention} for the Grails request-lifecycle observations. It provides the
 * pieces every Grails convention shares — {@link #supportsContext} (by context type), the
 * low-cardinality {@code error} key value, and the {@code null}/empty fallback helper — so concrete
 * conventions only declare their own name, contextual name and observation-specific tags.
 *
 * @param <C> the {@link Observation.Context} type this convention handles
 * @since 8.0
 */
public abstract class GrailsObservationConvention<C extends Observation.Context>
        implements ObservationConvention<C> {

    /** Placeholder for a missing/empty optional resource (e.g. a render with no named view). */
    protected static final String NONE = "none";

    /** Placeholder for a required-but-absent identifier (e.g. controller/action/interceptor name). */
    protected static final String UNKNOWN = "unknown";

    private final Class<C> contextType;

    protected GrailsObservationConvention(Class<C> contextType) {
        this.contextType = contextType;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return this.contextType.isInstance(context);
    }

    /**
     * The shared low-cardinality {@code error} key value: the thrown exception's simple name, or
     * {@code "none"} when the observation completed without error.
     */
    protected KeyValue error(C context) {
        Throwable error = context.getError();
        return errorKeyName().withValue((error != null) ? error.getClass().getSimpleName() : NONE);
    }

    /** The {@link KeyName} used for the shared {@code error} tag. */
    protected abstract KeyName errorKeyName();

    /** Returns {@code value} when it is non-null and non-empty, otherwise {@code fallback}. */
    protected static String orElse(String value, String fallback) {
        return (value != null && !value.isEmpty()) ? value : fallback;
    }
}
