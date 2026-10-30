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
package org.apache.grails.xml.aot

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.core.io.support.SpringFactoriesLoader
import spock.lang.Specification

import grails.converters.XML
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer
import org.grails.web.databinding.bindingsource.XmlDataBindingSourceCreator

/**
 * Covers the XML converter that encodeAsXML creates by name surviving into an ahead-of-time image, without which an
 * object is escaped as its string there.
 */
class XmlRuntimeHintsSpec extends Specification {

    void 'the XML converter and the constructor encodeAsXML creates it with are registered'() {
        given:
        def hints = new RuntimeHints()

        when:
        new XmlRuntimeHints().registerHints(hints, getClass().classLoader)

        then:
        RuntimeHintsPredicates.reflection().onType(XML).test(hints)
        RuntimeHintsPredicates.reflection().onConstructorInvocation(XML.getConstructor(Object)).test(hints)
    }

    void 'the initializer of the XML converter and the XML binding source creator, which other modules create by name, are registered'() {
        given:
        def hints = new RuntimeHints()

        when:
        new XmlRuntimeHints().registerHints(hints, getClass().classLoader)

        then:
        RuntimeHintsPredicates.reflection().onConstructorInvocation(
                XmlConvertersConfigurationInitializer.getConstructor()).test(hints)
        RuntimeHintsPredicates.reflection().onConstructorInvocation(XmlDataBindingSourceCreator.getConstructor()).test(hints)
    }

    void 'is registered for ahead-of-time processing'() {
        expect:
        SpringFactoriesLoader.forResourceLocation('META-INF/spring/aot.factories')
                .load(RuntimeHintsRegistrar).any { it instanceof XmlRuntimeHints }
    }
}
