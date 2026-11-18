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

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.SimpleVectorStore

import spock.lang.Specification

class StubEmbeddingModelSpec extends Specification {

    void 'embeds with the configured number of dimensions'() {
        expect:
        new StubEmbeddingModel().dimensions() == StubEmbeddingModel.DEFAULT_DIMENSIONS
        new StubEmbeddingModel().embed('text').length == StubEmbeddingModel.DEFAULT_DIMENSIONS
        new StubEmbeddingModel(16).dimensions() == 16
        new StubEmbeddingModel(16).embed('text').length == 16
    }

    void 'needs at least one dimension'() {
        when:
        new StubEmbeddingModel(0)

        then:
        thrown(IllegalArgumentException)
    }

    void 'embeds the same text the same way every time'() {
        given:
        StubEmbeddingModel model = new StubEmbeddingModel()

        expect:
        model.embed('hello world') == model.embed('hello world')
        model.embed('Hello, World!') == model.embed('hello world')
    }

    void 'always produces a unit vector, even for text without words'() {
        given:
        StubEmbeddingModel model = new StubEmbeddingModel()

        expect:
        Math.abs(squaredLength(model.embed(text)) - 1) < 1e-5

        where:
        text << ['hello world', '', '   ', '...', '日本語のテキスト', 'café']
    }

    void 'words are Unicode words, not ASCII ones'() {
        given:
        StubEmbeddingModel model = new StubEmbeddingModel()

        expect:
        model.embed('café') != model.embed('caf')
        model.embed('日本語') != model.embed('')
    }

    void 'a store holding a document without words can still be searched'() {
        given:
        SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel()).build()
        store.add([new Document(''), new Document('Grails is a web framework')])

        expect:
        store.similaritySearch(SearchRequest.builder().query('web framework').topK(1).build())*.getText() == ['Grails is a web framework']
    }

    void 'texts that share words are closer than texts that share none'() {
        given:
        StubEmbeddingModel model = new StubEmbeddingModel(64)
        float[] reference = model.embed('hello world')

        expect:
        dot(reference, model.embed('hello there')) > dot(reference, model.embed('completely different'))
    }

    void 'embeds every text of a request at its index'() {
        given:
        StubEmbeddingModel model = new StubEmbeddingModel()

        when:
        EmbeddingResponse response = model.call(new EmbeddingRequest(['first', 'second'], null))

        then:
        response.results*.index == [0, 1]
        response.results[0].output == model.embed('first')
        response.results[1].output == model.embed('second')
    }

    void 'embeds the text of a document'() {
        given:
        StubEmbeddingModel model = new StubEmbeddingModel()

        expect:
        model.embed(new Document('some text')) == model.embed('some text')
    }

    void 'lets an in-memory vector store find what was added to it'() {
        given:
        SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel(64)).build()
        store.add([new Document('Grails is a web framework'), new Document('Bananas are yellow fruit')])

        expect:
        store.similaritySearch(SearchRequest.builder().query('web framework').topK(1).build())*.getText() == ['Grails is a web framework']
    }

    private static double squaredLength(float[] vector) {
        double length = 0
        for (float component : vector) {
            length += component * component
        }
        length
    }

    private static double dot(float[] left, float[] right) {
        double product = 0
        for (int i = 0; i < left.length; i++) {
            product += left[i] * right[i]
        }
        product
    }

    void 'words whose hashed components cancel out still embed to a unit vector, never a zero one'() {
        given: 'one dimension, where every word lands on the same component with a weight of its own, so pairs of opposite sign and equal weight cancel'
        StubEmbeddingModel narrow = new StubEmbeddingModel(1)
        List<String> words = (1..40).collect { "word${it}".toString() }
        List<List<String>> pairs = [words, words].combinations().findAll { List<String> pair -> pair[0] < pair[1] }

        expect: 'both signs occur, so some pairs cancel'
        words.any { narrow.embed(it)[0] == 1f }
        words.any { narrow.embed(it)[0] == -1f }

        and: 'every pair, the cancelling ones included, is a unit vector'
        pairs.every { List<String> pair -> narrow.embed(pair.join(' '))[0] in [1f, -1f] }
    }

}
