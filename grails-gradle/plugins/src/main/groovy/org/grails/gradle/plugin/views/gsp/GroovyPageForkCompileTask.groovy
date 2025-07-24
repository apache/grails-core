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
import org.gradle.api.tasks.CacheableTask
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
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec

import org.grails.gradle.plugin.views.ViewCompileOptions

/**
 * Abstract Gradle task for compiling templates, using GroovyPageForkedCompiler
 * This Task is a Forked Java Task that is configurable with fork options provided
 * by {@link ViewCompileOptions}
 *
 * @author David Estes
 * @since 4.0
 */
@CompileStatic
@CacheableTask
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

    @OutputDirectory
    final DirectoryProperty destinationDirectory

    @Inject
    GroovyPageForkCompileTask(ExecOperations execOperations, ObjectFactory objectFactory) {
        this.execOperations = execOperations
        packageName = objectFactory.property(String).convention(project.name ?: project.projectDir.canonicalFile.name)
        srcDir = objectFactory.directoryProperty()
        compileOptions = objectFactory.newInstance(ViewCompileOptions.class)
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
        if(Directory.isAssignableFrom(source.class)) {
            this.srcDir.set(source as Directory)
        }
        else if(File.isAssignableFrom(source.class)) {
            this.srcDir.set(source as File)
            if (!srcDir.getAsFile().get().isDirectory()) {
                throw new IllegalArgumentException("The source for ${getFileExtension().toUpperCase()} compilation must be a single directory, but was $source")
            }
        }
        else if(DirectoryProperty.isAssignableFrom(source.class)) {
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
                        javaExecSpec.mainClass.set(getCompilerName())
                        javaExecSpec.setClasspath(getClasspath())

                        def jvmArgs = compileOptions.forkOptions.jvmArgs
                        if (jvmArgs) {
                            javaExecSpec.jvmArgs(jvmArgs)
                        }
                        javaExecSpec.setMaxHeapSize(compileOptions.forkOptions.memoryMaximumSize)
                        javaExecSpec.setMinHeapSize(compileOptions.forkOptions.memoryInitialSize)

                        String configFiles = grailsConfigurationPaths.files.collect { it.canonicalPath }.join(",")

                        Path path = Paths.get(tmpDirPath)
                        File tmp = Files.exists(path) ? path.toFile() : Files.createDirectories(path).toFile()
                        List<String> arguments = [
                                srcDir.get().asFile.canonicalPath,
                                destinationDirectory.get().asFile.canonicalPath,
                                tmp.canonicalPath,
                                targetCompatibility,
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
