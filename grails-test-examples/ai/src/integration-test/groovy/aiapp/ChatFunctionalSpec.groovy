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

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.ai.ScriptedChatModel
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * The chat client answers through the stubbed model in the test environment, synchronously and streamed
 * to the client as server-sent events.
 */
@Integration
class ChatFunctionalSpec extends Specification implements HttpClientSupport {

    private static final String COMPLETE = 'event: complete\ndata: \n\n'

    @Autowired
    ScriptedChatModel chatModel

    void setup() {
        chatModel.reset()
    }

    void 'a service answers through the chat client'() {
        given:
        chatModel.respondWith('42')

        when:
        def response = http('/chat/ask?question=What+is+six+times+seven%3F')

        then:
        response.assertStatus(200)
        response.body() == '42'
        chatModel.lastUserText == 'What is six times seven?'
        chatModel.lastPrompt.systemMessage.text == 'You answer briefly.'
    }

    void 'a streamed answer arrives token by token'() {
        given:
        chatModel.respondWith('Hello streaming world')

        when:
        def response = http('/chat/stream?question=hello')

        then:
        response.assertStatus(200)
        response.contentType.startsWith('text/event-stream')
        response.body() == 'data: Hello \n\ndata: streaming \n\ndata: world\n\n' + COMPLETE
    }

    void 'streamed chat responses are rendered as the text they carry'() {
        given:
        chatModel.respondWith('Hello streaming world')

        expect:
        http('/chat/streamResponses?question=hello').assertStatus(200).body() == 'data: Hello \n\ndata: streaming \n\ndata: world\n\n' + COMPLETE
    }

}
