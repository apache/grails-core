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

import groovy.transform.CompileStatic

import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.core.Ordered

import grails.async.web.StreamItemRenderer

/**
 * Renders the chat responses a controller action streams as the text the model generated, so an action
 * can return {@code chatClient.prompt().user(question).stream().chatResponse()} and the client receives
 * the answer token by token.
 *
 * <p>Consulted before the grails-async defaults; anything that is not a chat response or a message is
 * left to them.</p>
 *
 * @since 8.1
 */
@CompileStatic
class ChatResponseStreamItemRenderer implements StreamItemRenderer, Ordered {

    public static final int ORDER = 0

    @Override
    String render(Object item) {
        if (item instanceof ChatClientResponse) {
            return textOf(((ChatClientResponse) item).chatResponse())
        }
        if (item instanceof ChatResponse) {
            return textOf((ChatResponse) item)
        }
        if (item instanceof Message) {
            return ((Message) item).text ?: ''
        }
        return null
    }

    @Override
    int getOrder() {
        ORDER
    }

    private static String textOf(ChatResponse response) {
        response?.result?.output?.text ?: ''
    }

}
