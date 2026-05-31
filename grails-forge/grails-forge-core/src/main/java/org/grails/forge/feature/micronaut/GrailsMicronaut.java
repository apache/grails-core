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
package org.grails.forge.feature.micronaut;

import java.util.Optional;

import jakarta.inject.Singleton;

import io.micronaut.core.annotation.NonNull;

import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Coordinate;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.build.dependencies.PomDependencyVersionResolver;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.options.JdkVersion;

@Singleton
public class GrailsMicronaut implements Feature {

    private final PomDependencyVersionResolver versionResolver;

    public GrailsMicronaut(PomDependencyVersionResolver versionResolver) {
        this.versionResolver = versionResolver;
    }

    @Override
    @NonNull
    public String getName() {
        return "grails-micronaut";
    }

    @Override
    public String getTitle() {
        return "Micronaut Support";
    }

    @Override
    public String getDescription() {
        return "Adds support for Micronaut to Grails using the Spring Starter";
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        // micronaut-core's ScopedValues references java.lang.ScopedValue.CallableOp,
        // which only exists in JDK 25+ (JEP 506). Refuse to apply on older JDKs.
        JdkVersion jdk = featureContext.getJavaVersion();
        if (jdk.majorVersion() < JdkVersion.JDK_25.majorVersion()) {
            throw new IllegalArgumentException(
                getName() + " requires JDK 25 or later (selected: JDK " + jdk.majorVersion() + ")."
            );
        }
    }

    @Override
    public String getCategory() {
        return Category.SPRING;
    }

    @Override
    public String getDocumentation() {
        return "https://micronaut-projects.github.io/micronaut-spring/latest/guide/#springBootStarter";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-micronaut")
                .implementation());

        Optional<Coordinate> micronautPlatformVersion =
            versionResolver.resolve("micronaut-platform");
        micronautPlatformVersion.ifPresent(coordinate ->
            generatorContext.getBuildProperties().put(
                "micronautPlatformVersion",
                coordinate.getVersion()
            )
        );
    }
}
