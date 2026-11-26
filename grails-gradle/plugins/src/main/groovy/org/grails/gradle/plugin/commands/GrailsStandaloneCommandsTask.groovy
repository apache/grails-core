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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.FileTime

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import org.apache.grails.gradle.model.cli.StandaloneCommandsDocument

/**
 * Writes down, for the commands of the project that are run as a process of their own
 * ({@code StandaloneCommand}), the class path the {@code grails} command line starts them with:
 * {@link StandaloneCommandsDocument}, in {@link #LOCATION} of the directory of the project, beside
 * the catalog of the commands, which says which commands those are.
 *
 * <p>The Grails Gradle plugin has the task run after the task that writes the catalog, which runs
 * after {@code classes}: every build that compiles the project leaves the file current. The command
 * line takes the file for out of date when it is older than the catalog, or, for a command of the
 * project itself, older than a source of the project; the task holds itself to that rule, and writes
 * a file that is older than one of those again although what it says is the same.</p>
 *
 * <p>The file is nothing but paths of this machine, so the task is never taken from a build cache.
 * A project without such a command gets a file without a path. Where the command line could not keep
 * the class path current by itself, because it has the output of another project of the build, or
 * because it does not resolve completely, the file has no path either and says why, and the command
 * line runs the commands with Gradle.</p>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'The file holds paths of this machine')
abstract class GrailsStandaloneCommandsTask extends DefaultTask {

    /** The name of the task. */
    public static final String TASK_NAME = 'grailsStandaloneCommands'

    /** Where the file is written, in the directory of the project, whatever its build directory is. */
    public static final String LOCATION = StandaloneCommandsDocument.LOCATION

    /** The catalog of the commands of the project, which says which of them run as a process of their own. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getCatalogFile()

    /** The directories the classes and the resources of the project are in. */
    @Classpath
    abstract ConfigurableFileCollection getApplicationClasspath()

    /** What the project depends on at run time, and its command line tier. */
    @Classpath
    abstract ConfigurableFileCollection getDependencyClasspath()

    /** The directories of the sources of the project. */
    @Input
    abstract ListProperty<String> getSourceDirectories()

    /** The projects whose output is on the class path, by the paths they have in the build. */
    @Input
    abstract ListProperty<String> getProjectOutputs()

    /** What of the class path could not be resolved, as {@link GrailsCommandCatalogTask#getUnresolved} has it. */
    @Input
    abstract MapProperty<String, List<String>> getUnresolved()

    /** The version of Java the classes of the project are compiled for. */
    @Input
    abstract Property<Integer> getJavaVersion()

    /** Where the file is written: {@link #LOCATION}, where the command line reads it. */
    @OutputFile
    abstract RegularFileProperty getStandaloneCommandsFile()

    GrailsStandaloneCommandsTask() {
        outputs.upToDateWhen(new NotOlderThanWhatItIsMadeFrom())
    }

    /**
     * @return the paths the file holds for the output of the project, those that exist: a project that was moved has other paths, and another file
     */
    @Input
    List<String> getApplicationPaths() {
        paths(applicationClasspath.files)
    }

    /**
     * @return the paths the file holds for what the project depends on
     */
    @Input
    List<String> getDependencyPaths() {
        paths(dependencyClasspath.files)
    }

    @TaskAction
    void write() {
        Map<String, String> commands = StandaloneCommandsDocument.commandsOf(catalogFile.get().asFile.bytes)
        String json = StandaloneCommandsDocument.write(commands, applicationPaths, dependencyPaths, sourceDirectories.get(),
                javaVersion.get().intValue(), throughGradle())
        File target = standaloneCommandsFile.get().asFile
        target.parentFile.mkdirs()
        target.setBytes(json.getBytes(StandardCharsets.US_ASCII))
    }

    // why the command line cannot keep the class path current by itself; null when it can
    private String throughGradle() {
        List<String> projects = projectOutputs.get()
        if (projects) {
            return "their class path has the output of ${projects.size() == 1 ? 'another project' : 'other projects'} of the build " +
                    "(${projects.take(3).join(', ')}${projects.size() > 3 ? ', ...' : ''}), which only the build keeps current"
        }
        List<String> incomplete = unresolved.get().findAll { String name, List<String> failures -> !failures.isEmpty() }.keySet().toList()
        if (incomplete) {
            return "${incomplete.join(' and ')} cannot be resolved completely, so their class path is not known"
        }
        null
    }

    // The files and directories that are there: a directory of classes that no compiler wrote to, as the one of
    // Java in a project without Java sources, is on the class path of the build and does not exist, and the
    // command line takes an entry that is not there for one that is gone.
    private static List<String> paths(Collection<File> files) {
        List<String> paths = []
        for (File file : files) {
            if (file.exists()) {
                paths << file.absolutePath
            }
        }
        paths
    }

    /**
     * Whether the file is as new as everything the command line compares it with: the catalog, and the
     * sources of the project. A class of its own and not a closure, which the configuration cache
     * would have to store with what it encloses.
     */
    static class NotOlderThanWhatItIsMadeFrom implements Spec<Task>, Serializable {

        private static final long serialVersionUID = 1L

        @Override
        boolean isSatisfiedBy(Task task) {
            GrailsStandaloneCommandsTask standalone = (GrailsStandaloneCommandsTask) task
            File file = standalone.standaloneCommandsFile.get().asFile
            File catalog = standalone.catalogFile.get().asFile
            if (!file.isFile() || !catalog.isFile()) {
                return true
            }
            FileTime written
            try {
                written = Files.getLastModifiedTime(file.toPath())
                if (written < Files.getLastModifiedTime(catalog.toPath())) {
                    task.logger.info('The catalog of the commands is newer than {}, which is written again', LOCATION)
                    return false
                }
            } catch (IOException ignored) {
                return true
            }
            String changed = StandaloneCommandsDocument.changedSince(standalone.sourceDirectories.get(), written)
            if (changed != null) {
                task.logger.info('{} is newer than {}, which is written again', changed, LOCATION)
            }
            changed == null
        }
    }

    /**
     * Gives the projects among what a configuration resolved to, by the paths they have in the build.
     * A class of its own and not a closure, which the configuration cache would have to store with what it encloses.
     */
    static class ProjectOutputs implements Transformer<List<String>, Set<ResolvedArtifactResult>> {

        @Override
        List<String> transform(Set<ResolvedArtifactResult> artifacts) {
            Set<String> projects = new LinkedHashSet<String>()
            for (ResolvedArtifactResult artifact : artifacts) {
                ComponentIdentifier component = artifact.id.componentIdentifier
                if (component instanceof ProjectComponentIdentifier) {
                    projects << ((ProjectComponentIdentifier) component).buildTreePath
                }
            }
            new ArrayList<String>(projects)
        }
    }
}
