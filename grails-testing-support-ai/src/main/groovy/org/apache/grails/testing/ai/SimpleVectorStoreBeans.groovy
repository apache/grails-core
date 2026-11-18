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
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.ListableBeanFactory

/**
 * Registers and resets the in-memory vector store a unit test gets. Kept apart from {@link AiTestBeans}
 * so that class loads without Spring AI's vector store module.
 *
 * @since 8.1
 */
@CompileStatic
class SimpleVectorStoreBeans {

    static void register(BeanRegistry registry) {
        registry.registerBean(AiTestBeans.VECTOR_STORE, ResettableSimpleVectorStore) {
            it.supplier { context -> new ResettableSimpleVectorStore(context.bean(EmbeddingModel)) }
        }
    }

    /**
     * @return whether the context holds no vector store, of any kind
     */
    static boolean isMissing(ListableBeanFactory beanFactory) {
        beanFactory.getBeanNamesForType(VectorStore).length == 0
    }

    /**
     * Empties every resettable vector store the context holds.
     */
    static void reset(ListableBeanFactory beanFactory) {
        for (ResettableSimpleVectorStore store : beanFactory.getBeansOfType(ResettableSimpleVectorStore).values()) {
            store.clear()
        }
    }

}
