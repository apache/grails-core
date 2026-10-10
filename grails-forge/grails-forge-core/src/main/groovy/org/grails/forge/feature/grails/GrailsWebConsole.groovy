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
package org.grails.forge.feature.grails

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.Category
import org.grails.forge.feature.Feature

@Component
@CompileStatic
class GrailsWebConsole implements Feature {

    @Override
    String getName() {
        return 'grails-web-console'
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Override
    String getTitle() {
        return 'Web Console'
    }

    @Override
    String getDescription() {
        return 'A web-based Groovy console for interactive runtime application management and debugging.'
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.grails.plugins')
                .lookupArtifactId('grails-web-console')
                .runtimeOnly())

        final Map<String, Object> config = generatorContext.getConfiguration()
        config.put('environments.production.grails.plugin.console.enabled', false)
        config.put('environments.production.grails.plugin.console.fileStore.remote.enabled', false)
    }

    @Override
    String getCategory() {
        return Category.MANAGEMENT
    }

    @Override
    String getDocumentation() {
        return 'https://github.com/grails-plugins/grails-web-console/#readme'
    }
}
