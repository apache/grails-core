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
package org.grails.forge.feature.reloading;

import java.util.Set;

import jakarta.inject.Singleton;

import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.build.dependencies.Scope;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.config.ApplicationConfiguration;
import org.grails.forge.feature.config.ConfigurationFeature;
import org.grails.forge.feature.micronaut.GrailsMicronaut;
import org.grails.forge.options.DevelopmentReloading;
import org.grails.forge.options.Options;

@Singleton
public class SpringBootDevTools implements ReloadingFeature {

    @Override
    public String getName() {
        return "spring-boot-devtools";
    }

    @Override
    public String getTitle() {
        return "SpringBoot Developer Tools";
    }

    @Override
    public String getDescription() {
        return "Spring Boot Devtools is a powerful tool that enhances development productivity " +
                "by providing features like automatic application restarts on code changes, live " +
                "reloading of static resources, and remote debugging support. It enables developers " +
                "to rapidly iterate and test changes during the development process, making it a " +
                "valuable asset for Spring Boot projects.";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder().groupId("org.springframework.boot")
                .artifactId("spring-boot-devtools")
                .scope(Scope.DEVELOPMENT_ONLY)
                .build());

        // Spring Boot 4 changed the default of spring.devtools.livereload.enabled from
        // true to false. Re-enable it in the development environment only so the dev
        // workflow keeps Grails 7 livereload behavior, while non-dev environments honor
        // the new Spring Boot 4 default. Users who do not want livereload can set the
        // property to false in application-development.yml after generation.
        ApplicationConfiguration devConfig = generatorContext.getConfiguration(
                ConfigurationFeature.DEV_ENVIRONMENT_KEY,
                new ApplicationConfiguration(ConfigurationFeature.DEV_ENVIRONMENT_KEY)
        );
        devConfig.put("spring.devtools.livereload.enabled", true);
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        if (selectedFeatures.stream().anyMatch(f -> f instanceof GrailsMicronaut)) {
            return false;
        }
        return options.getDevelopmentReloading() == DevelopmentReloading.DEVTOOLS;
    }

    @Override
    public String getDocumentation() {
        return "https://docs.spring.io/spring-boot/reference/using/devtools.html";
    }

    @Override
    public DevelopmentReloading getReloading() {
        return DevelopmentReloading.DEVTOOLS;
    }
}
