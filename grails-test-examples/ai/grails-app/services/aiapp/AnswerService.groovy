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

import reactor.core.publisher.Flux

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatResponse

import grails.compiler.GrailsCompileStatic

/**
 * Asks the model the way an application service does: through the auto-configured {@code ChatClient.Builder}.
 */
@GrailsCompileStatic
class AnswerService {

    ChatClient.Builder chatClientBuilder

    String answer(String question) {
        chatClient().prompt().user(question).call().content()
    }

    Flux<String> stream(String question) {
        chatClient().prompt().user(question).stream().content()
    }

    Flux<ChatResponse> streamResponses(String question) {
        chatClient().prompt().user(question).stream().chatResponse()
    }

    private ChatClient chatClient() {
        chatClientBuilder.defaultSystem('You answer briefly.').build()
    }

}
