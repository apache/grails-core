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

import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.support.GenericApplicationContext

import spock.lang.Specification

class AiTestingAutoConfigurationSpec extends Specification {

    private static final String[] NO_PROVIDER = ['spring.ai.model.chat=none', 'spring.ai.model.embedding=none', 'spring.ai.vectorstore.type=none']

    private ApplicationContextRunner contextRunner() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AiTestingAutoConfiguration, AiTestingVectorStoreAutoConfiguration))
    }

    void 'the stubs stand in for the models when no provider is selected'() {
        expect:
        contextRunner().withPropertyValues(NO_PROVIDER).run { context ->
            assert context.getBean(ChatModel) instanceof ScriptedChatModel
            assert context.getBean(EmbeddingModel) instanceof StubEmbeddingModel
            assert context.getBean(EmbeddingModel).dimensions() == StubEmbeddingModel.DEFAULT_DIMENSIONS
            assert context.getBean(VectorStore) instanceof ResettableSimpleVectorStore
        }
    }

    void 'the stub vector store backs off when the application selects a vector store'() {
        expect: 'the store the application selected, evaluated later, is not pre-empted by the stub'
        contextRunner().withPropertyValues('spring.ai.model.chat=none', 'spring.ai.model.embedding=none', 'spring.ai.vectorstore.type=pgvector')
                .run { context ->
                    assert context.getBean(ChatModel) instanceof ScriptedChatModel
                    assert context.getBean(EmbeddingModel) instanceof StubEmbeddingModel
                    assert !context.getBeanNamesForType(VectorStore)
                }
    }

    void 'the stub vector store backs off without the vector store selection property'() {
        expect:
        contextRunner().withPropertyValues('spring.ai.model.chat=none', 'spring.ai.model.embedding=none')
                .run { context ->
                    assert !context.getBeanNamesForType(VectorStore)
                }
    }

    void 'the stub vector store can be emptied between tests that share a context'() {
        expect:
        contextRunner().withPropertyValues(NO_PROVIDER).run { context ->
            ResettableSimpleVectorStore store = context.getBean(ResettableSimpleVectorStore)
            store.add([new Document('Grails is a web framework')])
            assert store.similaritySearch(SearchRequest.builder().query('web framework').topK(1).build()).size() == 1
            store.clear()
            assert store.similaritySearch(SearchRequest.builder().query('web framework').topK(1).build()).empty
        }
    }

    void 'the stub embedding model takes its dimensions from the configuration'() {
        expect:
        contextRunner().withPropertyValues(NO_PROVIDER).withPropertyValues('grails.ai.stub.embedding-dimensions=16')
                .run { context ->
                    assert context.getBean(EmbeddingModel).dimensions() == 16
                }
    }

    void 'the stubs back off when a provider is selected'() {
        expect:
        contextRunner().withPropertyValues('spring.ai.model.chat=openai', 'spring.ai.model.embedding=openai')
                .run { context ->
                    assert !context.getBeanNamesForType(ChatModel)
                    assert !context.getBeanNamesForType(EmbeddingModel)
                    assert !context.getBeanNamesForType(VectorStore)
                }
    }

    void 'the stubs back off when disabled'() {
        expect:
        contextRunner().withPropertyValues(NO_PROVIDER).withPropertyValues('grails.ai.stub.enabled=false')
                .run { context ->
                    assert !context.getBeanNamesForType(ChatModel)
                    assert !context.getBeanNamesForType(EmbeddingModel)
                    assert !context.getBeanNamesForType(VectorStore)
                }
    }

    void 'the models and the vector store the application defines win'() {
        given:
        ChatModel ownChatModel = new ScriptedChatModel()
        EmbeddingModel ownEmbeddingModel = new StubEmbeddingModel(3)
        VectorStore ownVectorStore = SimpleVectorStore.builder(ownEmbeddingModel).build()

        expect:
        contextRunner().withPropertyValues(NO_PROVIDER)
                .withBean('ownChatModel', ChatModel, { -> ownChatModel })
                .withBean('ownEmbeddingModel', EmbeddingModel, { -> ownEmbeddingModel })
                .withBean('ownVectorStore', VectorStore, { -> ownVectorStore })
                .run { context ->
                    assert context.getBean(ChatModel).is(ownChatModel)
                    assert context.getBean(EmbeddingModel).is(ownEmbeddingModel)
                    assert context.getBean(VectorStore).is(ownVectorStore)
                    assert !context.containsBean('chatModel')
                    assert !context.containsBean('embeddingModel')
                    assert !context.containsBean('vectorStore')
                }
    }

    void 'a model or store the application registers after the conditions were evaluated, as resources.groovy beans are, wins over the stub'() {
        given: 'beans registered by a post-processor that runs after the auto-configuration conditions, as the Grails one does'
        ChatModel ownChatModel = new ScriptedChatModel()
        EmbeddingModel ownEmbeddingModel = new StubEmbeddingModel(3)
        VectorStore ownVectorStore = SimpleVectorStore.builder(ownEmbeddingModel).build()

        expect:
        contextRunner().withPropertyValues(NO_PROVIDER)
                .withInitializer { ConfigurableApplicationContext context ->
                    ((GenericApplicationContext) context).registerBean('lateApplicationBeans', LateApplicationBeans,
                            { -> new LateApplicationBeans(ownChatModel: ownChatModel, ownEmbeddingModel: ownEmbeddingModel, ownVectorStore: ownVectorStore) })
                }
                .run { context ->
                    assert context.containsBean('chatModel') && context.containsBean('embeddingModel') && context.containsBean('vectorStore')
                    assert context.beanFactory.getBeanDefinition('chatModel').fallback
                    assert context.beanFactory.getBeanDefinition('embeddingModel').fallback
                    assert context.beanFactory.getBeanDefinition('vectorStore').fallback
                    assert context.getBean(ChatModel).is(ownChatModel)
                    assert context.getBean(EmbeddingModel).is(ownEmbeddingModel)
                    assert context.getBean(VectorStore).is(ownVectorStore)
                }
    }

    void 'the stub vector store is built over an embedding model a provider auto-configuration supplies later in the order'() {
        expect: 'the stub store sorts after a provider auto-configuration that declares no order, so the model exists for its condition'
        contextRunner().withConfiguration(AutoConfigurations.of(ProviderEmbeddingAutoConfiguration))
                .withPropertyValues('spring.ai.model.chat=none', 'spring.ai.model.embedding=provider', 'spring.ai.vectorstore.type=none')
                .run { context ->
                    assert !context.containsBean('embeddingModel')
                    assert context.getBean(EmbeddingModel).is(ProviderEmbeddingAutoConfiguration.MODEL)
                    assert context.getBean(VectorStore) instanceof ResettableSimpleVectorStore
                    assert context.getBean(ResettableSimpleVectorStore).embeddingModel.is(ProviderEmbeddingAutoConfiguration.MODEL)
                }
    }

    void 'no vector store is registered without the vector store module'() {
        expect:
        contextRunner().withPropertyValues(NO_PROVIDER).withClassLoader(new FilteredClassLoader(SimpleVectorStore))
                .run { context ->
                    assert context.getBean(ChatModel) instanceof ScriptedChatModel
                    assert !context.getBeanNamesForType(VectorStore)
                }
    }

    /**
     * Registers beans the way GrailsApplicationPostProcessor registers an application's own: from a
     * post-processor that is neither PriorityOrdered nor Ordered, after the configuration classes.
     */
    static class LateApplicationBeans implements BeanDefinitionRegistryPostProcessor {

        ChatModel ownChatModel
        EmbeddingModel ownEmbeddingModel
        VectorStore ownVectorStore

        @Override
        void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            registry.registerBeanDefinition('ownChatModel', new RootBeanDefinition(ChatModel, { -> ownChatModel }))
            registry.registerBeanDefinition('ownEmbeddingModel', new RootBeanDefinition(EmbeddingModel, { -> ownEmbeddingModel }))
            registry.registerBeanDefinition('ownVectorStore', new RootBeanDefinition(VectorStore, { -> ownVectorStore }))
        }

        @Override
        void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        }

    }

    /**
     * A provider's embedding auto-configuration: no order of its own, and a name that sorts after the stubs'.
     */
    @AutoConfiguration
    @ConditionalOnProperty(name = 'spring.ai.model.embedding', havingValue = 'provider')
    static class ProviderEmbeddingAutoConfiguration {

        static final EmbeddingModel MODEL = new StubEmbeddingModel(8)

        @Bean
        EmbeddingModel providerEmbeddingModel() {
            MODEL
        }

    }

}
