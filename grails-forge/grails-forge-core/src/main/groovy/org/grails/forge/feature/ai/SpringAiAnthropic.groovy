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

/**
 * Anthropic as the Spring AI model provider. The API key is read from the {@code ANTHROPIC_API_KEY}
 * environment variable, so it never lands in the generated configuration.
 */
@Component
@CompileStatic
class SpringAiAnthropic extends SpringAiProviderFeature {

    public static final String FEATURE_NAME = 'spring-ai-anthropic'

    SpringAiAnthropic(GrailsSpringAi grailsSpringAi) {
        super(grailsSpringAi)
    }

    @Override
    String getName() {
        return FEATURE_NAME
    }

    @Override
    String getTitle() {
        return 'Spring AI Anthropic'
    }

    @Override
    String getDescription() {
        return 'Uses Anthropic Claude models through Spring AI, which reads the API key from the ANTHROPIC_API_KEY environment variable.'
    }

    @Override
    protected String getStarterArtifactId() {
        return 'spring-ai-starter-model-anthropic'
    }

    @Override
    String getThirdPartyDocumentation() {
        return 'https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html'
    }
}
