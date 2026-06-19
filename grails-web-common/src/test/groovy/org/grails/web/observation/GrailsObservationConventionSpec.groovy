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
package org.grails.web.observation

import spock.lang.Specification

class GrailsObservationConventionSpec extends Specification {

    private static Map<String, String> tags(keyValues) {
        keyValues.collectEntries { [(it.key): it.value] }
    }

    def "controller convention names the span and emits controller/action/error tags"() {
        given:
        def convention = new DefaultControllerObservationConvention()
        def context = new ControllerObservationContext('book', 'show')

        expect:
        convention.name == 'grails.controller'
        convention.getContextualName(context) == 'grails.controller book'
        tags(convention.getLowCardinalityKeyValues(context)) == [
                'grails.controller': 'book',
                'grails.action'    : 'show',
                'error'            : 'none',
        ]
        convention.supportsContext(context)
        !convention.supportsContext(new RenderObservationContext('x'))
    }

    def "controller convention records the thrown exception's simple name"() {
        given:
        def convention = new DefaultControllerObservationConvention()
        def context = new ControllerObservationContext('book', 'show')
        context.setError(new IllegalStateException('boom'))

        expect:
        tags(convention.getLowCardinalityKeyValues(context)).error == 'IllegalStateException'
    }

    def "controller convention falls back to 'unknown' for missing controller/action"() {
        expect:
        tags(new DefaultControllerObservationConvention()
                .getLowCardinalityKeyValues(new ControllerObservationContext(null, null))) == [
                'grails.controller': 'unknown',
                'grails.action'    : 'unknown',
                'error'            : 'none',
        ]
    }

    def "render convention names the span and emits view/error tags"() {
        given:
        def convention = new DefaultRenderObservationConvention()
        def context = new RenderObservationContext('/book/show')

        expect:
        convention.name == 'grails.render'
        convention.getContextualName(context) == 'grails.render /book/show'
        tags(convention.getLowCardinalityKeyValues(context)) == [
                'grails.view': '/book/show',
                'error'      : 'none',
        ]
        convention.supportsContext(context)
        !convention.supportsContext(new ControllerObservationContext('a', 'b'))
    }

    def "render convention falls back to 'none' for a missing view"() {
        expect:
        tags(new DefaultRenderObservationConvention()
                .getLowCardinalityKeyValues(new RenderObservationContext(null))) == [
                'grails.view': 'none',
                'error'      : 'none',
        ]
    }

    def "documentation wires each observation to its default convention"() {
        expect:
        GrailsObservationDocumentation.CONTROLLER.defaultConvention == DefaultControllerObservationConvention
        GrailsObservationDocumentation.RENDER.defaultConvention == DefaultRenderObservationConvention
    }
}
