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
package org.grails.forge.build.gradle;

import jakarta.annotation.Nonnull;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.options.BuildTool;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GradleBuildCreator {

    @Nonnull
    public GradleBuild create(@Nonnull GeneratorContext generatorContext) {
        GradleDsl gradleDsl = BuildTool.DEFAULT_OPTION
                .getGradleDsl()
                .orElseThrow(() -> new IllegalArgumentException("GradleBuildCreator can only create Gradle builds"));
        List<GradlePlugin> gradlePlugins = generatorContext.getBuildPlugins()
                .stream()
                .filter(GradlePlugin.class::isInstance)
                .map(GradlePlugin.class::cast)
                .sorted(Comparator.comparingInt(Ordered::getOrder))
                .collect(Collectors.toList());

        List<GradleRepository> buildRepositories = generatorContext.getBuildRepositories()
                .stream()
                .sorted(Comparator.comparingInt(Ordered::getOrder)).
                toList();

        List<GradleRepository> repositories = generatorContext.getRepositories()
                .stream()
                .sorted(Comparator.comparingInt(Ordered::getOrder))
                .toList();

        return new GradleBuild(gradleDsl, resolveDependencies(generatorContext), resolveBuildscriptDependencies(generatorContext), gradlePlugins, buildRepositories, repositories);
    }

    @Nonnull
    private List<GradleDependency> resolveDependencies(@Nonnull GeneratorContext generatorContext) {
        return generatorContext.getDependencies()
                .stream()
                .map(dep -> new GradleDependency(dep, generatorContext))
                .sorted(GradleDependency.COMPARATOR)
                .collect(Collectors.toList());
    }

    @Nonnull
    private List<GradleDependency> resolveBuildscriptDependencies(@Nonnull GeneratorContext generatorContext) {
        return generatorContext.getBuildscriptDependencies()
                .stream()
                .map(dep -> new GradleDependency(dep, generatorContext))
                .sorted(GradleDependency.COMPARATOR)
                .collect(Collectors.toList());
    }
}
