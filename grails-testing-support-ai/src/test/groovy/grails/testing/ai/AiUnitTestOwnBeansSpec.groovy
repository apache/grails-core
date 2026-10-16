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

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.testing.services.ServiceUnitTest
import org.apache.grails.testing.ai.ResettableSimpleVectorStore
import org.apache.grails.testing.ai.ScriptedChatModel
import org.apache.grails.testing.ai.StubEmbeddingModel
import spock.lang.Specification

/**
 * Every bean a specification defines itself, of each type the stubs cover, is used instead of the stub.
 */
class AiUnitTestOwnBeansSpec extends Specification implements ServiceUnitTest<AnswerService>, AiUnitTest {

    static final ScriptedChatModel OWN_CHAT_MODEL = new ScriptedChatModel()
    static final StubEmbeddingModel OWN_EMBEDDING_MODEL = new StubEmbeddingModel(8)
    static final ResettableSimpleVectorStore OWN_VECTOR_STORE = new ResettableSimpleVectorStore(OWN_EMBEDDING_MODEL)
    static final ChatClient.Builder OWN_BUILDER = ChatClient.builder(OWN_CHAT_MODEL)

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('ownChatModel', ScriptedChatModel) { it.supplier { OWN_CHAT_MODEL } }
            registry.registerBean('ownEmbeddingModel', StubEmbeddingModel) { it.supplier { OWN_EMBEDDING_MODEL } }
            registry.registerBean('ownVectorStore', ResettableSimpleVectorStore) { it.supplier { OWN_VECTOR_STORE } }
            // under the name an application's services are autowired by, as an application defines it
            registry.registerBean('chatClientBuilder', ChatClient.Builder) { it.supplier { OWN_BUILDER } }
        } as BeanRegistrar
    }

    void 'no stub is registered when the specification defines a bean of every type'() {
        expect:
        !applicationContext.containsBean('chatModel')
        !applicationContext.containsBean('embeddingModel')
        !applicationContext.containsBean('vectorStore')
        applicationContext.getBean('chatClientBuilder').is(OWN_BUILDER)
        applicationContext.getBeansOfType(ChatModel).keySet() == ['ownChatModel'] as Set
        applicationContext.getBeansOfType(EmbeddingModel).keySet() == ['ownEmbeddingModel'] as Set
        applicationContext.getBeansOfType(VectorStore).keySet() == ['ownVectorStore'] as Set
        applicationContext.getBeansOfType(ChatClient.Builder).keySet() == ['chatClientBuilder'] as Set
    }

    void 'the trait and the service under test use the specification-defined beans'() {
        given:
        respondWith('mine')

        expect:
        chatModel.is(OWN_CHAT_MODEL)
        embeddingModel.is(OWN_EMBEDDING_MODEL)
        embeddingModel.dimensions() == 8
        chatClient.prompt().user('whose?').call().content() == 'mine'
        service.answer('and the service?') == 'and the service?'
        OWN_CHAT_MODEL.prompts*.contents == ['whose?', 'and the service?']
    }

    void 'the specification-defined store holds what a feature adds'() {
        given:
        OWN_VECTOR_STORE.add([new Document('Grails is a web framework')])

        expect:
        applicationContext.getBean(VectorStore).similaritySearch(SearchRequest.builder().query('web framework').topK(1).build()).size() == 1
    }

    void 'the specification-defined model and store are reset before every feature method too'() {
        expect:
        OWN_CHAT_MODEL.prompts.empty
        OWN_VECTOR_STORE.similaritySearch(SearchRequest.builder().query('web framework').topK(1).build()).empty
    }

}
