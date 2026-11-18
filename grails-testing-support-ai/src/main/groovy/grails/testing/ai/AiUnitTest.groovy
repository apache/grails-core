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
package grails.testing.ai

import java.util.function.Function

import groovy.transform.CompileStatic

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.Prompt

import org.apache.grails.testing.ai.ScriptedChatModel
import org.apache.grails.testing.ai.StubEmbeddingModel
import org.grails.testing.GrailsUnitTest

/**
 * A unit test of code that uses Spring AI, against a scripted chat model and a deterministic embedding
 * model instead of a provider.
 *
 * <p>The test application context holds a {@code chatModel}, an {@code embeddingModel}, a prototype
 * {@code chatClientBuilder} over the scripted model and, when Spring AI's vector store module is on the
 * classpath, an in-memory {@code vectorStore}; a service under test gets them by injection as it would
 * get the real ones. Script the answers with {@link #respondWith(String...)} and assert on
 * {@link #getPrompts()}. The script, the prompts and the vector store are cleared before every feature
 * method.</p>
 *
 * <p>The stubs are registered before the specification's {@code setupSpec}, after the beans of its
 * {@code doWithSpring()} and {@code beanRegistrar()} have been created, and each only when the context
 * holds no bean of its type: a chat model, embedding model, chat client builder or vector store the
 * specification defines is used instead. The chat model then has to be a {@code ScriptedChatModel} for
 * {@link #getChatModel()} and {@link #respondWith(String...)} to find it. A bean defined in
 * {@code doWithSpring()} that needs the builder at creation should take it from the context lazily, or
 * be defined in {@code setupSpec} with {@code defineBeans}.</p>
 *
 * <pre>
 * class AnswerServiceSpec extends Specification implements ServiceUnitTest&lt;AnswerService&gt;, AiUnitTest {
 *
 *     void 'answers with the model'() {
 *         given:
 *         respondWith('42')
 *
 *         expect:
 *         service.answer('What is six times seven?') == '42'
 *         prompts.last().userMessage.text == 'What is six times seven?'
 *     }
 * }
 * </pre>
 *
 * @since 8.1
 */
@CompileStatic
trait AiUnitTest extends GrailsUnitTest {

    /**
     * @return the scripted chat model the test context holds
     */
    ScriptedChatModel getChatModel() {
        applicationContext.getBean(ScriptedChatModel)
    }

    /**
     * @return the stub embedding model the test context holds
     */
    StubEmbeddingModel getEmbeddingModel() {
        applicationContext.getBean(StubEmbeddingModel)
    }

    /**
     * @return a chat client over the chat model, built from the context's {@code ChatClient.Builder}
     */
    ChatClient getChatClient() {
        applicationContext.getBean(ChatClient.Builder).build()
    }

    /**
     * Scripts the next answers of the chat model, in order.
     */
    ScriptedChatModel respondWith(String... responses) {
        chatModel.respondWith(responses)
    }

    /**
     * Scripts the next answer of the chat model as a function of the prompt it answers.
     */
    ScriptedChatModel respondWith(Function<Prompt, String> responder) {
        chatModel.respondWith(responder)
    }

    /**
     * @return every prompt the chat model received in this feature method, oldest first
     */
    List<Prompt> getPrompts() {
        chatModel.prompts
    }

}
