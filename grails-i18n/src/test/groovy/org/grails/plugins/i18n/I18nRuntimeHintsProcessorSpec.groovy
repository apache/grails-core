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
package org.grails.plugins.i18n

import org.springframework.aot.generate.GenerationContext
import org.springframework.aot.hint.ResourcePatternHints
import org.springframework.aot.hint.RuntimeHints
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment

import spock.lang.Specification

/**
 * Spring Boot's own {@code MessageSourceRuntimeHints} registers only two hardcoded patterns for
 * {@code messages*}, deriving nothing from the configured base names. Every Grails plugin bundle uses a
 * namespaced base name, so without these hints plugin messages resolve on the JVM and vanish in a
 * native image.
 */
class I18nRuntimeHintsProcessorSpec extends Specification {

    private List<String> patternsFor(String... basenames) {
        MockEnvironment environment = new MockEnvironment()
        if (basenames) {
            environment.setProperty('spring.messages.basename', basenames.join(','))
        }
        registeredPatterns(environment)
    }

    private List<String> registeredPatterns(Environment environment) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory()
        beanFactory.registerSingleton(ConfigurableApplicationContext.ENVIRONMENT_BEAN_NAME, environment)

        BeanFactoryInitializationAotContribution contribution =
                new I18nRuntimeHintsProcessor().processAheadOfTime(beanFactory)
        RuntimeHints hints = new RuntimeHints()
        contribution.applyTo(Stub(GenerationContext) { getRuntimeHints() >> hints }, null)

        hints.resources().resourcePatternHints()
                .collect { ResourcePatternHints it -> it.includes }
                .flatten()
                .collect { it.pattern }
    }

    void 'each base name contributes its base bundle and its locale variants'() {
        when:
        List<String> patterns = patternsFor('messages', 'spring-security-core')

        then:
        patterns.containsAll([
                'messages.properties', 'messages_*.properties',
                'spring-security-core.properties', 'spring-security-core_*.properties'])
    }

    void 'the descriptor itself is registered so the runtime can read it back'() {
        expect: 'discovery is an exact-name getResources lookup, which still needs the resource present'
        patternsFor('messages').contains(I18nDescriptors.DESCRIPTOR_PATH)
    }

    void 'a dotted base name is converted to its resource path'() {
        when: 'base names are ResourceBundle names, not file paths'
        List<String> patterns = patternsFor('config.i18n.custom')

        then: 'config.i18n.custom resolves to config/i18n/custom.properties'
        patterns.containsAll(['config/i18n/custom.properties', 'config/i18n/custom_*.properties'])

        and: 'registering the literal name would have missed the bundle entirely'
        !patterns.contains('config.i18n.custom.properties')
    }

    void 'a slash-form base name passes through unchanged'() {
        expect: 'MessageSourceProperties documents relaxed support for slash based locations'
        patternsFor('config/i18n/custom').containsAll([
                'config/i18n/custom.properties', 'config/i18n/custom_*.properties'])
    }

    void 'a base name an application configured itself is covered too'() {
        expect: 'reading the effective property, not the descriptors, is what closes this gap'
        patternsFor('messages', 'config.i18n.custom').containsAll([
                'messages.properties', 'config/i18n/custom.properties'])
    }

    void 'a classpath-prefixed base name is rejected rather than silently mis-registered'() {
        when: 'ResourceBundleMessageSource cannot resolve a classpath: prefix'
        patternsFor('classpath:messages')

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("use 'messages' instead")
    }

    void 'the default base name applies when nothing is configured'() {
        expect:
        patternsFor().containsAll(['messages.properties', 'messages_*.properties'])
    }

    void 'no environment means no contribution rather than a failure'() {
        expect: 'AOT processing outside an application context has nothing to derive hints from'
        new I18nRuntimeHintsProcessor().processAheadOfTime(new DefaultListableBeanFactory()) == null
    }
}
