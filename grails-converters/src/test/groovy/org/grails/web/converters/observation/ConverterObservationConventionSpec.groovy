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
package org.grails.web.converters.observation

import spock.lang.Specification

class ConverterObservationConventionSpec extends Specification {

    private static Map<String, String> tags(keyValues) {
        keyValues.collectEntries { [(it.key): it.value] }
    }

    def "convention names the span and emits format/error tags"() {
        given:
        def convention = new DefaultConverterObservationConvention()
        def context = new ConverterObservationContext('json')

        expect:
        convention.name == 'grails.convert'
        convention.getContextualName(context) == 'grails.convert json'
        tags(convention.getLowCardinalityKeyValues(context)) == [
                'grails.convert.format': 'json',
                'error'                : 'none',
        ]
        convention.supportsContext(context)
    }

    def "convention records the thrown exception and falls back to 'unknown'"() {
        given:
        def convention = new DefaultConverterObservationConvention()
        def context = new ConverterObservationContext(null)
        context.setError(new IllegalStateException('x'))
        Map<String, String> t = tags(convention.getLowCardinalityKeyValues(context))

        expect:
        t['grails.convert.format'] == 'unknown'
        t.error == 'IllegalStateException'
    }

    def "documentation wires the default convention"() {
        expect:
        ConverterObservationDocumentation.CONVERT.defaultConvention == DefaultConverterObservationConvention
    }
}
