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
package org.grails.forge.feature.ai

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.generator.GeneratorContext

/**
 * Ollama as the Spring AI model provider, for models that run locally and need no credentials.
 */
@Component
@CompileStatic
class SpringAiOllama extends SpringAiProviderFeature {

    public static final String FEATURE_NAME = 'spring-ai-ollama'

    SpringAiOllama(GrailsSpringAi grailsSpringAi) {
        super(grailsSpringAi)
    }

    @Override
    String getName() {
        return FEATURE_NAME
    }

    @Override
    String getTitle() {
        return 'Spring AI Ollama'
    }

    @Override
    String getDescription() {
        return 'Uses models served by a local Ollama through Spring AI; no credentials needed.'
    }

    @Override
    protected String getStarterArtifactId() {
        return 'spring-ai-starter-model-ollama'
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        super.apply(generatorContext)
        generatorContext.getConfiguration().put('spring.ai.ollama.base-url', 'http://localhost:11434')
    }

    @Override
    String getThirdPartyDocumentation() {
        return 'https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html'
    }
}
