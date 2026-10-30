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
package org.grails.forge.feature.lang.groovy

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.gradle.GradlePlugin
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.options.Options
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView

@Component('grailsApplicationFeature')
@CompileStatic
class GrailsApplication implements GrailsApplicationFeature, DefaultFeature {

    @Override
    @Nullable
    String mainClassName(GeneratorContext generatorContext) {
        return generatorContext.getProject().getPackageName() + '.Application'
    }

    @Override
    String getName() {
        return 'grails-application'
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        GrailsApplicationFeature.super.apply(generatorContext)
        final ApplicationType applicationType = generatorContext.getApplicationType()
        if (shouldGenerateApplicationFile(applicationType, generatorContext)) {
            generatorContext.addBuildPlugin(GradlePlugin.builder().id('war').build())
            generatorContext.addTemplate('application', new GspTemplate(getPath(),
                    GspView.of('/forge/feature/lang/groovy/templates/application.gsp', [applicationType: applicationType, project: generatorContext.getProject(), features: generatorContext.getFeatures()])))
            if (applicationType == ApplicationType.REST_API) {
                generatorContext.addTemplate('applicationController', new GspTemplate('grails-app/controllers/{packagePath}/ApplicationController.groovy',
                        GspView.of('/forge/feature/grails/templates/applicationController.gsp', [project: generatorContext.getProject()])))
            }
        }
        if (applicationType == ApplicationType.PLUGIN || applicationType == ApplicationType.WEB_PLUGIN) {
            generatorContext.addTemplate('plugin', new GspTemplate(generatorContext.getSourcePath('/{packagePath}/{className}GrailsPlugin'),
                    GspView.of('/forge/feature/grails/templates/plugin.gsp', [project: generatorContext.getProject(), applicationType: applicationType])))
        }
        generatorContext.addTemplate('bootStrap', new GspTemplate('grails-app/init/{packagePath}/BootStrap.groovy', GspView.of('/forge/feature/lang/groovy/templates/bootStrap.gsp', [project: generatorContext.getProject(), features: generatorContext.getFeatures()])))
    }

    protected boolean shouldGenerateApplicationFile(ApplicationType applicationType, GeneratorContext generatorContext) {
        return applicationType == ApplicationType.WEB ||
                applicationType == ApplicationType.PLUGIN ||
                applicationType == ApplicationType.WEB_PLUGIN ||
                applicationType == ApplicationType.REST_API
    }

    protected String getPath() {
        return 'grails-app/init/{packagePath}/Application.groovy'
    }

    @Override
    boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.REST_API || applicationType == ApplicationType.WEB_PLUGIN || applicationType == ApplicationType.PLUGIN
    }
}
