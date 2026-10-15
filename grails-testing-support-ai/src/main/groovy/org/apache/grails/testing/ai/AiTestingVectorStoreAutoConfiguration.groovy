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

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Fallback
import org.springframework.core.Ordered

/**
 * Registers an in-memory vector store over the stub embedding model when Spring AI's vector store
 * module is on the classpath, no vector store is selected and the application defines none, so code
 * that stores and searches documents can be tested without a database. The store is a
 * {@link ResettableSimpleVectorStore}, which a test that shares a context clears between features.
 *
 * <p>The guard on {@code spring.ai.vectorstore.type} matters because this auto-configuration would otherwise
 * be evaluated before Spring AI's own: the stub would be registered first and a store the application
 * selected, such as PgVector, would back off. It is also ordered after every other auto-configuration,
 * so the embedding model it needs exists when its condition is evaluated whether the stub or a provider
 * the application selected supplies it. Like the model stubs, the store is a fallback bean: one the
 * application defines later, in {@code resources.groovy} or {@code doWithSpring}, wins.</p>
 *
 * @since 8.1
 */
@CompileStatic
@AutoConfiguration(after = AiTestingAutoConfiguration)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnClass(SimpleVectorStore)
@ConditionalOnProperty(prefix = AiStubProperties.PREFIX, name = 'enabled', havingValue = 'true', matchIfMissing = true)
class AiTestingVectorStoreAutoConfiguration {

    @Bean
    @Fallback
    @ConditionalOnMissingBean(VectorStore)
    @ConditionalOnBean(EmbeddingModel)
    @ConditionalOnProperty(name = AiStubEnvironmentPostProcessor.VECTOR_STORE, havingValue = AiStubEnvironmentPostProcessor.NONE)
    ResettableSimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        new ResettableSimpleVectorStore(embeddingModel)
    }

}
