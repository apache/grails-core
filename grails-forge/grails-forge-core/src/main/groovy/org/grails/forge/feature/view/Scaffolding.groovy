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
package org.grails.forge.feature.view

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.options.Options
import org.grails.forge.util.VersionInfo

@Component
@CompileStatic
class Scaffolding implements DefaultFeature {

    private final GrailsGsp grailsGsp

    Scaffolding(GrailsGsp grailsGsp) {
        this.grailsGsp = grailsGsp
    }

    @Override
    boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.WEB_PLUGIN
    }

    @Override
    String getName() {
        return 'scaffolding'
    }

    @Override
    String getTitle() {
        return 'Scaffolding'
    }

    @Override
    String getDescription() {
        return 'The Scaffolding Plugin simplifies generating CRUD interfaces for domain classes. ' +
               'It is useful for rapid prototyping and creates basic data management user interfaces ' +
                'with minimal manual code.'
    }

    @Override
    String getDocumentation() {
        return 'https://grails.apache.org/docs/' + VersionInfo.getDocumentationVersion() + '/guide/scaffolding.html'
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.WEB_PLUGIN
    }

    @Override
    void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(GrailsGsp) && grailsGsp != null) {
            featureContext.addFeature(grailsGsp)
        }
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId('grails-scaffolding')
                .implementation())
    }
}
