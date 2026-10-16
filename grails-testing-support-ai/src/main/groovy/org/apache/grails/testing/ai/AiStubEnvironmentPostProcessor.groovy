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
import groovy.util.logging.Slf4j

import org.springframework.ai.model.SpringAIModelProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * Turns the provider auto-configurations off while the stubs are enabled, so a test environment needs
 * neither credentials nor a database: every {@code spring.ai.model.*} selection property, the chat,
 * embedding, image, audio and moderation models', and {@code spring.ai.vectorstore.type} default to
 * {@code none}. The defaults sit below every other property source, so an application that names a
 * provider in its configuration keeps it, and the stub for that kind of model then backs off.
 *
 * <p>The stubs are meant for the test environment and are enabled there by default. Outside it, the
 * module is on the classpath by mistake more often than on purpose, so the stubs stay off unless
 * {@code grails.ai.stub.enabled} is set to {@code true} explicitly, and a warning says the module was
 * found; a production application never has its model replaced quietly by one that echoes the prompt.</p>
 *
 * @since 8.1
 */
@CompileStatic
@Slf4j
class AiStubEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = 'grailsAiStubDefaults'
    public static final String STUB_ENABLED = AiStubProperties.PREFIX + '.enabled'
    public static final String GRAILS_ENVIRONMENT = 'grails.env'
    public static final String TEST_ENVIRONMENT = 'test'
    public static final String CHAT_MODEL = SpringAIModelProperties.CHAT_MODEL
    public static final String EMBEDDING_MODEL = SpringAIModelProperties.EMBEDDING_MODEL
    public static final String TEXT_EMBEDDING_MODEL = SpringAIModelProperties.TEXT_EMBEDDING_MODEL
    public static final String MULTIMODAL_EMBEDDING_MODEL = SpringAIModelProperties.MULTI_MODAL_EMBEDDING_MODEL
    public static final String IMAGE_MODEL = SpringAIModelProperties.IMAGE_MODEL
    public static final String AUDIO_TRANSCRIPTION_MODEL = SpringAIModelProperties.AUDIO_TRANSCRIPTION_MODEL
    public static final String AUDIO_SPEECH_MODEL = SpringAIModelProperties.AUDIO_SPEECH_MODEL
    public static final String MODERATION_MODEL = SpringAIModelProperties.MODERATION_MODEL
    public static final String VECTOR_STORE = 'spring.ai.vectorstore.type'
    public static final String NONE = 'none'

    /**
     * Every selection property Spring AI reads: one per kind of model, and the vector store's. Each is
     * defaulted to {@code none}, so no provider auto-configures a model that would need credentials.
     */
    public static final List<String> SELECTION_PROPERTIES = List.of(
            CHAT_MODEL, EMBEDDING_MODEL, TEXT_EMBEDDING_MODEL, MULTIMODAL_EMBEDDING_MODEL, IMAGE_MODEL,
            AUDIO_TRANSCRIPTION_MODEL, AUDIO_SPEECH_MODEL, MODERATION_MODEL, VECTOR_STORE)

    @Override
    void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.propertySources.contains(PROPERTY_SOURCE_NAME)) {
            return
        }
        Boolean enabled = environment.getProperty(STUB_ENABLED, Boolean)
        if (enabled == null) {
            if (!isTestEnvironment(environment)) {
                log.warn('grails-testing-support-ai is on the classpath outside the test environment; its stubs stay off ' +
                        'and the configured provider is used. Set {} to true to use the stubs here.', STUB_ENABLED)
                environment.propertySources.addLast(new MapPropertySource(PROPERTY_SOURCE_NAME,
                        [(STUB_ENABLED): false] as Map<String, Object>))
                return
            }
        }
        else if (!enabled) {
            return
        }
        Map<String, Object> defaults = [:]
        for (String property : SELECTION_PROPERTIES) {
            defaults[property] = NONE
        }
        environment.propertySources.addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults))
    }

    /**
     * @return whether the application runs in the Grails test environment: {@code grails.env} is
     * {@code test}, which the Grails Gradle plugin sets for every test task, or {@code test} is an active
     * Spring profile, which Grails adds for its environment
     */
    static boolean isTestEnvironment(ConfigurableEnvironment environment) {
        String grailsEnvironment = environment.getProperty(GRAILS_ENVIRONMENT)
        if (grailsEnvironment) {
            return grailsEnvironment == TEST_ENVIRONMENT
        }
        TEST_ENVIRONMENT in environment.activeProfiles
    }

    @Override
    int getOrder() {
        Ordered.LOWEST_PRECEDENCE
    }

}
