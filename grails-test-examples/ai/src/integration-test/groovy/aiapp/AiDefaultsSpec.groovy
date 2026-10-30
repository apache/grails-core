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
package aiapp

import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.ai.ScriptedChatModel
import org.apache.grails.testing.ai.StubEmbeddingModel

/**
 * What the plugin's defaults and the testing support do to a running application in the test environment.
 */
@Integration
class AiDefaultsSpec extends Specification {

    @Autowired
    Environment environment

    @Autowired
    ApplicationContext applicationContext

    void 'the plugin defaults reach the environment, below the application configuration'() {
        expect:
        environment.getProperty('spring.ai.retry.max-attempts', Integer) == 3
        environment.getProperty('spring.ai.ollama.base-url') == 'http://localhost:11434'
    }

    void 'Spring AI binds the retry default the plugin ships'() {
        given:
        Object retryProperties = applicationContext.getBean(Class.forName('org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties'))

        expect:
        retryProperties.maxAttempts == 3
    }

    void 'prompts are not logged outside the development environment'() {
        expect:
        environment.getProperty('spring.ai.chat.client.observations.log-prompt') == null
        environment.getProperty('spring.ai.chat.observations.include-error-logging') == null
    }

    void 'the stubs stand in for the provider, which backs off without credentials or a server'() {
        expect:
        environment.getProperty('spring.ai.model.chat') == 'none'
        environment.getProperty('spring.ai.model.embedding') == 'none'
        applicationContext.getBean(ChatModel) instanceof ScriptedChatModel
        applicationContext.getBean(EmbeddingModel) instanceof StubEmbeddingModel
        applicationContext.getBeanNamesForType(Class.forName('org.springframework.ai.ollama.OllamaChatModel')).length == 0
    }

    void 'the stubs are fallback beans, so a model the application defines would be the one injected'() {
        expect:
        ((ConfigurableApplicationContext) applicationContext).beanFactory.getBeanDefinition('chatModel').fallback
        ((ConfigurableApplicationContext) applicationContext).beanFactory.getBeanDefinition('embeddingModel').fallback
    }

}
