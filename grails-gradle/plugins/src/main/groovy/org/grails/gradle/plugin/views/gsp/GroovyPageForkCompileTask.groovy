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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import javax.inject.Inject

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec
import org.gradle.work.DisableCachingByDefault

import org.grails.gradle.plugin.views.ViewCompileOptions

/**
 * Abstract Gradle task for compiling templates, using GroovyPageForkedCompiler
 * This Task is a Forked Java Task that is configurable with fork options provided
 * by {@link ViewCompileOptions}
 *
 * <p>Not cacheable. A page is compiled by a forked Groovy, and what comes out depends on which
 * Groovy did it -- which this task's inputs do not describe, because {@code AbstractCompile} does
 * not track its own classpath: an application building a native image resolves Groovy 6, and one
 * training a cache resolves Groovy 5. Cached, the first build's pages were handed to the second,
 * which failed at the moment a page was first rendered, with
 * {@code BUG! your call tried to do a property set} -- long after the build said it had
 * succeeded.</p>
 *
 * <p>Which Java did the compiling is described, by {@link #getJavaLauncher()}. Which Groovy is
 * not, and this stays uncacheable until it is.</p>
 *
 * <p>Compiling them again costs seconds. Getting this wrong costs an afternoon.</p>
 *
 * @author David Estes
 * @since 4.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'What a forked compiler produces is not described by this task\'s inputs')
abstract class GroovyPageForkCompileTask extends AbstractCompile {

    @Input
    @Optional
    final Property<String> packageName

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final ConfigurableFileCollection grailsConfigurationPaths

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty srcDir

    @Nested
    final ViewCompileOptions compileOptions

    @LocalState
    String tmpDirPath

    @Input
    @Optional
    final Property<String> serverpath

    private ExecOperations execOperations

    /**
     * The Java runtime the pages are compiled by.
     *
     * <p>Compilation is forked, and a forked process runs whatever JVM it is given rather than the
     * one the project asked for. Left to itself it inherits the JVM running Gradle, so a project
     * declaring a toolchain gets its pages compiled by a different Java than everything else it
     * builds -- which shows up as an {@code UnsupportedClassVersionError} at the moment a page is
     * first rendered, long after the build called itself successful.</p>
     *
     * <p>Nested rather than internal because the Java that did the compiling is part of what the
     * result is: pages built by one are not left standing when the build asks for another.</p>
     */
    @Nested
    abstract Property<JavaLauncher> getJavaLauncher()

    @OutputDirectory
    final DirectoryProperty destinationDirectory

    @Inject
    GroovyPageForkCompileTask(ExecOperations execOperations, ObjectFactory objectFactory) {
        this.execOperations = execOperations
        packageName = objectFactory.property(String).convention(project.name ?: project.projectDir.canonicalFile.name)
        srcDir = objectFactory.directoryProperty()
        compileOptions = objectFactory.newInstance(ViewCompileOptions)
        serverpath = objectFactory.property(String)
        grailsConfigurationPaths = objectFactory.fileCollection()
        grailsConfigurationPaths.from(
                project.layout.projectDirectory.file('grails-app/conf/application.yml'),
                project.layout.projectDirectory.file('grails-app/conf/application.groovy')
        )
        destinationDirectory = objectFactory.directoryProperty().convention(project.layout.buildDirectory.dir('gsp-classes/main'))
    }

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSource() {
        return super.getSource()
    }

    @Override
    void setSource(Object source) {
        if (Directory.isAssignableFrom(source.class)) {
            this.srcDir.set(source as Directory)
        }
        else if (File.isAssignableFrom(source.class)) {
            this.srcDir.set(source as File)
            if (!srcDir.getAsFile().get().isDirectory()) {
                throw new IllegalArgumentException("The source for ${getFileExtension().toUpperCase()} compilation must be a single directory, but was $source")
            }
        }
        else if (DirectoryProperty.isAssignableFrom(source.class)) {
            this.srcDir.set(source as DirectoryProperty)
        }
        else {
            throw new RuntimeException("Unsupported source type: ${source.class.name}")
        }
        super.setSource(source)
    }

    @TaskAction
    void execute() {
        compile()
    }

    protected void compile() {
        ExecResult result = execOperations.javaexec(
                new Action<JavaExecSpec>() {
                    @Override
                    @CompileDynamic
                    void execute(JavaExecSpec javaExecSpec) {
                        javaExecSpec.executable = javaLauncher.get().executablePath.asFile.absolutePath
                        javaExecSpec.mainClass.set(getCompilerName())
                        javaExecSpec.setClasspath(getClasspath())

                        def jvmArgs = compileOptions.forkOptions.jvmArgs
                        if (jvmArgs) {
                            javaExecSpec.jvmArgs(jvmArgs)
                        }
                        javaExecSpec.setMaxHeapSize(compileOptions.forkOptions.memoryMaximumSize)
                        javaExecSpec.setMinHeapSize(compileOptions.forkOptions.memoryInitialSize)

                        String configFiles = grailsConfigurationPaths.files.collect { it.canonicalPath }.join(',')

                        Path path = Paths.get(tmpDirPath)
                        File tmp = Files.exists(path) ? path.toFile() : Files.createDirectories(path).toFile()
                        List<String> arguments = [
                                srcDir.get().asFile.canonicalPath,
                                destinationDirectory.get().asFile.canonicalPath,
                                tmp.canonicalPath,
                                // What a page is compiled for follows what it is compiled by,
                                // unless the build has said otherwise for itself.
                                targetCompatibility ?: javaLauncher.get().metadata.languageVersion.toString(),
                                packageName.get() as String,
                                serverpath.getOrNull() as String,
                                configFiles,
                                compileOptions.encoding.get()
                        ]

                        prepareArguments(arguments)
                        javaExecSpec.args(arguments)
                    }

                }
        )
        result.assertNormalExitValue()

    }

    void prepareArguments(List<String> arguments) {
        // no-op
    }

    @Input
    protected String getCompilerName() {
        'org.grails.web.pages.GroovyPageForkedCompiler'
    }

    @Input
    String getFileExtension() {
        'gsp'
    }
}
