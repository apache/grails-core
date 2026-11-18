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

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

/**
 * An embedding model that computes a vector from the words of the text instead of calling a provider.
 *
 * <p>The vector is deterministic, so the same text always embeds the same way, and it is a unit vector
 * built from the words of the text by feature hashing, so texts that share words are closer than texts
 * that share none. The vector is never zero, since a vector store cannot compare a zero vector: text
 * without a word, and text whose words happen to cancel one another out, embed to a fixed unit vector.
 * That is enough for a vector store in a unit test to find what was added to it.</p>
 *
 * @since 8.1
 */
@CompileStatic
class StubEmbeddingModel implements EmbeddingModel {

    public static final int DEFAULT_DIMENSIONS = 64

    private static final int HASHES_PER_WORD = 3
    private static final int GOLDEN_RATIO = -1640531527 // 0x9E3779B9
    private static final int MIX_1 = -2048144789 // 0x85EBCA6B
    private static final int MIX_2 = -1028477387 // 0xC2B2AE35

    private final int dimensions

    StubEmbeddingModel() {
        this(DEFAULT_DIMENSIONS)
    }

    StubEmbeddingModel(int dimensions) {
        if (dimensions < 1) {
            throw new IllegalArgumentException('An embedding needs at least one dimension')
        }
        this.dimensions = dimensions
    }

    @Override
    EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = []
        request.instructions.eachWithIndex { String text, int index ->
            embeddings << new Embedding(embed(text), index)
        }
        new EmbeddingResponse(embeddings)
    }

    @Override
    float[] embed(Document document) {
        // getText() by name: Document also has isText(), which the text property would otherwise resolve to
        String text = document.getText()
        embed(text == null ? '' : text)
    }

    @Override
    float[] embed(String text) {
        float[] vector = new float[dimensions]
        for (String word : text.toLowerCase(Locale.ROOT).split('(?U)\\W+')) {
            if (word) {
                addWord(vector, word)
            }
        }
        double length = 0
        for (float component : vector) {
            length += component * component
        }
        if (length == 0) {
            // no words, or words whose hashed components cancelled out: a fixed unit vector, never a zero one
            vector[0] = 1
            return vector
        }
        float norm = (float) Math.sqrt(length)
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm)
        }
        vector
    }

    @Override
    int dimensions() {
        dimensions
    }

    /**
     * Adds a word to a few components, each with a sign of its own, so two words that happen to share a
     * component rarely look alike.
     */
    private void addWord(float[] vector, String word) {
        for (int seed = 0; seed < HASHES_PER_WORD; seed++) {
            int hash = mix(word.hashCode() + seed * GOLDEN_RATIO)
            int component = Math.floorMod(hash >>> 1, dimensions)
            vector[component] += (hash & 1) == 0 ? 1 : -1
        }
    }

    private static int mix(int value) {
        int hash = value
        hash ^= hash >>> 16
        hash *= MIX_1
        hash ^= hash >>> 13
        hash *= MIX_2
        hash ^ (hash >>> 16)
    }

}
