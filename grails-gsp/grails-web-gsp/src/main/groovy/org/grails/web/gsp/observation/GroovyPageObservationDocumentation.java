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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documented {@link io.micrometer.observation.Observation}s for Groovy Server Pages (GSP) rendering.
 *
 * @author Grails
 * @since 8.0
 */
public enum GroovyPageObservationDocumentation implements ObservationDocumentation {

    /**
     * Observation created around the rendering of a single GSP view.
     */
    GSP_VIEW {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultGroovyPageObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeyNames.values();
        }
    };

    public enum LowCardinalityKeyNames implements KeyName {

        /**
         * URI of the GSP view being rendered.
         */
        VIEW {
            @Override
            public String asString() {
                return "gsp.view";
            }
        },

        /**
         * Simple name of the exception thrown during rendering, or {@code "none"}.
         */
        ERROR {
            @Override
            public String asString() {
                return "error";
            }
        }
    }
}
