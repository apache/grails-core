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
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.Category
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.OneOfFeature

/**
 * A Spring AI model provider: adds the provider's starter, whose version the Grails BOM manages, and
 * the Grails Spring AI plugin the application uses it through. An application selects one provider at
 * most.
 */
@CompileStatic
abstract class SpringAiProviderFeature implements OneOfFeature {

    private final GrailsSpringAi grailsSpringAi

    protected SpringAiProviderFeature(GrailsSpringAi grailsSpringAi) {
        this.grailsSpringAi = grailsSpringAi
    }

    /**
     * @return the {@code spring-ai-starter-model-*} artifact of the provider
     */
    protected abstract String getStarterArtifactId()

    /**
     * One provider per application: a second starter would auto-configure a second {@code ChatModel},
     * and nothing selects between them for the {@code ChatClient.Builder}.
     */
    @Override
    Class<?> getFeatureClass() {
        return SpringAiProviderFeature
    }

    @Override
    void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(GrailsSpringAi)) {
            featureContext.addFeature(grailsSpringAi)
        }
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.springframework.ai')
                .artifactId(getStarterArtifactId())
                .implementation())
    }

    /**
     * A provider is an application's choice: a plugin that carried a starter would impose it on every
     * application that uses the plugin.
     */
    @Override
    boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.REST_API
    }

    @Override
    String getCategory() {
        return Category.AI
    }

    @Override
    String getDocumentation() {
        return grailsSpringAi.getDocumentation()
    }
}
