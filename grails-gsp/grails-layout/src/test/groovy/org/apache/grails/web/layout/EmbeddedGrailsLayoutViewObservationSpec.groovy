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
package org.apache.grails.web.layout

import com.opensymphony.sitemesh.Content

import io.micrometer.common.KeyValues
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry

import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.servlet.View

import spock.lang.Specification

/**
 * Tests that {@link EmbeddedGrailsLayoutView} records a {@code gsp.layout} observation around SiteMesh
 * decoration when an {@link ObservationRegistry} is configured, following the Micrometer Observation
 * pattern. A stub {@link SpringMVCViewDecorator} isolates the observation wrapper from the decoration
 * pipeline (the layout module has no byte-buddy on the test classpath, so the concrete decorator can't
 * be mocked directly).
 */
class EmbeddedGrailsLayoutViewObservationSpec extends Specification {

    private List<Observation.Context> recorded = []

    /** A decorator whose render() is controllable and whose page name is fixed for tag assertions. */
    private static class StubDecorator extends SpringMVCViewDecorator {
        boolean rendered = false
        RuntimeException toThrow

        StubDecorator(View view) {
            super('main', view)
        }

        @Override
        String getPage() { '/layouts/main' }

        @Override
        void render(Content content, Map<String, ?> model, HttpServletRequest request,
                    HttpServletResponse response, ServletContext servletContext) {
            rendered = true
            if (toThrow != null) {
                throw toThrow
            }
        }
    }

    private ObservationRegistry recordingRegistry() {
        ObservationRegistry registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override boolean supportsContext(Observation.Context context) { true }
            @Override void onStop(Observation.Context context) { recorded << context }
        })
        registry
    }

    private EmbeddedGrailsLayoutView viewFor(ObservationRegistry registry) {
        EmbeddedGrailsLayoutView view = new EmbeddedGrailsLayoutView(null, null)
        view.observationRegistry = registry
        view
    }

    void "a gsp.layout observation is recorded with the layout page name on a successful decoration"() {
        given:
        EmbeddedGrailsLayoutView view = viewFor(recordingRegistry())
        StubDecorator decorator = new StubDecorator(Mock(View))

        when:
        view.renderWithLayout(decorator, Mock(Content), [:], null, null, Mock(GrailsWebRequest))

        then:
        decorator.rendered
        recorded.size() == 1
        recorded[0].name == 'gsp.layout'
        recorded[0].contextualName == 'gsp.layout /layouts/main'

        and: "gsp.name is high-cardinality (span only); error is the low-cardinality metric tag"
        recorded[0].highCardinalityKeyValues.find { it.key == 'gsp.name' }?.value == '/layouts/main'
        recorded[0].lowCardinalityKeyValues.find { it.key == 'gsp.name' } == null
        recorded[0].lowCardinalityKeyValues.find { it.key == 'error' }?.value == 'none'
    }

    void "no observation is recorded when the registry is NOOP (zero overhead)"() {
        given:
        EmbeddedGrailsLayoutView view = viewFor(ObservationRegistry.NOOP)
        StubDecorator decorator = new StubDecorator(Mock(View))

        when:
        view.renderWithLayout(decorator, Mock(Content), [:], null, null, Mock(GrailsWebRequest))

        then:
        decorator.rendered
        recorded.isEmpty()
    }

    void "the error key carries the exception name when decoration fails"() {
        given:
        EmbeddedGrailsLayoutView view = viewFor(recordingRegistry())
        StubDecorator decorator = new StubDecorator(Mock(View))
        decorator.toThrow = new IllegalStateException('boom')

        when:
        view.renderWithLayout(decorator, Mock(Content), [:], null, null, Mock(GrailsWebRequest))

        then:
        thrown(IllegalStateException)
        recorded.size() == 1
        recorded[0].name == 'gsp.layout'
        recorded[0].lowCardinalityKeyValues.find { it.key == 'error' }?.value == 'IllegalStateException'
    }
}
