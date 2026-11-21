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

package org.grails.forge.feature.ai

import groovy.yaml.YamlSlurper
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Category
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.feature.Features
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options

class GrailsSpringAiSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "the grails-spring-ai feature is available in the AI category"() {
        when:
        final Features features = getFeatures(['grails-spring-ai'])

        then:
        features.contains('grails-spring-ai')
        featureNamed('grails-spring-ai').category == Category.AI
    }

    void "grails-spring-ai adds the plugin and its testing support at the versions the grails-bom manages"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(['grails-spring-ai'])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-spring-ai"')
        template.contains('testImplementation "org.apache.grails:grails-testing-support-ai"')
        !template.contains('org.apache.grails:grails-spring-ai:')
        !template.contains('org.apache.grails:grails-testing-support-ai:')
    }

    void "grails-spring-ai chooses no provider on its own"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['grails-spring-ai'])
        final def config = new YamlSlurper().parseText(output["grails-app/conf/application.yml"])

        then:
        !output["build.gradle"].contains('org.springframework.ai:')
        config['spring.ai.openai.api-key'] == null
    }

    void "the #feature provider pulls in grails-spring-ai and the #starter starter"() {
        when:
        final Features features = getFeatures([feature])
        final String template = new BuildBuilder(beanContext)
                .features([feature])
                .render()

        then:
        features.contains('grails-spring-ai')
        featureNamed(feature).category == Category.AI
        template.contains('implementation "org.apache.grails:grails-spring-ai"')
        template.contains("implementation \"org.springframework.ai:${starter}\"")
        !template.contains("org.springframework.ai:${starter}:")

        where:
        feature                | starter
        'spring-ai-openai'      | 'spring-ai-starter-model-openai'
        'spring-ai-anthropic'   | 'spring-ai-starter-model-anthropic'
        'spring-ai-ollama'      | 'spring-ai-starter-model-ollama'
    }

    void "the Ollama provider configures where Ollama listens"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['spring-ai-ollama'])
        final def config = new YamlSlurper().parseText(output["grails-app/conf/application.yml"])

        then:
        config['spring.ai.ollama.base-url'] == 'http://localhost:11434'
    }

    void "the #feature provider writes no API key: Spring AI reads #variable from the environment, and an unset placeholder would be sent as the key"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), [feature])
        final String config = output["grails-app/conf/application.yml"]

        then:
        !config.contains('api-key')
        !config.contains(variable)

        where:
        feature                        | variable
        'spring-ai-openai'    | 'OPENAI_API_KEY'
        'spring-ai-anthropic' | 'ANTHROPIC_API_KEY'
    }

    void "a provider is a choice an application makes, not one a plugin imposes on its applications"() {
        expect:
        providers().every { it.supports(ApplicationType.WEB) && it.supports(ApplicationType.REST_API) }
        providers().every { !it.supports(ApplicationType.PLUGIN) && !it.supports(ApplicationType.WEB_PLUGIN) && !it.supports(ApplicationType.REST_API_PLUGIN) }
        featureNamed('grails-spring-ai').supports(ApplicationType.PLUGIN)
    }

    void "only one Spring AI provider can be selected"() {
        when:
        getFeatures(['spring-ai-openai', 'spring-ai-anthropic'])

        then:
        IllegalArgumentException failure = thrown()
        failure.message.contains('There can only be one of the following features selected')
        failure.message.contains('spring-ai-openai')
        failure.message.contains('spring-ai-anthropic')
    }

    void "every provider is one of the same group"() {
        expect:
        providers()*.name.toSet() ==
                ['spring-ai-openai', 'spring-ai-anthropic', 'spring-ai-ollama'].toSet()
        providers().every { it.group == 'spring-ai-provider' }
    }

    void "a provider is selected once even when grails-spring-ai is selected as well"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(['grails-spring-ai', 'spring-ai-ollama'])
                .render()

        then:
        template.count('implementation "org.apache.grails:grails-spring-ai"') == 1
        template.count('implementation "org.springframework.ai:spring-ai-starter-model-ollama"') == 1
    }


    private Feature featureNamed(String name) {
        return beanContext.getBean(FeatureRegistry).all().find { it.name == name }
    }

    private List<OneOfFeature> providers() {
        return beanContext.getBean(FeatureRegistry).all()
                .findAll { it instanceof OneOfFeature && ((OneOfFeature) it).group == 'spring-ai-provider' } as List<OneOfFeature>
    }
}
