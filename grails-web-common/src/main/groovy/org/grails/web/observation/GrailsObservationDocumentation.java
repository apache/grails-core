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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * {@link ObservationDocumentation} for the Grails request-handling lifecycle observations that sit
 * between the Spring HTTP server observation and the leaf GSP/data-store observations.
 *
 * @author Apache Grails
 * @since 8.0.0
 */
public enum GrailsObservationDocumentation implements ObservationDocumentation {

    /**
     * Execution of the controller action selected by the matched URL mapping.
     */
    CONTROLLER {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultControllerObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return ControllerLowCardinalityKeyNames.values();
        }
    },

    /**
     * Rendering of the response view (view resolution, layout decoration and writing the response).
     */
    RENDER {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultRenderObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return RenderLowCardinalityKeyNames.values();
        }
    };

    public enum ControllerLowCardinalityKeyNames implements KeyName {

        /** Logical name of the controller. */
        CONTROLLER {
            @Override
            public String asString() {
                return "grails.controller";
            }
        },

        /** Name of the executed action. */
        ACTION {
            @Override
            public String asString() {
                return "grails.action";
            }
        },

        /** Simple name of the thrown exception, or {@code none}. */
        ERROR {
            @Override
            public String asString() {
                return "error";
            }
        }
    }

    public enum RenderLowCardinalityKeyNames implements KeyName {

        /** Name of the rendered view, or {@code none}. */
        VIEW {
            @Override
            public String asString() {
                return "grails.view";
            }
        },

        /** Simple name of the thrown exception, or {@code none}. */
        ERROR {
            @Override
            public String asString() {
                return "error";
            }
        }
    }
}
