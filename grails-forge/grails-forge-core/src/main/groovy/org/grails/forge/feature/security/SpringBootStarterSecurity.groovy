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
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.view.Scaffolding
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView

/**
 * Secures the application with plain Spring Security: the spring-boot-starter-security
 * dependency, the shared user artifacts, and form login wired through the {@code beans}
 * DSL on the application's own {@code Application} class. No Grails security plugins are
 * involved, and no separate configuration class is generated.
 *
 * @since 8.0
 */
@Component
@CompileStatic
class SpringBootStarterSecurity extends SecurityFeature implements PrimarySecurityFeature {

    SpringBootStarterSecurity(Scaffolding scaffolding) {
        super(scaffolding)
    }

    @Override
    String getName() {
        return 'spring-boot-starter-security'
    }

    @Override
    String getTitle() {
        return 'Spring Boot Starter Security'
    }

    @Override
    String getDescription() {
        return 'Secures the application with a lightweight, plain Spring Security setup that does NOT use any Grails plugins: ' +
                'spring-boot-starter-security, a GORM-backed User domain implementing UserDetails, form login and logout, ' +
                'a scaffolded user admin, and a bootstrapped admin account with a generated password.'
    }

    @Override
    String getDocumentation() {
        return 'https://docs.spring.io/spring-boot/reference/web/spring-security.html'
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.springframework.boot')
                .artifactId('spring-boot-starter-security')
                .implementation())

        final Project project = generatorContext.getProject()
        generatorContext.addTemplate('securityUser',
                new GspTemplate('grails-app/domain/{packagePath}/User.groovy',
                        GspView.of('/forge/feature/security/template/user.gsp', [project: project, features: generatorContext.getFeatures()])))
        generatorContext.addTemplate('securityUserController',
                new GspTemplate('grails-app/controllers/{packagePath}/UserController.groovy', GspView.of('/forge/feature/security/template/userController.gsp', [project: project])))
        generatorContext.addTemplate('securityUserService',
                new GspTemplate('grails-app/services/{packagePath}/UserService.groovy', GspView.of('/forge/feature/security/template/userService.gsp', [project: project])))
        generatorContext.addTemplate('securityUserSpec',
                new GspTemplate(generatorContext.getTestSourcePath('/{packagePath}/User'), GspView.of('/forge/feature/security/template/userSpec.gsp', [project: project])))
    }
}
