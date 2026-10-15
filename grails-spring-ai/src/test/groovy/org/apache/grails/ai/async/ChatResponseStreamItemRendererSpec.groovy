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
package org.apache.grails.ai.async

import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation

import org.grails.plugins.web.async.mvc.DefaultStreamItemRenderer
import org.grails.plugins.web.async.mvc.ServerSentEventFormatter
import spock.lang.Specification

class ChatResponseStreamItemRendererSpec extends Specification {

    ChatResponseStreamItemRenderer renderer = new ChatResponseStreamItemRenderer()

    void 'renders the generated text of a chat response'() {
        expect:
        renderer.render(chatResponse('Hello')) == 'Hello'
        renderer.render(chatResponse(' world')) == ' world'
        renderer.render(new ChatResponse([])) == ''
    }

    void 'renders the chat response inside a chat client response'() {
        expect:
        renderer.render(new ChatClientResponse(chatResponse('Hi'), [:])) == 'Hi'
        renderer.render(new ChatClientResponse(null, [:])) == ''
    }

    void 'renders the text of a message'() {
        expect:
        renderer.render(new UserMessage('question')) == 'question'
        renderer.render(new AssistantMessage('answer')) == 'answer'
    }

    void 'leaves anything else to the next renderer'() {
        expect:
        renderer.render(item) == null

        where:
        item << ['text', 42, [a: 1], null]
    }

    void 'a streamed chat response becomes the data of a server-sent event'() {
        given:
        ServerSentEventFormatter formatter = new ServerSentEventFormatter([renderer, new DefaultStreamItemRenderer()])

        expect:
        formatter.format(chatResponse('two\nlines')) == 'data: two\ndata: lines\n\n'
        formatter.format('plain') == 'data: plain\n\n'
    }

    private static ChatResponse chatResponse(String text) {
        new ChatResponse([new Generation(new AssistantMessage(text))])
    }

}
