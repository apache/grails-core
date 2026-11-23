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
import java.util.concurrent.Callable

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.capabilities.Capability
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.apache.grails.gradle.model.cli.CommandCatalogBuilder
import org.apache.grails.gradle.model.cli.CommandCatalogFreshness

/**
 * Writes the catalog of the application commands of the project: the commands that
 * {@code runCommand} can run, from the commands of the project itself ({@code grails-app/commands}),
 * the companion {@code -cli} artifacts of its plugins, and the Grails 7 plugins on its class path.
 *
 * <p>The catalog is one JSON document, {@link CommandCatalogBuilder#FORMAT format 1} of the document
 * {@code help --json} of the command line writes, in {@link #CATALOG_LOCATION} of the directory of the
 * project, which also lists the test phases of the project, its {@code Test} tasks, by the names
 * {@code grails test-app} selects them with. The task reads registrations and descriptors and loads no class, so it starts no
 * application and runs no code of a dependency. The document has no time and no path of this
 * machine in it: the same commands give the same bytes.</p>
 *
 * <p>The catalog is not in the build directory: the command line reads it at that one place in every
 * project, also where the build has moved its build directory, and never runs the build to find out
 * where that is. Beside it the task writes {@link #IGNORE_LOCATION}, which keeps the directory out of a
 * commit: what is in it is of one machine.</p>
 *
 * <p>The Grails Gradle plugin has the task run after {@code classes}, so every build that compiles the
 * project leaves the catalog current. The command line takes a catalog for out of date when a file it is
 * made from was changed after it was written ({@link CommandCatalogFreshness}), which a compiler that
 * writes a descriptor again with the same content does; the task holds itself to that rule as well, and
 * writes a catalog that is older than one of those files again, although what it lists is the same.</p>
 *
 * <p>A class path the commands run with that does not resolve completely, such as a runtime class path
 * with versions that conflict, does not fail the build that compiles the project, which does not need
 * it: the catalog lists the commands of what resolves, says in its diagnostics what does not, and is not
 * complete. Running a command with that class path fails as the application does.</p>
 *
 * @since 9.0
 */
@CompileStatic
@CacheableTask
abstract class GrailsCommandCatalogTask extends DefaultTask {

    /** The name of the task. */
    public static final String TASK_NAME = 'grailsCommandCatalog'

    /** Where the catalog is written, in the directory of the project, whatever its build directory is. */
    public static final String CATALOG_LOCATION = CommandCatalogBuilder.LOCATION

    /** The file that keeps the directory of the catalog out of a commit, in the directory of the project. */
    public static final String IGNORE_LOCATION = '.grails/cache/.gitignore'

    /** What {@link #IGNORE_LOCATION} says: that nothing in its directory is committed, itself included. */
    public static final String IGNORE_CONTENT = '*\n'

    /** What is read from the output of the project: registrations and descriptors, not classes. */
    public static final List<String> REGISTRATION_PATTERNS = [
            'META-INF/grails-cli.factories',
            'META-INF/grails.factories',
            'META-INF/grails-commands/*.json',
    ].asImmutable()

    /**
     * The registrations and descriptors in the output of the project.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getApplicationRegistrations()

    /**
     * The directories the output of the project is in.
     */
    @Internal
    abstract ConfigurableFileCollection getApplicationOutput()

    /**
     * The class path the commands of the project run with, beside its own output.
     */
    @Classpath
    abstract ConfigurableFileCollection getCommandClasspath()

    /**
     * Where each file of the class path comes from, by its path.
     */
    @Internal
    abstract MapProperty<String, String> getOriginsByPath()

    /**
     * Where the files of the class path come from: a jar that is the same under other coordinates gives another catalog.
     */
    @Input
    abstract ListProperty<String> getOrigins()

    /**
     * What of the class path could not be resolved: by the name of each class path, what the build
     * says is missing from it, one line each ({@link UnresolvedLines}); an empty list for a class path
     * that was resolved completely. The commands of what is missing are not listed, and the catalog
     * says so: the class path is resolved leniently, so that a build that compiles the project, which
     * this task finalizes, does not fail because the class path its commands run with does not resolve.
     */
    @Input
    abstract MapProperty<String, List<String>> getUnresolved()

