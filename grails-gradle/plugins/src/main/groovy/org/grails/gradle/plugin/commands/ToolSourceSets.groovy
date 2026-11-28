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

import groovy.transform.CompileStatic

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSourceSpec
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.GroovyCompile

import org.apache.grails.gradle.model.cli.ToolBuildNames
import org.grails.gradle.plugin.core.FactoriesCompileInput
import org.grails.gradle.plugin.core.PruneGeneratedFactories

/**
 * The source sets the tools of a project are in, which are the same in an application and in a plugin.
 * Where the source of a tool is decides what it is compiled against, and so what it can use:
 * <ul>
 *   <li>{@link #CLI}, {@code src/cli}: compiled against the classes of the project, the tool-only
 *       dependencies ({@code grailsCli} and {@code grailsCliStandalone}) and the classes of
 *       {@link #STANDALONE}. The tools the build runs, with the application started around them
 *       unless a tool says otherwise. {@link #LEGACY_FOLDER} of a project is compiled with it, and
 *       is deprecated.</li>
 *   <li>{@link #STANDALONE}, {@code src/standalone}: compiled against the tool API and
 *       {@code grailsCliStandalone} only, never the classes of the project. The tools the
 *       {@code grails} command line runs as a process of their own.</li>
 * </ul>
 * Nothing of either is on the class path the application runs with or in an archive it is packaged
 * as. Both the Grails Gradle plugin (the consuming side) and the plugin that publishes the companions
 * of a Grails plugin (the producing side) make them, whichever is applied first.
 *
 * @since 9.0
 */
@CompileStatic
final class ToolSourceSets {

    /** The source set of the tools the build runs: {@code src/cli}. */
    public static final String CLI = ToolBuildNames.CLI_SOURCE_SET

    /** The source set of the tools that run as a process of their own: {@code src/standalone}. */
    public static final String STANDALONE = ToolBuildNames.STANDALONE_SOURCE_SET

    /** The deprecated folder of tools, in the directory of the project, compiled with {@link #CLI}. */
    public static final String LEGACY_FOLDER = ToolBuildNames.LEGACY_SOURCE_FOLDER

    /** The folder of the scripts of a project, which a plugin publishes with its {@code -cli} companion. */
    public static final String SCRIPTS_FOLDER = 'src/main/scripts'

    private ToolSourceSets() {
    }

    /**
     * Makes the two source sets of the tools of the project when they are not there: {@link #STANDALONE}
     * and {@link #CLI}, each with a compilation that merges and prunes the registrations of its tools as
     * the one of the main source set does. {@link #LEGACY_FOLDER} is a Groovy source directory of
     * {@link #CLI}, and its compilation warns when it has sources.
     *
     * @param project a project with the {@code java} plugin
     */
    static void create(Project project) {
        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        if (sourceSets.findByName(STANDALONE) == null) {
            registersTools(project, sourceSets.create(STANDALONE))
        }
        if (sourceSets.findByName(CLI) == null) {
            SourceSet cli = sourceSets.create(CLI)
            registersTools(project, cli)
            File legacy = project.file(LEGACY_FOLDER)
            project.plugins.withId('groovy') {
                cli.extensions.getByType(GroovySourceDirectorySet).srcDir(legacy)
                project.tasks.named(cli.getCompileTaskName('groovy')).configure { Task compile ->
                    compile.doFirst(new WarnOfLegacyFolder(legacy))
                }
            }
        }
    }

    /**
     * @param project a project with the {@code java} plugin
     * @return the source set {@link #CLI}, made when it is not there
     */
    static SourceSet cli(Project project) {
        create(project)
        project.extensions.getByType(SourceSetContainer).getByName(CLI)
    }

    /**
     * @param project a project with the {@code java} plugin
     * @return the source set {@link #STANDALONE}, made when it is not there
     */
    static SourceSet standalone(Project project) {
        create(project)
        project.extensions.getByType(SourceSetContainer).getByName(STANDALONE)
    }

    /**
     * Whether the project has a source of a tool the build runs: a Groovy or Java file below
     * {@code src/cli} or {@link #LEGACY_FOLDER}, or any file below {@link #SCRIPTS_FOLDER}. Decided
     * when the build is configured; a configuration cache entry is not reused when it changes.
     *
     * @param project a project
     * @return whether it has one
     */
    static boolean hasCliSources(Project project) {
        hasSources(project, [ToolBuildNames.CLI_SOURCE_FOLDER, LEGACY_FOLDER], [SCRIPTS_FOLDER])
    }

    /**
     * Whether the project has a source of a tool that runs as a process of its own: a Groovy or Java
     * file below {@code src/standalone}. Decided as {@link #hasCliSources} is.
     *
     * @param project a project
     * @return whether it has one
     */
    static boolean hasStandaloneSources(Project project) {
        hasSources(project, [ToolBuildNames.STANDALONE_SOURCE_FOLDER], [])
    }

    private static boolean hasSources(Project project, List<String> sourceFolders, List<String> anyFileFolders) {
        Provider<Boolean> found = project.providers.of(ToolSourcesValueSource) { ValueSourceSpec<ToolSourcesValueSource.Parameters> spec ->
            spec.parameters.projectDirectory.set(project.layout.projectDirectory)
            spec.parameters.sourceFolders.set(sourceFolders)
            spec.parameters.anyFileFolders.set(anyFileFolders)
        }
        found.get()
    }

    // The compiler merges the hand-authored grails-cli.factories into the one it generates for the tools,
    // so a change to that file has to recompile them even when no tool changed, and a tool deleted
    // without anything being recompiled, which only an incremental Groovy compilation
    // (options.incremental = true) does, has to leave the generated file all the same.
    private static void registersTools(Project project, SourceSet sourceSet) {
        project.plugins.withId('groovy') {
            project.tasks.named(sourceSet.getCompileTaskName('groovy'), GroovyCompile).configure { GroovyCompile task ->
                FactoriesCompileInput.registerCliFactories(project, task)
                PruneGeneratedFactories.register(project, task, PruneGeneratedFactories.CLI_FACTORIES_LOCATION,
                        FactoriesCompileInput.CLI_FACTORIES_PATHS)
            }
        }
    }

    /**
     * Says, when the tools are compiled, that {@link #LEGACY_FOLDER} is deprecated, once for each
     * compilation, when it has sources. A class of its own and not a closure, which the configuration
     * cache would have to store with what it encloses.
     */
    static class WarnOfLegacyFolder implements Action<Task>, Serializable {

        private static final long serialVersionUID = 1L

        /** What the compilation says. */
        public static final String MESSAGE = 'grails-app/commands is deprecated: the classes in it are compiled and run as before, ' +
                'and are no longer packaged with the application. Move them to src/cli/groovy, as they are; ' +
                'a new tool is written there by: grails create-tool'

        private final File directory

        WarnOfLegacyFolder(File directory) {
            this.directory = directory
        }

        @Override
        void execute(Task task) {
            if (ToolSourcesValueSource.has(directory.toPath(), ToolSourcesValueSource.SOURCE_ENDINGS)) {
                task.logger.warn(MESSAGE)
            }
        }
    }
}
