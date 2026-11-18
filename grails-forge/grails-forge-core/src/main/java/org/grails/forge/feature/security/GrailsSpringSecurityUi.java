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

import java.util.List;

import jakarta.inject.Singleton;

import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.feature.view.Scaffolding;

/**
 * Supplements the Grails Spring Security Core plugin - added automatically when
 * this feature is selected - with the Spring Security UI plugin's administration
 * screens. All domain artifacts, the scaffolded user admin and the security
 * config come from the core feature, which widens its static rules and enables
 * service-level password encoding when this feature is present.
 *
 * @since 8.0
 */
@Singleton
public class GrailsSpringSecurityUi extends SecurityFeature {

    private final GrailsSpringSecurity grailsSpringSecurity;

    public GrailsSpringSecurityUi(GrailsSpringSecurity grailsSpringSecurity, Scaffolding scaffolding) {
        super(scaffolding);
        this.grailsSpringSecurity = grailsSpringSecurity;
    }

    @Override
    public String getName() {
        return "grails-spring-security-ui";
    }

    @Override
    public String getTitle() {
        return "Grails Spring Security UI Plugin";
    }

    @Override
    public String getDescription() {
        return "Supplements the Grails Spring Security Core plugin - added automatically when selected - with the Spring " +
                "Security UI plugin's administration screens, rendered through the application's own layout: user and role " +
                "CRUD, registration and forgot-password flows (sending mail requires SMTP configuration), all on the same " +
                "classic User/Role/UserRole domain model.";
    }

    @Override
    public String getDocumentation() {
        return "https://grails.apache.org/docs/latest/grails-spring-security-ui/";
    }

    @Override
    public List<String> getDependentFeatures() {
        return List.of("grails-spring-security");
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        super.processSelectedFeatures(featureContext);
        if (!featureContext.isPresent(GrailsSpringSecurity.class) && grailsSpringSecurity != null) {
            featureContext.addFeature(grailsSpringSecurity);
        }
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-spring-security-ui")
                .implementation());
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-mail")
                .implementation());

        generatorContext.getConfiguration().put("grails.mail.default.from", "do.not.reply@localhost");
    }
}
