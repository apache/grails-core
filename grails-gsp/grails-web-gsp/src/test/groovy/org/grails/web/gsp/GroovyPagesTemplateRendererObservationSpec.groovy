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

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry

import org.grails.gsp.GroovyPagesException
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.taglib.TemplateVariableBinding
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
        ObservationRegistry.create().tap {
            observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
                @Override boolean supportsContext(Observation.Context context) { true }
                @Override void onStop(Observation.Context context) { recorded << context }
            })
        }
    }

    /** A renderer whose actual render body is supplied by a closure, bypassing the GSP pipeline. */
    private GroovyPagesTemplateRenderer rendererFor(ObservationRegistry registry, Closure body = {}) {
        new GroovyPagesTemplateRenderer() {
            @Override
            protected void doRender(String templateName, GrailsWebRequest webRequest,
                                    TemplateVariableBinding pageScope, Map<String, Object> attrs,
                                    Object body2, Writer out) {
                body.call()
            }
        }.tap {
            groovyPagesTemplateEngine = Mock(GroovyPagesTemplateEngine)
            observationRegistry = registry
        }
    }

    void "a gsp.template observation is recorded with the template name on a successful render"() {
        given:
        def renderer = rendererFor(recordingRegistry())

        when:
        renderer.render(null, null, [template: '/shared/_card'], null, new StringWriter())

        then:
        recorded.size() == 1
        recorded[0].name == 'gsp.template'
        recorded[0].contextualName == 'gsp.template /shared/_card'

        and: "gsp.name is high-cardinality (span only); no error on a successful render"
        recorded[0].highCardinalityKeyValues.find { it.key == 'gsp.name' }?.value == '/shared/_card'
        recorded[0].lowCardinalityKeyValues.find { it.key == 'gsp.name' } == null
        recorded[0].error == null
    }

    void "no observation is recorded when the registry is NOOP (zero overhead)"() {
        given:
        def renderer = rendererFor(ObservationRegistry.NOOP)

        when:
        renderer.render(null, null, [template: '/shared/_card'], null, new StringWriter())

        then:
        recorded.isEmpty()
    }

    void "the observation records the exception when rendering fails"() {
        given:
        def renderer = rendererFor(recordingRegistry()) {
            throw new GroovyPagesException('boom')
        }

        when:
        renderer.render(null, null, [template: '/shared/_card'], null, new StringWriter())

        then:
        thrown(GroovyPagesException)
        recorded.size() == 1
        recorded[0].name == 'gsp.template'
        recorded[0].error instanceof GroovyPagesException
    }
}