    /**
     * The test phases of the project, which {@code grails test-app} selects with a switch each: the
     * Gradle task of each phase by the name of the phase.
     */
    @Input
    abstract MapProperty<String, String> getTestPhases()

    /** Where the catalog is written: {@link #CATALOG_LOCATION}, where the command line reads it. */
    @OutputFile
    abstract RegularFileProperty getCatalogFile()

    /** The file that keeps the directory of the catalog out of a commit: {@link #IGNORE_LOCATION}. */
    @OutputFile
    abstract RegularFileProperty getIgnoreFile()

    /**
     * The directory of the project, in which the command line looks at what the catalog is made from
     * to tell whether it may be out of date (see {@link CommandCatalogFreshness}).
     */
    @Internal
    abstract DirectoryProperty getProjectDirectory()

    GrailsCommandCatalogTask() {
        outputs.upToDateWhen(new NotOlderThanItsSources())
    }

    @TaskAction
    void write() {
        CommandCatalogBuilder catalog = new CommandCatalogBuilder()
        // one element: the compiler copies a registration written by hand into the classes directory,
        // and the descriptor written by hand beside it stays in the resources directory
        catalog.add(CommandCatalogBuilder.ORIGIN_APPLICATION, applicationOutput.files)
        Map<String, String> origins = originsByPath.get()
        for (File file : commandClasspath.files) {
            catalog.add(origins.get(file.absolutePath) ?: "file:${file.name}".toString(), file)
        }
        for (Map.Entry<String, List<String>> classPath : unresolved.get()) {
            for (String failure : classPath.value) {
                catalog.unresolved(classPath.key, failure)
            }
        }
        // in the order of the phases: the unit tests, the integration tests, the others by name
        Map<String, String> phases = testPhases.get()
        Map<String, String> ordered = new LinkedHashMap<String, String>()
        for (String first : ['unit', 'integration']) {
            if (phases.containsKey(first)) {
                ordered.put(first, phases.get(first))
            }
        }
        ordered.putAll(new TreeMap<String, String>(phases).findAll { String name, String task -> !ordered.containsKey(name) })
        catalog.testPhases(ordered)
        File target = catalogFile.get().asFile
        target.parentFile.mkdirs()
        target.setBytes(catalog.toJson().getBytes(StandardCharsets.US_ASCII))
        File ignore = ignoreFile.get().asFile
        ignore.parentFile.mkdirs()
        ignore.setBytes(IGNORE_CONTENT.getBytes(StandardCharsets.US_ASCII))
        Map<String, Object> document = catalog.build()
        for (Object diagnostic : (List) document.diagnostics) {
            logger.warn('{}', diagnostic)
        }
    }

    /**
     * Whether the catalog is as new as everything the command line compares it with: a catalog that is
     * older than one of those is out of date for the command line, whatever its content is, and is
     * written again. Without a catalog Gradle decides, which runs the task. A class of its own and not a
     * closure, which the configuration cache would have to store with what it encloses.
     */
    static class NotOlderThanItsSources implements Spec<Task>, Serializable {

        private static final long serialVersionUID = 1L

        @Override
        boolean isSatisfiedBy(Task task) {
            GrailsCommandCatalogTask catalogTask = (GrailsCommandCatalogTask) task
            File catalog = catalogTask.catalogFile.get().asFile
            if (!catalog.isFile() || !catalogTask.projectDirectory.isPresent()) {
                return true
            }
            FileTime written
            try {
                written = Files.getLastModifiedTime(catalog.toPath())
            } catch (IOException ignored) {
                return true
            }
            String changed = CommandCatalogFreshness.changedSince(catalogTask.projectDirectory.get().asFile, catalogTask.applicationOutput.files, written)
            if (changed != null) {
                task.logger.info('{} is newer than the catalog of the commands, which is written again', changed)
            }
            return changed == null
        }
    }

