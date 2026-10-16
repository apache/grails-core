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
package org.apache.grails.testing.ai

import java.lang.reflect.Modifier

import org.springframework.ai.model.SpringAIModelProperties
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import spock.lang.Specification

class AiStubEnvironmentPostProcessorSpec extends Specification {

    AiStubEnvironmentPostProcessor postProcessor = new AiStubEnvironmentPostProcessor()
    StandardEnvironment environment = new StandardEnvironment()

    void setup() {
        environment.propertySources.addFirst(new MapPropertySource('grailsEnvironment', ['grails.env': 'test']))
    }

    void 'selects no provider for any kind of model, nor a vector store, by default'() {
        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('spring.ai.model.chat') == 'none'
        environment.getProperty('spring.ai.model.embedding') == 'none'
        environment.getProperty('spring.ai.model.embedding.text') == 'none'
        environment.getProperty('spring.ai.model.embedding.multimodal') == 'none'
        environment.getProperty('spring.ai.model.image') == 'none'
        environment.getProperty('spring.ai.model.audio.transcription') == 'none'
        environment.getProperty('spring.ai.model.audio.speech') == 'none'
        environment.getProperty('spring.ai.model.moderation') == 'none'
        environment.getProperty('spring.ai.vectorstore.type') == 'none'
        environment.propertySources.stream().toList().last().name == AiStubEnvironmentPostProcessor.PROPERTY_SOURCE_NAME
    }

    void 'the selection properties are every model selection property Spring AI declares, and the vector store'() {
        given: 'the selection properties of the Spring AI version on the classpath, so a new kind of model fails this'
        Set<String> declared = SpringAIModelProperties.declaredFields
                .findAll { Modifier.isStatic(it.modifiers) && it.type == String && it.name != 'MODEL_PREFIX' }
                .collect { (String) it.get(null) }
                .toSet()

        expect:
        declared.every { it.startsWith(SpringAIModelProperties.MODEL_PREFIX + '.') }
        AiStubEnvironmentPostProcessor.SELECTION_PROPERTIES.toSet() == declared + ['spring.ai.vectorstore.type']
    }

    void 'the stubs stay off outside the test environment, and say so'() {
        given:
        environment.propertySources.remove('grailsEnvironment')
        environment.propertySources.addFirst(new MapPropertySource('grailsEnvironment', ['grails.env': grailsEnv]))

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('spring.ai.model.chat') == null
        environment.getProperty('grails.ai.stub.enabled', Boolean) == false
        environment.propertySources.contains(AiStubEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)

        where:
        grailsEnv << ['production', 'development']
    }

    void 'the test environment is recognised from the active Spring profile when grails.env is not set'() {
        given:
        environment.propertySources.remove('grailsEnvironment')
        environment.addActiveProfile('test')

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('spring.ai.model.chat') == 'none'
    }

    void 'neither grails.env nor a profile means not the test environment'() {
        given:
        environment.propertySources.remove('grailsEnvironment')

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('spring.ai.model.chat') == null
        environment.getProperty('grails.ai.stub.enabled', Boolean) == false
    }

    void 'the stubs can be enabled explicitly outside the test environment'() {
        given:
        environment.propertySources.remove('grailsEnvironment')
        environment.propertySources.addFirst(new MapPropertySource('application', ['grails.env': 'development', 'grails.ai.stub.enabled': 'true']))

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('spring.ai.model.chat') == 'none'
    }

    void 'a provider the application selects wins over the default'() {
        given:
        environment.propertySources.addFirst(new MapPropertySource('application',
                ['spring.ai.model.chat': 'openai', 'spring.ai.model.image': 'openai', 'spring.ai.vectorstore.type': 'pgvector']))

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('spring.ai.model.chat') == 'openai'
        environment.getProperty('spring.ai.model.image') == 'openai'
        environment.getProperty('spring.ai.model.embedding') == 'none'
        environment.getProperty('spring.ai.model.moderation') == 'none'
        environment.getProperty('spring.ai.vectorstore.type') == 'pgvector'
    }

    void 'does nothing when the stubs are disabled'() {
        given:
        environment.propertySources.addFirst(new MapPropertySource('application', ['grails.ai.stub.enabled': 'false']))

        when:
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.getProperty('spring.ai.model.chat') == null
        !environment.propertySources.contains(AiStubEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)
    }

    void 'adds its defaults once'() {
        when:
        postProcessor.postProcessEnvironment(environment, null)
        postProcessor.postProcessEnvironment(environment, null)

        then:
        environment.propertySources.stream().filter { it.name == AiStubEnvironmentPostProcessor.PROPERTY_SOURCE_NAME }.count() == 1
    }

    void 'runs after the configuration has been loaded'() {
        expect:
        postProcessor.order == Integer.MAX_VALUE
    }

}
