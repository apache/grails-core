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
package org.grails.gradle.plugin.commands

import org.gradle.api.provider.Property

/**
 * Configuration for {@link GrailsCliArtifactGradlePlugin}.
 *
 * @since 8.0
 */
abstract class CliArtifactExtension {

    /**
     * The artifactId of the companion cli artifact; defaults to {@code "${project.name}-cli"}.
     *
     * <p>When customized, the companion's variants carry {@code "${group}:${artifactId}"} as an
     * additional capability (alongside the Gradle default {@code "${group}:${project.name}-cli"}),
     * so in-build consumers can require the advertised coordinate either way.</p>
     */
    abstract Property<String> getArtifactId()

    /**
     * The artifactId of the companion with the standalone tools of {@code src/standalone}; defaults to
     * {@code "${project.name}-cli-standalone"}. Customized, it is an additional capability of the
     * companion's variants, as {@link #getArtifactId} is.
     *
     * @since 9.0
     */
    abstract Property<String> getStandaloneArtifactId()

    /**
     * The optional {@code Automatic-Module-Name} of the companion cli jar. Recommended: a unique,
     * stable JPMS module name matching the cli source set's package
     * (e.g. {@code com.example.myplugin.cli}).
     */
    abstract Property<String> getAutomaticModuleName()

    /**
     * Whether the plugin adds the default dependencies of the companions — to {@code cliApi} the plugin
     * project's own runtime classes, the {@code org.apache.grails:grails-core-cli} tool API and, when the
     * plugin has standalone tools, its own {@code -cli-standalone}; to {@code standaloneApi} the tool API
     * alone. Each only when the companion is made, which it is when the project has sources of tools.
     * Defaults to {@code true}; disable to wire the dependency graph of the companions manually
     * (e.g. with project dependencies inside a composite build).
     */
    abstract Property<Boolean> getDefaultDependencies()
}
