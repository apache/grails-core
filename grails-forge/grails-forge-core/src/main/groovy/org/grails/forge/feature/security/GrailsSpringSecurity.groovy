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
import org.springframework.stereotype.Component
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.view.Scaffolding
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView

/**
 * Secures the application with the Grails Spring Security Core plugin over the
 * classic User/Role/UserRole domain model, served by the plugin's own GORM
 * {@code UserDetailsService}. The model matches the Spring Security UI flavor
 * exactly, so adopting the UI plugin later is a dependency-and-config change,
 * not a domain restructuring. The plugin locks every URL down by default
 * ({@code rejectIfNoRule}); generated static rules open the public pages and
 * restrict the scaffolded user admin to {@code ROLE_ADMIN}.
 *
 * @since 8.0
 */
@Component
@CompileStatic
class GrailsSpringSecurity extends SecurityFeature implements PrimarySecurityFeature {

    GrailsSpringSecurity(Scaffolding scaffolding) {
        super(scaffolding)
    }

    @Override
    String getName() {
        return 'grails-spring-security'
    }

    @Override
    String getTitle() {
        return 'Grails Spring Security Plugin'
    }

    @Override
    String getDescription() {
        return 'Secures the application with the Grails Spring Security Core plugin: @Secured annotations, the sec taglib, ' +
                'the plugin\'s login pages backed by the classic GORM User/Role/UserRole model, static URL rules that lock ' +
                'the application down (the scaffolded user admin requires ROLE_ADMIN), and a bootstrapped admin account ' +
                'with a generated password.'
    }

    @Override
    String getDocumentation() {
        return 'https://grails.apache.org/docs/latest/grails-spring-security/'
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId('grails-spring-security')
                .implementation())

        applyClassicDomainModel(generatorContext)
        generatorContext.addTemplate('securityUserService',
                new GspTemplate('grails-app/services/{packagePath}/UserService.groovy',
                        GspView.of('/forge/feature/security/template/userClassicService.gsp', [project: generatorContext.getProject()])))
        generatorContext.addTemplate('securityUserController',
                new GspTemplate('grails-app/controllers/{packagePath}/UserController.groovy',
                        GspView.of('/forge/feature/security/template/userController.gsp', [project: generatorContext.getProject()])))
        boolean ui = generatorContext.getFeatures().contains('grails-spring-security-ui')
        generatorContext.addTemplate('securityApplicationGroovy',
                new GspTemplate('grails-app/conf/application.groovy',
                        GspView.of('/forge/feature/security/template/securityApplicationGroovy.gsp', [project: generatorContext.getProject(), ui: ui])))
    }
}
