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
package org.grails.gradle.plugin.views.gsp

import javax.inject.Inject

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.api.tasks.util.PatternSet
import org.gradle.process.ExecOperations
import org.gradle.process.JavaExecSpec

/**
 * Writes the tag library index describing the tag libraries in this project.
 *
 * <p>The index has to exist before anything that resolves tag calls is compiled, which is why this
 * runs ahead of compilation rather than being produced as a side effect of it. Generating it for the
 * whole source set at once is also what lets a renamed or deleted tag library disappear from it,
 * where an index accumulated class by class keeps describing tags that no longer exist.
 *
 * <p>The work runs in a forked process against the project's own compile classpath, because the rules
 * that decide what a tag is belong to the framework being built rather than to the build tooling, and
 * must be the same rules the application applies when it starts.
 *
 * @since 8.0.0
 */
@CacheableTask
@CompileStatic
abstract class GenerateTagLibraryIndexTask extends DefaultTask {

    static final String GENERATOR_CLASS = 'org.grails.taglib.index.TagLibraryIndexGenerator'

    private final ExecOperations execOperations

    @Inject
    GenerateTagLibraryIndexTask(ExecOperations execOperations) {
        this.execOperations = execOperations
        description = 'Generates the tag library index used to resolve tag calls at compile time'
        group = 'build'
    }

    /**
     * The directories holding tag library sources.
     *
     * <p>Defaults to {@code grails-app/taglib}. A project keeping tag libraries elsewhere can add
     * those directories, which is what makes them resolvable in the same compilation that defines
     * them; without that they are still described as they compile, and so are resolvable to whatever
     * is compiled afterwards.
     */
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getSourceDirectories()

    /**
     * The source roots a type this project declares may be resolved from.
     *
     * <p>A tag library commonly refers to a service, base class or trait of the same project, none of
     * which exist as classes yet. Their source is compiled alongside it so that what they contribute -
     * a namespace, tags, a parameter type - is read rather than guessed.
     */
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getResolutionSourceRoots()

    /**
     * Where the index is written. Placed on the compile classpath and packaged with the artifact.
     */
    @OutputDirectory
    abstract DirectoryProperty getDestinationDirectory()

    /**
     * Where the settings this build declared are written.
     *
     * <p>Kept apart from the descriptors because the two travel differently: the descriptors are
     * published, and the settings say how this project is compiled and must reach no one else. Sharing
     * a directory would put them wherever the descriptors go, including into an executable archive
     * built from the runtime classpath, where no exclusion on an archive task can reach them.
     *
     * <p>Written beside the descriptors when unset, which suits a caller with nothing to publish.
     */
    @OutputDirectory
    @Optional
    abstract DirectoryProperty getSettingsDirectory()

    /**
     * The classpath the generator runs against, which supplies the framework's discovery rules.
     */
    @Classpath
    abstract ConfigurableFileCollection getGeneratorClasspath()

    /**
     * Whether this compilation writes parameter names into class files. It decides whether a tag's
     * attributes and body parameters have to carry those names to be dispatchable, so the index must
     * be generated under the same setting the sources are compiled with.
     */
    @Input
    abstract Property<Boolean> getParameterNamesRetained()

    /**
     * The source encoding, matching the one compilation uses.
     */
    @Input
    @Optional
    abstract Property<String> getSourceEncoding()

    /**
     * Whether a tag no compiled tag library declares fails compilation rather than being reported as a
     * warning. Recorded alongside the index, where the compiler reads it.
     */
    @Input
    abstract Property<Boolean> getStrictTags()

    /**
     * Whether a tag call written without its namespace may be compiled into a direct invocation.
     * Recorded alongside the index, where the compiler reads it.
     */
    @Input
    abstract Property<Boolean> getUnqualifiedTagCalls()

    /**
     * Namespaces the build declares as filled in while the application runs. Tags in them are never
     * reported as unknown.
     */
    @Input
    abstract SetProperty<String> getDynamicTagNamespaces()

    /**
     * The Java the index is generated with. It runs against the project's own compile classpath, so it
     * has to be the Java that classpath was built for rather than whichever one happens to be running
     * Gradle.
     */
    @Nested
    abstract Property<JavaLauncher> getJavaLauncher()

    @TaskAction
    void generate() {
        File destination = destinationDirectory.get().asFile
        destination.mkdirs()
        List<File> directories = new ArrayList<File>(sourceDirectories.files.findAll { File dir -> dir.isDirectory() })
        // A directory that exists but holds no sources is not worth forking a process to read, and a
        // project with no tag libraries at all must not need the generator on its classpath to build.
        if (directories && !sourceDirectories.asFileTree.matching(new PatternSet().include('**/*.groovy')).empty) {
            List<File> roots = new ArrayList<File>(resolutionSourceRoots.files.findAll { File dir -> dir.isDirectory() })
            List<String> arguments = [
                    destination.canonicalPath,
                    String.valueOf(parameterNamesRetained.getOrElse(true)),
                    sourceEncoding.getOrElse('UTF-8'),
                    String.valueOf(directories.size())
            ]
            arguments.addAll(directories.collect { File source -> source.canonicalPath })
            arguments.addAll(roots.collect { File root -> root.canonicalPath })
            // One process for every source directory at once: the generator rewrites the index in
            // full, so a second process would erase what the first wrote.
            execOperations.javaexec { JavaExecSpec spec ->
                spec.mainClass.set(GENERATOR_CLASS)
                spec.classpath = generatorClasspath
                if (javaLauncher.present) {
                    spec.executable = javaLauncher.get().executablePath.asFile.absolutePath
                }
                spec.args(arguments)
            }.assertNormalExitValue()
        }
        else {
            TagLibraryIndexFiles.clearIndex(destination)
        }
        File settingsDestination = settingsDirectory.present ? settingsDirectory.get().asFile : destination
        settingsDestination.mkdirs()
        TagLibraryIndexFiles.writeSettings(settingsDestination, strictTags.getOrElse(false),
                dynamicTagNamespaces.getOrElse([] as Set), unqualifiedTagCalls.getOrElse(false),
                TagLibraryIndexFiles.readNamespaces(destination))
    }
}
