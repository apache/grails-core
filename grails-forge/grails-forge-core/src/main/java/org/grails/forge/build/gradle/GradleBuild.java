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

import io.micronaut.core.annotation.NonNull;
import org.grails.forge.template.Writable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GradleBuild {
    private static final Logger LOG = LoggerFactory.getLogger(GradleBuild.class);

    private final GradleDsl dsl;
    private final List<GradleRepository> repositories;
    private final List<GradleRepository> buildRepositories;
    private final List<GradleDependency> dependencies;
    private final List<GradleDependency> buildscriptDependencies;
    private final List<GradlePlugin> plugins;

    public GradleBuild() {
        this(GradleDsl.GROOVY, Collections.emptyList(), Collections.emptyList());
    }

    public GradleBuild(@NonNull GradleDsl gradleDsl,
                       @NonNull List<GradleDependency> dependencies,
                       @NonNull List<GradleDependency> buildscriptDependencies) {
        this(gradleDsl, dependencies, buildscriptDependencies, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public GradleBuild(@NonNull GradleDsl gradleDsl,
                       @NonNull List<GradleDependency> dependencies,
                       @NonNull List<GradleDependency> buildscriptDependencies,
                       @NonNull List<GradlePlugin> plugins,
                       @NonNull List<GradleRepository> buildRepositories,
                       @NonNull List<GradleRepository> repositories) {
        this.dsl = gradleDsl;
        this.dependencies = dependencies;
        this.buildscriptDependencies = buildscriptDependencies;
        this.plugins = plugins;
        this.buildRepositories = buildRepositories;
        this.repositories = repositories;
    }

    @NonNull
    public GradleDsl getDsl() {
        return dsl;
    }

    @NonNull
    public List<GradleDependency> getDependencies() {
        return dependencies;
    }

    @NonNull
    public List<GradleRepository> getBuildRepositories() {
        return buildRepositories;
    }

    @NonNull
    public List<GradleRepository> getRepositories() {
        return repositories;
    }

    @NonNull
    public List<GradleDependency> getBuildSrcDependencies() {
        return buildscriptDependencies.stream().filter(gradleDependency -> !gradleDependency.getConfiguration().equals(GradleConfiguration.CLASSPATH)).collect(Collectors.toList());
    }

    @NonNull
    public List<GradleDependency> getBuildscriptDependencies() {
        return buildscriptDependencies.stream().filter(gradleDependency -> gradleDependency.getConfiguration().equals(GradleConfiguration.CLASSPATH)).collect(Collectors.toList());
    }

    @NonNull
    public List<GradleDependency> getAllBuildscriptDependencies() {
        return buildscriptDependencies;
    }

    @NonNull
    public List<GradlePlugin> getPlugins() {
        return plugins;
    }

    @NonNull
    public List<GradlePlugin> getPluginsWithVersion() {
        return plugins.stream().filter(plugin -> plugin.getVersion() != null).collect(Collectors.toList());
    }

    @NonNull
    public List<GradlePlugin> getPluginsWithoutApply() {
        return plugins.stream().filter(plugin -> !plugin.useApplyPlugin()).collect(Collectors.toList());
    }

    @NonNull
    public List<GradlePlugin> getPluginsWithApply() {
        return plugins.stream().filter(plugin -> plugin.useApplyPlugin()).collect(Collectors.toList());
    }

    @NonNull
    public String renderExtensions() {
        return renderWritableExtensions(Stream.concat(
                buildscriptDependencies.stream().map(GradleDependency::getExtension).filter(Objects::nonNull),
                plugins.stream().map(GradlePlugin::getExtension)));
    }

    @NonNull
    public String renderSettingsExtensions() {
        return renderWritableExtensions(plugins.stream().map(GradlePlugin::getSettingsExtension));
    }

    @NonNull
    private String renderWritableExtensions(Stream<Writable> extensions) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        extensions
                .filter(Objects::nonNull)
                .forEach(writable -> {
                    try {
                        writable.write(outputStream);
                        outputStream.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("IO Exception rendering Gradle Plugin extension");
                        }
                    }
                });
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    @NonNull
    public Set<String> getPluginsImports() {
        Set<String> imports = new HashSet<>();
        for (GradlePlugin p : plugins) {
            Set<String> pluginImports = p.getBuildImports();
            if (pluginImports != null) {
                imports.addAll(pluginImports);
            }
        }
        return imports.stream().map(it -> it + System.lineSeparator()).collect(Collectors.toSet());
    }
}
