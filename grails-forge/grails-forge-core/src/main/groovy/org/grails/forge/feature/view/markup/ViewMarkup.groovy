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
package org.grails.forge.feature.view.markup

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.springframework.stereotype.Component
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.build.gradle.GradlePlugin
import org.grails.forge.feature.Feature
import org.grails.forge.feature.view.GrailsViews
import org.grails.forge.feature.web.GrailsWeb
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView

@Component
@CompileStatic
class ViewMarkup extends GrailsViews implements Feature {

    ViewMarkup(GrailsWeb grailsWeb) {
        super(grailsWeb)
    }

    @Override
    @Nonnull
    String getName() {
        return 'views-markup'
    }

    @Override
    String getTitle() {
        return 'Markup Views'
    }

    @Override
    @Nonnull
    String getDescription() {
        return 'Markup views are written in Groovy, end with the file extension gml and reside in the grails-app/views directory. They provide a DSL for producing output in the XML.'
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addBuildPlugin(GradlePlugin.builder()
                .id('org.apache.grails.gradle.grails-markup')
                .useApplyPlugin(true)
                .build())

        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId('grails-views-markup')
                .implementation())

        generatorContext.addTemplate('application_index_gml', new GspTemplate(getViewFolderPath() + 'application/index.gml', GspView.of('/forge/feature/view/markup/templates/index.gsp', [:])))
        generatorContext.addTemplate('_errors_gml', new GspTemplate(getViewFolderPath() + 'errors/_errors.gml', GspView.of('/forge/feature/view/markup/templates/_errors.gsp', [:])))
        generatorContext.addTemplate('_object_gml', new GspTemplate(getViewFolderPath() + 'object/_object.gml', GspView.of('/forge/feature/view/markup/templates/_object.gsp', [:])))
        generatorContext.addTemplate('error_gml', new GspTemplate(getViewFolderPath() + 'error.gml', GspView.of('/forge/feature/view/markup/templates/error.gsp', [:])))
        generatorContext.addTemplate('notFound_gml', new GspTemplate(getViewFolderPath() + 'notFound.gml', GspView.of('/forge/feature/view/markup/templates/notFound.gsp', [:])))
    }

}
