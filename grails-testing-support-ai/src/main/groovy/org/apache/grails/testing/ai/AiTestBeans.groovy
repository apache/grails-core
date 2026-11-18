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
package org.apache.grails.testing.ai

import groovy.transform.CompileStatic

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import org.springframework.util.ClassUtils

/**
 * The beans a unit test gets from {@code AiUnitTest}: the scripted chat model, the stub embedding
 * model, a {@code ChatClient.Builder} over the chat model, and an in-memory vector store when
 * Spring AI's vector store module is on the classpath.
 *
 * <p>Each is registered only when the test context holds no bean of its type yet, so a model, builder
 * or store the specification defines itself is kept, as an application's own bean makes the
 * auto-configured stub back off in an integration test. The stub embedding model takes its dimensions
 * from {@code grails.ai.stub.embeddingDimensions}, as the auto-configured one does.</p>
 *
 * @since 8.1
 */
@CompileStatic
class AiTestBeans {

    public static final String CHAT_MODEL = 'chatModel'
    public static final String EMBEDDING_MODEL = 'embeddingModel'
    public static final String CHAT_CLIENT_BUILDER = 'chatClientBuilder'
    public static final String VECTOR_STORE = 'vectorStore'

    public static final String SIMPLE_VECTOR_STORE = 'org.springframework.ai.vectorstore.SimpleVectorStore'

    /**
     * @param existing the beans the test context already holds, which the stubs give way to
     */
    static BeanRegistrar registrar(ListableBeanFactory existing) {
        boolean chatModel = existing.getBeanNamesForType(ChatModel).length == 0
        boolean embeddingModel = existing.getBeanNamesForType(EmbeddingModel).length == 0
        boolean chatClientBuilder = existing.getBeanNamesForType(ChatClient.Builder).length == 0
        boolean vectorStore = ClassUtils.isPresent(SIMPLE_VECTOR_STORE, AiTestBeans.classLoader) &&
                SimpleVectorStoreBeans.isMissing(existing)
        return { BeanRegistry registry, Environment environment ->
            if (chatModel) {
                registry.registerBean(CHAT_MODEL, ScriptedChatModel) { it.primary() }
            }
            if (embeddingModel) {
                int dimensions = embeddingDimensions(environment)
                registry.registerBean(EMBEDDING_MODEL, StubEmbeddingModel) {
                    it.primary()
                    it.supplier { new StubEmbeddingModel(dimensions) }
                }
            }
            if (chatClientBuilder) {
                registry.registerBean(CHAT_CLIENT_BUILDER, ChatClient.Builder) {
                    it.prototype()
                    it.supplier { context -> ChatClient.builder(context.bean(ChatModel)) }
                }
            }
            if (vectorStore) {
                SimpleVectorStoreBeans.register(registry)
            }
        } as BeanRegistrar
    }

    /**
     * @return the dimensions {@code grails.ai.stub.embeddingDimensions} sets, or the stub's default
     */
    static int embeddingDimensions(Environment environment) {
        Binder.get(environment).bind(AiStubProperties.PREFIX, AiStubProperties)
                .orElseGet { -> new AiStubProperties() }
                .embeddingDimensions
    }

}
