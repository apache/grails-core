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
 * <p>Each observation shares the same {@link LowCardinalityKeyNames key names} ({@code gsp.name},
 * {@code error}); the observation name ({@code gsp.view} / {@code gsp.template} / {@code gsp.layout})
 * distinguishes what was rendered.</p>
 *
 * @author Grails
 * @since 8.0
 */
public enum GroovyPageObservationDocumentation implements ObservationDocumentation {

    /**
     * Rendering of a single GSP view.
     */
    GSP_VIEW,

    /**
     * Rendering of an included GSP template (e.g. {@code <g:render template="..."/>}).
     */
    GSP_TEMPLATE,

    /**
     * Decoration of rendered content by a GSP layout (SiteMesh).
     */
    GSP_LAYOUT;

    @Override
    public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
        return DefaultGroovyPageObservationConvention.class;
    }

    @Override
    public KeyName[] getLowCardinalityKeyNames() {
        return LowCardinalityKeyNames.values();
    }

    public enum LowCardinalityKeyNames implements KeyName {

        /**
         * Name of the rendered resource (view URI, template name, or layout name).
         */
        NAME {
            @Override
            public String asString() {
                return "gsp.name";
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
