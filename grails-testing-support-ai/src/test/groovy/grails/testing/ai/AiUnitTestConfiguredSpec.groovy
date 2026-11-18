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

import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.embedding.EmbeddingModel
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
 * The configuration a specification sets reaches the stubs, and a bean it defines itself replaces only
 * the stub of its own type.
 */
class AiUnitTestConfiguredSpec extends Specification implements ServiceUnitTest<AnswerService>, AiUnitTest {

    static final ScriptedChatModel OWN_CHAT_MODEL = new ScriptedChatModel()

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('ownChatModel', ScriptedChatModel) { it.supplier { OWN_CHAT_MODEL } }
        } as BeanRegistrar
    }

    @Override
    Closure doWithConfig() {
        { config -> config['grails.ai.stub.embeddingDimensions'] = 16 }
    }

    void 'a chat model the specification defines is used instead of the stub'() {
        given:
        respondWith('mine')

        expect:
        chatModel.is(OWN_CHAT_MODEL)
        applicationContext.getBeansOfType(ChatModel).keySet() == ['ownChatModel'] as Set
        !applicationContext.containsBean('chatModel')
        chatClient.prompt().user('whose?').call().content() == 'mine'
        service.answer('and the service?') == 'and the service?'
        OWN_CHAT_MODEL.prompts*.contents == ['whose?', 'and the service?']
    }

    void 'the specification-defined model is reset before every feature method too'() {
        expect:
        OWN_CHAT_MODEL.prompts.empty
    }

    void 'the stub embedding model takes its dimensions from the configuration'() {
        expect:
        embeddingModel.dimensions() == 16
        applicationContext.getBean(EmbeddingModel).dimensions() == 16
        embeddingModel.embed('a few words').length == 16
    }

    void 'the stubs the specification does not define are still registered'() {
        expect:
        applicationContext.getBean(EmbeddingModel) instanceof StubEmbeddingModel
        applicationContext.getBean(VectorStore) instanceof ResettableSimpleVectorStore
    }

}
