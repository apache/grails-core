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
package org.grails.plugins.web.interceptors.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

import org.grails.observation.GrailsObservationKeyNames;

/**
 * {@link ObservationDocumentation} for the execution of a Grails interceptor's {@code before()} /
 * {@code after()} callback around a request.
 *
 * @since 8.0
 */
public enum InterceptorObservationDocumentation implements ObservationDocumentation {

    /**
     * Execution of one matched interceptor callback (one span per {@code before()}/{@code after()}).
     */
    INTERCEPTOR {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultInterceptorObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return new KeyName[] {
                InterceptorLowCardinalityKeyNames.INTERCEPTOR,
                InterceptorLowCardinalityKeyNames.PHASE,
                GrailsObservationKeyNames.ERROR,
            };
        }
    };

    public enum InterceptorLowCardinalityKeyNames implements KeyName {

        /** Logical name of the interceptor (e.g. {@code auth}). */
        INTERCEPTOR {
            @Override
            public String asString() {
                return "grails.interceptor";
            }
        },

        /** Callback phase: {@code before} or {@code after}. */
        PHASE {
            @Override
            public String asString() {
                return "grails.interceptor.phase";
            }
        }
    }
}
