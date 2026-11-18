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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.ai.ScriptedChatModel
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * A chat model the application defines itself is the one the chat client uses, although it is
 * registered after the stub's conditions were evaluated: the stub is a fallback bean.
 */
@Integration(applicationClass = OwnModelApplication)
class OwnChatModelFunctionalSpec extends Specification implements HttpClientSupport {

    @Autowired
    ConfigurableApplicationContext applicationContext

    void 'a chat model the application defines wins over the stub, which stays as a fallback'() {
        given:
        ScriptedChatModel own = applicationContext.getBean('ownChatModel', ScriptedChatModel)
        ScriptedChatModel stub = applicationContext.getBean('chatModel', ScriptedChatModel)
        own.reset()
        stub.reset()
        own.respondWith('from the application model')

        when:
        def response = http('/chat/ask?question=whose')

        then:
        applicationContext.beanFactory.getBeanDefinition('chatModel').fallback
        applicationContext.getBean(ChatModel).is(own)
        response.statusCode() == 200
        response.body() == 'from the application model'
        own.lastUserText == 'whose'
        stub.prompts.empty
    }

}
