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

import io.micrometer.observation.Observation;

/**
 * Context that holds information for {@link io.micrometer.observation.Observation} instrumentation
 * of Groovy Server Pages (GSP) rendering — the view, an included template, or a layout.
 *
 * @author Grails
 * @since 8.0
 * @see GroovyPageObservationDocumentation
 */
public class GroovyPageObservationContext extends Observation.Context {

    private final String resource;

    public GroovyPageObservationContext(String resource) {
        this.resource = resource;
    }

    /**
     * Return the name of the rendered resource — the view URI (e.g. {@code /book/show}), the
     * template name, or the layout name, depending on the observation.
     * @return the resource name, possibly {@code null}
     */
    public String getResource() {
        return this.resource;
    }
}
