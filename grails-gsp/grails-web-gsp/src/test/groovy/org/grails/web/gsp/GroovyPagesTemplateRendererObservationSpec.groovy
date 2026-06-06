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
package org.grails.web.gsp

import io.micrometer.common.KeyValues
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry

import org.grails.gsp.GroovyPagesException
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.web.servlet.mvc.GrailsWebRequest

import spock.lang.Specification

/**
 * Tests that {@link GroovyPagesTemplateRenderer} records a {@code gsp.template} observation when an
 * {@link ObservationRegistry} is configured, following the Micrometer Observation pattern.
 *
 * <p>The actual {@code <g:render template>} pipeline is overridden ({@code doRender}) so the test
 * isolates the observation wrapper from the rendering machinery.</p>
 */
class GroovyPagesTemplateRendererObservationSpec extends Specification {

    private List<Observation.Context> recorded = []

    private ObservationRegistry recordingRegistry() {
        ObservationRegistry registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override boolean supportsContext(Observation.Context context) { true }
            @Override void onStop(Observation.Context context) { recorded << context }
        })
        registry
    }

    /** A renderer whose actual render body is supplied by a closure, bypassing the GSP pipeline. */
    private GroovyPagesTemplateRenderer rendererFor(ObservationRegistry registry, Closure body = {}) {
        GroovyPagesTemplateRenderer renderer = new GroovyPagesTemplateRenderer() {
            @Override
            protected void doRender(String templateName, GrailsWebRequest webRequest,
                    org.grails.taglib.TemplateVariableBinding pageScope, Map<String, Object> attrs,
                    Object body2, Writer out) {
                body.call()
            }
        }
        renderer.groovyPagesTemplateEngine = Mock(GroovyPagesTemplateEngine)
        renderer.observationRegistry = registry
        renderer
    }

    void "a gsp.template observation is recorded with the template name on a successful render"() {
        given:
        GroovyPagesTemplateRenderer renderer = rendererFor(recordingRegistry())

        when:
        renderer.render(null, null, [template: '/shared/_card'], null, new StringWriter())

        then:
        recorded.size() == 1
        recorded[0].name == 'gsp.template'
        recorded[0].contextualName == 'gsp.template /shared/_card'

        and:
        KeyValues kvs = recorded[0].lowCardinalityKeyValues
        kvs.find { it.key == 'gsp.name' }?.value == '/shared/_card'
        kvs.find { it.key == 'error' }?.value == 'none'
    }

    void "no observation is recorded when the registry is NOOP (zero overhead)"() {
        given:
        GroovyPagesTemplateRenderer renderer = rendererFor(ObservationRegistry.NOOP)

        when:
        renderer.render(null, null, [template: '/shared/_card'], null, new StringWriter())

        then:
        recorded.isEmpty()
    }

    void "the error key carries the exception name when rendering fails"() {
        given:
        GroovyPagesTemplateRenderer renderer = rendererFor(recordingRegistry(), { throw new GroovyPagesException('boom') })

        when:
        renderer.render(null, null, [template: '/shared/_card'], null, new StringWriter())

        then:
        thrown(GroovyPagesException)
        recorded.size() == 1
        recorded[0].name == 'gsp.template'
        recorded[0].lowCardinalityKeyValues.find { it.key == 'error' }?.value == 'GroovyPagesException'
    }
}
