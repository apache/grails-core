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
package org.apache.grails.ai

import ch.qos.logback.classic.Level

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

import org.apache.grails.core.testing.support.LogCapture
import spock.lang.Specification

/**
 * The prompt and completion logging the plugin turns on in development goes through Spring AI's
 * observation handlers, which only run when an {@code ObservationRegistry} bean observes the chat client.
 * The plugin brings Spring Boot's auto-configuration of that registry, so the settings take effect.
 */
class SpringAiPromptLoggingSpec extends Specification {

    private static final String PROMPT_HANDLER = 'org.springframework.ai.chat.client.observation.ChatClientPromptContentObservationHandler'
    private static final String COMPLETION_HANDLER = 'org.springframework.ai.chat.client.observation.ChatClientCompletionObservationHandler'

    private static final ChatModel ANSWERING_MODEL = { Prompt prompt ->
        new ChatResponse([new Generation(new AssistantMessage('42'))])
    } as ChatModel

    private ApplicationContextRunner contextRunner() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration, ChatClientAutoConfiguration))
                .withBean('chatModel', ChatModel, { -> ANSWERING_MODEL })
    }

    void 'the development defaults log the prompt and the completion of a chat client call'() {
        expect:
        contextRunner()
                .withPropertyValues('spring.ai.chat.client.observations.log-prompt=true',
                        'spring.ai.chat.client.observations.log-completion=true')
                .run { context ->
                    try (LogCapture prompts = new LogCapture(PROMPT_HANDLER, Level.INFO);
                         LogCapture completions = new LogCapture(COMPLETION_HANDLER, Level.INFO)) {
                        assert context.getBean(ChatClient.Builder).build().prompt().user('What is six times seven?').call().content() == '42'
                        assert prompts.events*.formattedMessage.any { it.contains('What is six times seven?') }
                        assert completions.events*.formattedMessage.any { it.contains('42') }
                    }
                }
    }

    void 'nothing is logged when the settings are off, as they are outside development'() {
        expect:
        contextRunner().run { context ->
            try (LogCapture prompts = new LogCapture(PROMPT_HANDLER, Level.INFO);
                 LogCapture completions = new LogCapture(COMPLETION_HANDLER, Level.INFO)) {
                assert context.getBean(ChatClient.Builder).build().prompt().user('What is six times seven?').call().content() == '42'
                assert prompts.events.empty
                assert completions.events.empty
            }
        }
    }

    void 'the plugin brings the auto-configuration of the observation registry the handlers need'() {
        expect: 'the auto-configuration resolves from the plugin runtime classpath, on which this test runs'
        Class.forName('org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration')
    }

}
