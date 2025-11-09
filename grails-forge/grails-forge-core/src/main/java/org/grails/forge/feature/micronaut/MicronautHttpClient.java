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

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Coordinate;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.build.dependencies.PomDependencyVersionResolver;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;

import java.util.Optional;

@Singleton
public class MicronautHttpClient implements Feature {

    private final PomDependencyVersionResolver versionResolver;

    public MicronautHttpClient(PomDependencyVersionResolver versionResolver) {
        this.versionResolver = versionResolver;
    }

    @Override
    @NonNull
    public String getName() {
        return "micronaut-http-client";
    }

    @Override
    public String getTitle() {
        return "Micronaut HTTP Client";
    }

    @Override
    public String getDescription() {
        return "Add support for the Micronaut HTTP client.";
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public String getCategory() {
        return Category.CLIENT;
    }

    @Override
    public String getDocumentation() {
        return "https://docs.micronaut.io/latest/guide/index.html#httpClient";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Optional<Coordinate> micronautPlatformVersion =
            versionResolver.resolve("micronaut-platform");
        micronautPlatformVersion.ifPresent(coordinate ->
            generatorContext.getBuildProperties().put(
                "micronautPlatformVersion",
                coordinate.getVersion()
            )
        );

        generatorContext.addDependency(Dependency.builder()
                .groupId("io.micronaut.platform")
                .artifactId("micronaut-platform")
                .pom(true)
                .version("$micronautPlatformVersion")
                .implementation()
            .build());

        generatorContext.addDependency(Dependency.builder()
                .groupId("io.micronaut")
                .artifactId("micronaut-http-client-jdk") // Use jdk version as Spring and Micronaut has different Netty versions https://github.com/apache/grails-core/issues/15149
                .implementation());

        // micronaut-http-client no longer provides the jackson implementation
        generatorContext.addDependency(Dependency.builder()
                .groupId("io.micronaut.serde")
                .artifactId("micronaut-serde-jackson")
                .implementation());
    }
}
