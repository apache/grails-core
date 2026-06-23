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
package org.grails.web.servlet.view

import io.micrometer.common.KeyValues
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.grails.gsp.GroovyPagesException
import org.grails.web.servlet.mvc.GrailsWebRequest

import spock.lang.Specification

/**
 * Tests that {@link GroovyPageView} records a {@code gsp.view} observation when an
 * {@link ObservationRegistry} is configured, following the Micrometer Observation pattern.
 *
 * <p>The actual GSP rendering is overridden ({@code doRenderTemplate}) so the test isolates the
 * observation wrapper from the rendering pipeline.</p>
 */
class GroovyPageViewObservationSpec extends Specification {

    private List<Observation.Context> recorded = []

    private ObservationRegistry recordingRegistry() {
        ObservationRegistry registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override boolean supportsContext(Observation.Context context) { true }
            @Override void onStop(Observation.Context context) { recorded << context }
        })
        registry
    }

    /** A view whose actual render body is supplied by a closure, bypassing the GSP pipeline. */
    private GroovyPageView viewFor(ObservationRegistry registry, String url = '/book/show', Closure body = {}) {
        def view = new GroovyPageView() {
            @Override
            protected void doRenderTemplate(Map<String, Object> model, GrailsWebRequest webRequest,
                    HttpServletRequest request, HttpServletResponse response) {
                body.call()
            }
        }
        view.url = url
        view.observationRegistry = registry
        view
    }

    void "a gsp.view observation is recorded with the view URI on a successful render"() {
        given:
        GroovyPageView view = viewFor(recordingRegistry())

        when:
        view.renderTemplate([:], null, null, null)

        then:
        recorded.size() == 1
        recorded[0].name == 'gsp.view'
        recorded[0].contextualName == 'gsp.view /book/show'

        and: "gsp.name is high-cardinality (span only); no error on a successful render"
        recorded[0].highCardinalityKeyValues.find { it.key == 'gsp.name' }?.value == '/book/show'
        recorded[0].lowCardinalityKeyValues.find { it.key == 'gsp.name' } == null
        recorded[0].error == null
    }

    void "no observation is recorded when the registry is NOOP (zero overhead)"() {
        given:
        GroovyPageView view = viewFor(ObservationRegistry.NOOP)

        when:
        view.renderTemplate([:], null, null, null)

        then:
        recorded.isEmpty()
    }

    void "the observation records the exception when rendering fails"() {
        given:
        GroovyPageView view = viewFor(recordingRegistry(), '/book/show', { throw new GroovyPagesException('boom') })

        when:
        view.renderTemplate([:], null, null, null)

        then:
        thrown(GroovyPagesException)
        recorded.size() == 1
        recorded[0].name == 'gsp.view'
        recorded[0].error instanceof GroovyPagesException
    }
}
