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
package org.grails.plugins.web.interceptors.observation

import spock.lang.Specification

class InterceptorObservationConventionSpec extends Specification {

    private static Map<String, String> tags(keyValues) {
        keyValues.collectEntries { [(it.key): it.value] }
    }

    def "convention names the span and emits interceptor/phase/error tags"() {
        given:
        def convention = new DefaultInterceptorObservationConvention()
        def context = new InterceptorObservationContext('auth', 'before')

        expect:
        convention.name == 'grails.interceptor'
        convention.getContextualName(context) == 'grails.interceptor auth'
        tags(convention.getLowCardinalityKeyValues(context)) == [
                'grails.interceptor'      : 'auth',
                'grails.interceptor.phase': 'before',
                'error'                   : 'none',
        ]
        convention.supportsContext(context)
    }

    def "convention records the thrown exception and falls back to 'unknown'"() {
        given:
        def convention = new DefaultInterceptorObservationConvention()
        def context = new InterceptorObservationContext(null, null)
        context.setError(new IllegalStateException('x'))
        Map<String, String> t = tags(convention.getLowCardinalityKeyValues(context))

        expect:
        t['grails.interceptor'] == 'unknown'
        t['grails.interceptor.phase'] == 'unknown'
        t.error == 'IllegalStateException'
    }

    def "documentation wires the default convention"() {
        expect:
        InterceptorObservationDocumentation.INTERCEPTOR.defaultConvention == DefaultInterceptorObservationConvention
    }
}
