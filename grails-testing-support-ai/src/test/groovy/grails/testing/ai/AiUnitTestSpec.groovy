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
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore

import grails.testing.services.ServiceUnitTest
import org.apache.grails.testing.ai.StubEmbeddingModel
import spock.lang.Specification

class AiUnitTestSpec extends Specification implements ServiceUnitTest<AnswerService>, AiUnitTest {

    void 'the chat client answers with the scripted responses and records the prompts'() {
        given:
        respondWith('42')

        expect:
        chatClient.prompt().user('What is six times seven?').call().content() == '42'
        prompts.size() == 1
        prompts.first().userMessage.text == 'What is six times seven?'
    }

    void 'the script and the prompts are cleared before every feature method'() {
        expect:
        prompts.empty
        chatClient.prompt().user('fresh').call().content() == 'fresh'
    }

    void 'an answer may be scripted as a function of the prompt'() {
        given:
        respondWith { Prompt prompt -> "You asked: ${prompt.userMessage.text}".toString() }

        expect:
        chatClient.prompt().user('why?').call().content() == 'You asked: why?'
    }

    void 'the chat client streams the scripted response'() {
        given:
        respondWith('Hello streaming world')

        expect:
        chatClient.prompt().user('x').stream().content().collectList().block() == ['Hello ', 'streaming ', 'world']
    }

    void 'a service under test gets the chat client builder by injection'() {
        given:
        respondWith('Paris')

        expect:
        service.answer('Capital of France?') == 'Paris'
        chatModel.lastUserText == 'Capital of France?'
    }

    void 'the stubs are the primary models of the test context, under the names an application uses'() {
        expect:
        applicationContext.getBean(ChatModel).is(chatModel)
        applicationContext.getBean(EmbeddingModel).is(embeddingModel)
        applicationContext.getBean('chatModel').is(chatModel)
        applicationContext.getBean('embeddingModel').is(embeddingModel)
        applicationContext.getBean('chatClientBuilder') instanceof ChatClient.Builder
        embeddingModel.dimensions() == StubEmbeddingModel.DEFAULT_DIMENSIONS
    }

    void 'the model can be made strict for a test that must not run out of script'() {
        given:
        chatModel.strict = true

        when:
        chatClient.prompt().user('nothing scripted').call().content()

        then:
        thrown(IllegalStateException)
    }

    void 'an in-memory vector store is available when the vector store module is on the classpath'() {
        given:
        VectorStore vectorStore = applicationContext.getBean('vectorStore', VectorStore)
        vectorStore.add([new Document('Grails is a web framework'), new Document('Bananas are yellow fruit')])

        expect:
        vectorStore instanceof SimpleVectorStore
        vectorStore.similaritySearch(SearchRequest.builder().query('web framework').topK(1).build())*.getText() == ['Grails is a web framework']
    }

    void 'the vector store is emptied before every feature method'() {
        expect:
        applicationContext.getBean('vectorStore', VectorStore).similaritySearch(SearchRequest.builder().query('web framework').topK(5).build()).empty
    }

}
