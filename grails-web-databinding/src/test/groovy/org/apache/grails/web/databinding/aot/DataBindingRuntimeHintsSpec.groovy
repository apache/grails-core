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
package org.apache.grails.web.databinding.aot

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.core.io.support.SpringFactoriesLoader
import spock.lang.Specification

import grails.databinding.DataBindingSource
import org.grails.web.databinding.bindingsource.JsonDataBindingSourceCreator

class DataBindingRuntimeHintsSpec extends Specification {

    void 'the types binding dispatches on are registered'() {
        given:
        def hints = new RuntimeHints()

        when:
        new DataBindingRuntimeHints().registerHints(hints, getClass().classLoader)

        then:
        RuntimeHintsPredicates.reflection().onType(DataBindingSource).test(hints)
    }

    void 'what Spring creates the JSON binding source creator by its class with, to parse with Jackson, is registered'() {
        given:
        def hints = new RuntimeHints()

        when:
        new DataBindingRuntimeHints().registerHints(hints, getClass().classLoader)

        then:
        RuntimeHintsPredicates.reflection().onConstructorInvocation(JsonDataBindingSourceCreator.getConstructor()).test(hints)

        and: 'the fields Spring injects and the method it initializes it with, which it finds by reflection'
        ['jsonMapperProvider', 'applicationContext', 'useJackson', 'jsonSlurper'].every { String field ->
            RuntimeHintsPredicates.reflection().onFieldAccess(JsonDataBindingSourceCreator, field).test(hints)
        }
        RuntimeHintsPredicates.reflection().onMethodInvocation(JsonDataBindingSourceCreator, 'reportLegacyParsing').test(hints)
    }

    void 'is registered for ahead-of-time processing'() {
        expect:
        SpringFactoriesLoader.forResourceLocation('META-INF/spring/aot.factories')
                .load(RuntimeHintsRegistrar).any { it instanceof DataBindingRuntimeHints }
    }
}
