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

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner

import grails.async.web.StreamItemRenderer
import org.apache.grails.ai.async.ChatResponseStreamItemRenderer
import org.grails.plugins.web.async.mvc.DefaultStreamItemRenderer
import spock.lang.Specification

class SpringAiAutoConfigurationSpec extends Specification {

    private ApplicationContextRunner contextRunner() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SpringAiAutoConfiguration))
    }

    void 'registers the chat response renderer for streamed actions when grails-async is present'() {
        expect:
        contextRunner().run { context ->
            StreamItemRenderer renderer = context.getBean('aiChatResponseStreamItemRenderer', StreamItemRenderer)
            assert renderer instanceof ChatResponseStreamItemRenderer
            assert renderer.render(new ChatResponse([new Generation(new AssistantMessage('Hello'))])) == 'Hello'
        }
    }

    void 'the chat response renderer sorts ahead of the grails-async defaults'() {
        expect:
        new ChatResponseStreamItemRenderer().order < new DefaultStreamItemRenderer().order
    }

    void 'a renderer the application defines makes the auto-configured one back off'() {
        given:
        ChatResponseStreamItemRenderer custom = new ChatResponseStreamItemRenderer()

        expect:
        contextRunner().withBean('myRenderer', ChatResponseStreamItemRenderer, { -> custom })
                .run { context ->
                    assert !context.containsBean('aiChatResponseStreamItemRenderer')
                    assert context.getBean(ChatResponseStreamItemRenderer).is(custom)
                }
    }

    void 'nothing is registered for streaming when grails-async is absent'() {
        expect:
        contextRunner().withClassLoader(new FilteredClassLoader(StreamItemRenderer))
                .run { context ->
                    assert !context.containsBean('aiChatResponseStreamItemRenderer')
                    assert context.getBeanNamesForType(Object).every { !it.contains('Streaming') }
                }
    }

}
