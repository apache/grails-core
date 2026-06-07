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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documented {@link io.micrometer.observation.Observation}s for Groovy Server Pages (GSP) rendering.
 *
 * <p>The observation name ({@code gsp.view} / {@code gsp.template} / {@code gsp.layout}) distinguishes
 * what was rendered. {@code error} is the only {@link LowCardinalityKeyNames low-cardinality} key, so it
 * is the only one that becomes a <em>metric</em> tag. {@code gsp.name} (the rendered resource path) is a
 * {@link HighCardinalityKeyNames high-cardinality} key — it is attached to the span/trace for drilldown
 * but deliberately kept off the timer, because a real application has hundreds or thousands of distinct
 * GSPs and tagging the metric per-resource would explode the time-series cardinality.</p>
 *
 * <p>Applicability across run modes: {@code gsp.view} / {@code gsp.template} / {@code gsp.layout} fire on
 * every render and are meaningful in production. {@code gsp.compile} fires only when a GSP is compiled at
 * runtime — expected in development, but on a precompiled (production) deployment it should essentially
 * never appear, so its presence there is a useful signal that a view is <em>not</em> precompiled.</p>
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
    GSP_LAYOUT,

    /**
     * Runtime compilation of a GSP into its {@code GroovyPageMetaInfo}. Happens on a runtime
     * template-compile cache miss — i.e. in development, or when a view is not precompiled. A
     * precompiled production deployment should not emit this.
     */
    GSP_COMPILE;

    @Override
    public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
        return DefaultGroovyPageObservationConvention.class;
    }

    @Override
    public KeyName[] getLowCardinalityKeyNames() {
        return LowCardinalityKeyNames.values();
    }

    @Override
    public KeyName[] getHighCardinalityKeyNames() {
        return HighCardinalityKeyNames.values();
    }

    public enum LowCardinalityKeyNames implements KeyName {

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

    public enum HighCardinalityKeyNames implements KeyName {

        /**
         * Name of the rendered resource (view URI, template name, or layout name). High-cardinality:
         * attached to the span for drilldown, but not to the metric, to avoid per-resource time-series
         * explosion on applications with many GSPs.
         */
        NAME {
            @Override
            public String asString() {
                return "gsp.name";
            }
        }
    }
}