    /**
     * Where a resolved artifact comes from, as the catalog names it.
     *
     * @param artifact the artifact
     * @return {@code group:artifact:version} for a module and for a project (which is named by what
     * its variant provides, so the companion of a project of this build is {@code group:name-cli:version}),
     * and {@code file:<name>} for a file that is neither
     */
    static String origin(ResolvedArtifactResult artifact) {
        ComponentIdentifier component = artifact.id.componentIdentifier
        if (component instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier module = (ModuleComponentIdentifier) component
            return "${module.group}:${module.module}:${module.version}"
        }
        List<Capability> capabilities = artifact.variant.capabilities
        if (capabilities) {
            Capability capability = capabilities[0]
            return "${capability.group}:${capability.name}:${capability.version ?: 'unspecified'}"
        }
        "file:${artifact.file.name}"
    }

    /**
     * Gives where each artifact a configuration resolved to comes from, by the path of its file, in their order.
     * A class of its own and not a closure, which the configuration cache would have to store with what it encloses.
     */
    static class OriginsByPath implements Transformer<Map<String, String>, Set<ResolvedArtifactResult>> {

        @Override
        Map<String, String> transform(Set<ResolvedArtifactResult> artifacts) {
            Map<String, String> origins = new LinkedHashMap<String, String>()
            for (ResolvedArtifactResult artifact : artifacts) {
                origins.putIfAbsent(artifact.file.absolutePath, origin(artifact))
            }
            origins
        }
    }

    /**
     * Gives what a class path that is resolved leniently could not resolve, one line each, in the order
     * the build gives it: the first line of what the build says of each failure and of each failure
     * that caused it, without what follows, which names where a module was looked for, and so paths of
     * this machine. Called when the configuration cache is stored, or without it when the task is about
     * to run, and not again when the configuration cache is reused.
     */
    static class UnresolvedLines implements Callable<List<String>> {

        private final ArtifactCollection artifacts

        UnresolvedLines(ArtifactCollection artifacts) {
            this.artifacts = artifacts
        }

        @Override
        List<String> call() {
            // Gradle fails each dependency on a module that cannot be selected, and gives all of them the
            // same cause: one line for each cause, as Gradle says one failure with an identical cause
            Map<String, String> lines = new LinkedHashMap<String, String>()
            for (Throwable failure : artifacts.failures) {
                Throwable cause = failure
                Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>())
                while (cause.cause != null && seen.add(cause)) {
                    cause = cause.cause
                }
                lines.putIfAbsent(messageOf(cause) ?: cause.class.name, firstLines(failure))
            }
            new ArrayList<String>(new LinkedHashSet<String>(lines.values()))
        }
    }

    /**
     * @param failure what the build threw for something it could not resolve
     * @return the first line of its message and of the message of each of its causes, each as a sentence,
     * in one line: {@code Could not find com.example:missing:1.0.}, {@code Could not resolve
     * com.example:lib:1.0. Component is the target of multiple version constraints with conflicting requirements.}
     */
    static String firstLines(Throwable failure) {
        List<String> sentences = []
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>())
        for (Throwable each = failure; each != null && seen.add(each); each = each.cause) {
            String first = (messageOf(each) ?: '').readLines().collect { String line -> line.trim() }.find { String line -> !line.isEmpty() }
            if (first == null) {
                continue
            }
            // a line that leads to the lines after it ends with a colon, and is a sentence without them
            String sentence = first.endsWith(':') ? first.substring(0, first.length() - 1).trim() : first
            sentence = sentence.endsWith('.') ? sentence : "${sentence}.".toString()
            if (!sentences.contains(sentence)) {
                sentences << sentence
            }
        }
        sentences ? sentences.join(' ') : failure.class.simpleName
    }

    /**
     * @param failure a failure
     * @return its message, or {@code null} when it has none or cannot give it
     */
    static String messageOf(Throwable failure) {
        try {
            return failure.message
        } catch (RuntimeException ignored) {
            return null
        }
    }

    /**
     * Gives the origins without the paths.
     */
    static class Origins implements Transformer<List<String>, Map<String, String>> {

        @Override
        List<String> transform(Map<String, String> originsByPath) {
            new ArrayList<String>(originsByPath.values())
        }
    }
}
