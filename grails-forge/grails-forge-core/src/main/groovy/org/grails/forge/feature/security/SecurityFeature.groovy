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
package org.grails.forge.feature.security

import groovy.transform.CompileStatic
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Category
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeaturePhase
import org.grails.forge.feature.view.Scaffolding
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView

/**
 * Common ground of the security features: category, WEB-only support, the ordering
 * that lets them override default templates while still preceding the build-file
 * rendering, and the classic User/Role/UserRole domain model shared by the plugin
 * flavors so the UI plugin composes with (and can be adopted after) the core plugin.
 *
 * @since 8.0
 */
@CompileStatic
abstract class SecurityFeature implements Feature {

    private final Scaffolding scaffolding

    protected SecurityFeature(Scaffolding scaffolding) {
        this.scaffolding = scaffolding
    }

    @Override
    String getCategory() {
        return Category.SPRING_SECURITY
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB
    }

    @Override
    int getOrder() {
        // After the default features, so overriding templates registered under
        // their keys (e.g. spring/resources.groovy) takes effect - but before
        // the BUILD phase renders build.gradle from the collected dependencies.
        return FeaturePhase.TEST.getOrder()
    }

    @Override
    void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(Scaffolding) && scaffolding != null) {
            featureContext.addFeature(scaffolding)
        }
    }

    protected void applyClassicDomainModel(GeneratorContext generatorContext) {
        final Project project = generatorContext.getProject()
        generatorContext.addTemplate('securityUser',
                new GspTemplate('grails-app/domain/{packagePath}/User.groovy',
                        GspView.of('/forge/feature/security/template/userClassic.gsp', [project: project, features: generatorContext.getFeatures()])))
        generatorContext.addTemplate('securityRole',
                new GspTemplate('grails-app/domain/{packagePath}/Role.groovy', GspView.of('/forge/feature/security/template/role.gsp', [project: project])))
        generatorContext.addTemplate('securityUserRole',
                new GspTemplate('grails-app/domain/{packagePath}/UserRole.groovy', GspView.of('/forge/feature/security/template/userRole.gsp', [project: project])))
        generatorContext.addTemplate('securityUserSpec',
                new GspTemplate(generatorContext.getTestSourcePath('/{packagePath}/User'), GspView.of('/forge/feature/security/template/userClassicSpec.gsp', [project: project])))
    }
}
