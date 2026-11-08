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
package org.grails.forge.feature.logging;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.Options;

import java.util.Set;

@Singleton
public class Logback implements LoggingFeature, DefaultFeature {

    @Override
    public String getName() {
        return "logback";
    }

    @Override
    public String getTitle() {
        return "Logback Logging";
    }

    @Override
    public String getDescription() {
        return "Add Logback Logging library.";
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return selectedFeatures.stream().noneMatch(f -> f instanceof LoggingFeature);
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        // Only add the logging library. Do not generate grails-app/conf/logback-spring.xml:
        // grails-logging brings spring-boot-starter-logging, so Logback and Spring Boot's
        // default configuration apply automatically when the app supplies no config. This
        // matches Spring Boot 4.x's zero-config logging convention. Users add their own
        // logback-spring.xml only when they need to customize.
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-logging")
                .implementation());
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }
}
