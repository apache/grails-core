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

package org.grails.gradle.plugin.views

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec
import org.gradle.work.InputChanges

import javax.inject.Inject

/**
 * Abstract Gradle task for compiling templates, using GenericGroovyTemplateCompiler
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@CacheableTask
abstract class AbstractGroovyTemplateCompileTask extends AbstractCompile {

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

    @Input
    final Property<String> fileExtension

    @Input
    final Property<String> scriptBaseName

    @Input
    final Property<String> compilerName

    @Input
    final SetProperty<String> projectPackageNames

    @PathSensitive(PathSensitivity.RELATIVE)
    @Override
    FileTree getSource() {
        return super.getSource();
    }

    private ExecOperations execOperations

    @Inject
    AbstractGroovyTemplateCompileTask(ExecOperations execOperations, ObjectFactory objectFactory, String extensionDefault, String scriptBaseNameDefault, String compilerNameDefault) {
        this.execOperations = execOperations
        packageName = objectFactory.property(String).convention(project.name ?: project.projectDir.canonicalFile.name)
        srcDir = objectFactory.directoryProperty()
        compileOptions = new ViewCompileOptions(objectFactory)
        fileExtension = objectFactory.property(String).convention(extensionDefault)
        scriptBaseName = objectFactory.property(String).convention(scriptBaseNameDefault)
        compilerName = objectFactory.property(String).convention(compilerNameDefault)
        grailsConfigurationPaths = objectFactory.fileCollection()
        grailsConfigurationPaths.from(
                //TODO: historically this only used .yml, should it explore all configuration paths?
                project.layout.projectDirectory.file('grails-app/conf/application.yml')
        )

        projectPackageNames = objectFactory.setProperty(String).convention(project.provider {
            getProjectPackageNames(project.projectDir)
        })
    }

    @Override
    void setSource(Object source) {
        srcDir.set(project.layout.projectDirectory.dir(source.toString()))
        if (!srcDir.getAsFile().get().isDirectory()) {
            throw new IllegalArgumentException("The source for ${fileExtension.get().toUpperCase()} compilation must be a single directory, but was $source")
        }
        super.setSource(source)
    }

    @TaskAction
    void execute(InputChanges inputs) {
        ExecResult result = execOperations.javaexec(
                new Action<JavaExecSpec>() {
                    @Override
                    @CompileDynamic
                    void execute(JavaExecSpec javaExecSpec) {
                        javaExecSpec.mainClass.set(compilerName)
                        javaExecSpec.classpath = classpath

                        List<String> jvmArgs = compileOptions.forkOptions.jvmArgs
                        if (jvmArgs) {
                            javaExecSpec.jvmArgs(jvmArgs)
                        }
                        javaExecSpec.maxHeapSize = compileOptions.forkOptions.memoryMaximumSize
                        javaExecSpec.minHeapSize = compileOptions.forkOptions.memoryInitialSize

                        String packageImports = projectPackageNames.get().join(',') ?: packageName.get()

                        String configFiles = grailsConfigurationPaths.files.collect { it.canonicalPath }.join(",")

                        List<String> arguments = [
                                srcDir.get().asFile.canonicalPath,
                                destinationDirectory.get().asFile.canonicalPath,
                                targetCompatibility,
                                packageImports,
                                packageName.get(),
                                configFiles,
                                compileOptions.encoding.get()
                        ] as List<String>

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

    Set<String> getProjectPackageNames(File baseDir) {
        File rootDir = baseDir ? new File(baseDir, "grails-app${File.separator}domain") : null
        Set<String> packageNames = []
        if (rootDir?.exists()) {
            populatePackages(rootDir, packageNames, '')
        }
        return packageNames
    }

    protected populatePackages(File rootDir, Collection<String> packageNames, String prefix) {
        rootDir.eachDir { File dir ->
            String dirName = dir.name
            if (!dir.hidden && !dirName.startsWith('.')) {
                packageNames << "${prefix}${dirName}".toString()
                populatePackages(dir, packageNames, "${prefix}${dirName}.")
            }
        }
    }
}
