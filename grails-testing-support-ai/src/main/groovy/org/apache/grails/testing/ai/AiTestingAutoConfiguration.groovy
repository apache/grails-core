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

import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Fallback

/**
 * Registers a {@link ScriptedChatModel} and a {@link StubEmbeddingModel} in place of the provider's
 * models while {@code grails.ai.stub.enabled} is {@code true}, which it is by default when this module
 * is on the classpath.
 *
 * <p>A stub registers only when the matching model selection is {@code none}, the default
 * {@link AiStubEnvironmentPostProcessor} supplies, so an application that selects a provider in its
 * configuration keeps it; and only when the application defines no model of that kind itself. Beans an
 * application defines in {@code resources.groovy}, {@code doWithSpring} or its {@code beanRegistrar} are
 * registered after these conditions are evaluated, so each stub is a fallback bean as well: such a
 * bean is the one injected, and the stub is left alone.</p>
 *
 * @since 8.1
 */
@CompileStatic
@AutoConfiguration(beforeName = 'org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration')
@ConditionalOnClass(ChatModel)
@ConditionalOnProperty(prefix = AiStubProperties.PREFIX, name = 'enabled', havingValue = 'true', matchIfMissing = true)
@EnableConfigurationProperties(AiStubProperties)
class AiTestingAutoConfiguration {

    @Bean
    @Fallback
    @ConditionalOnMissingBean(ChatModel)
    @ConditionalOnProperty(name = AiStubEnvironmentPostProcessor.CHAT_MODEL, havingValue = AiStubEnvironmentPostProcessor.NONE)
    ScriptedChatModel chatModel() {
        new ScriptedChatModel()
    }

    @Bean
    @Fallback
    @ConditionalOnMissingBean(EmbeddingModel)
    @ConditionalOnProperty(name = AiStubEnvironmentPostProcessor.EMBEDDING_MODEL, havingValue = AiStubEnvironmentPostProcessor.NONE)
    StubEmbeddingModel embeddingModel(AiStubProperties properties) {
        new StubEmbeddingModel(properties.embeddingDimensions)
    }

}
