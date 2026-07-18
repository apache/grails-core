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
package org.grails.forge.feature.security;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.Project;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.security.template.securityRole;
import org.grails.forge.feature.security.template.springSecurityResources;
import org.grails.forge.feature.security.template.securityUser;
import org.grails.forge.feature.security.template.securityUserPasswordEncoderListener;
import org.grails.forge.feature.security.template.securityUserRole;
import org.grails.forge.template.RockerTemplate;
import org.grails.forge.util.VersionInfo;

import java.util.List;
import java.util.Map;

@Singleton
public class SpringSecurityCore implements Feature {

    @Override
    public String getName() {
        return "spring-security";
    }

    @Override
    public String getTitle() {
        return "Spring Security Core";
    }

    @Override
    public String getDescription() {
        return "Secures the application with the Spring Security Core plugin, generating the SecurityUser, SecurityRole and "
                + "SecurityUserRole domain classes and the standard permit-all rules for the public pages "
                + "(the same starting point the s2-quickstart command produces).";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Project project = generatorContext.getProject();
        String packageName = project.getPackageName();

        Map<String, Object> config = generatorContext.getConfiguration();
        config.put("grails.plugin.springsecurity.userLookup.userDomainClassName", packageName + ".SecurityUser");
        config.put("grails.plugin.springsecurity.userLookup.authorityJoinClassName", packageName + ".SecurityUserRole");
        config.put("grails.plugin.springsecurity.authority.className", packageName + ".SecurityRole");
        config.put("grails.plugin.springsecurity.controllerAnnotations.staticRules", staticRules());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-spring-security")
                .implementation());

        generatorContext.addTemplate("springSecurityUserDomain",
                new RockerTemplate("grails-app/domain/{packagePath}/SecurityUser.groovy", securityUser.template(project)));
        generatorContext.addTemplate("springSecurityRoleDomain",
                new RockerTemplate("grails-app/domain/{packagePath}/SecurityRole.groovy", securityRole.template(project)));
        generatorContext.addTemplate("springSecurityUserRoleDomain",
                new RockerTemplate("grails-app/domain/{packagePath}/SecurityUserRole.groovy", securityUserRole.template(project)));

        // GORM does not autowire services into domain instances by default, so password encoding is
        // performed by a persistence-event listener bean — the same approach s2-quickstart generates.
        generatorContext.addTemplate("springSecurityPasswordEncoderListener",
                new RockerTemplate("src/main/groovy/{packagePath}/SecurityUserPasswordEncoderListener.groovy",
                        securityUserPasswordEncoderListener.template(project)));
        // SpringResources backs off when this feature is selected, so this is the only
        // template writing grails-app/conf/spring/resources.groovy
        generatorContext.addTemplate("springResources",
                new RockerTemplate("grails-app/conf/spring/resources.groovy", springSecurityResources.template(project)));
    }

    private static List<Map<String, Object>> staticRules() {
        return List.of(
                rule("/", "permitAll"),
                rule("/error", "permitAll"),
                rule("/index", "permitAll"),
                rule("/index.gsp", "permitAll"),
                rule("/shutdown", "permitAll"),
                rule("/assets/**", "permitAll"),
                rule("/**/js/**", "permitAll"),
                rule("/**/css/**", "permitAll"),
                rule("/**/images/**", "permitAll"),
                rule("/**/favicon.ico", "permitAll"));
    }

    private static Map<String, Object> rule(String pattern, String access) {
        return Map.of("pattern", pattern, "access", List.of(access));
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.REST_API;
    }

    @Override
    public String getCategory() {
        return Category.SECURITY;
    }

    @Override
    public String getDocumentation() {
        return "https://grails.apache.org/docs/" + VersionInfo.getDocumentationVersion() + "/guide/security.html";
    }

}
