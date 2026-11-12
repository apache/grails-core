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
package grails.doc.gradle

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternSet
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

/**
 * Converts the AsciiDoc documents of a directory to HTML pages.
 *
 * <p>Every document matching {@link #getIncludes()} and not {@link #getExcludes()} - by default
 * every {@code .adoc} file whose name does not start with an underscore - is converted on its
 * own, with its own directory as the base directory, to the same relative path under
 * {@link #getOutputDir()}. The {@code images} directory of the source directory is copied
 * alongside the pages.</p>
 *
 * <p>Documents that include files from outside the source directory name those files'
 * directories with {@link #getPathAttributes()} and declare the files with
 * {@link #getIncludedFiles()}, so a change to an included example converts the documents again.</p>
 *
 * <p>Like the Asciidoctor Gradle plugin, the task sets {@code revnumber} and the
 * {@code gradle-project-name}, {@code gradle-project-group} and {@code gradle-project-version}
 * attributes from the project, and {@code includedir} to the source directory, unless
 * {@link #getAttributes()} sets them.</p>
 *
 * <p>The conversion runs in a forked worker process, like {@link PublishGuideTask}, so the JRuby
 * runtime AsciidoctorJ starts never lands in the Gradle daemon.</p>
 *
 * @since 8.0
 */
@CompileStatic
@CacheableTask
abstract class ConvertAsciidocTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getSourceDir()

    @Input
    abstract ListProperty<String> getIncludes()

    @Input
    abstract ListProperty<String> getExcludes()

    /**
     * The document attributes. Values are converted to strings before the documents are converted.
     */
    @Input
    abstract MapProperty<String, Object> getAttributes()

    /**
     * The document attributes that name a file or directory, such as the root of the examples a document includes.
     * The documents get their absolute paths; the task's inputs only depend on their paths relative to
     * {@link #getRootDirectory()}, so the build cache can reuse the result on another machine.
     */
    @Internal
    abstract MapProperty<String, File> getPathAttributes()

    @Input
    Map<String, String> getRelativePathAttributes() {
        File root = rootDirectory.get().asFile
        pathAttributes.get().collectEntries { String name, File path ->
            [(name): root.toPath().relativize(path.toPath()).toString().replace(File.separatorChar, '/' as char)]
        } as Map<String, String>
    }

    /** The directory {@link #getPathAttributes()} are relative to in the task's inputs, the root project's by default. */
    @Internal
    abstract DirectoryProperty getRootDirectory()

    /** The files the documents include from outside the source directory. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getIncludedFiles()

    /** The attributes describing the project, which {@link #getAttributes()} can override. */
    @Input
    abstract MapProperty<String, String> getProjectAttributes()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    /** The JVM arguments of the worker process the documents are converted in. */
    @Internal
    abstract ListProperty<String> getJvmArgs()

    /** The maximum heap of the worker process the documents are converted in. */
    @Internal
    abstract Property<String> getMaxHeapSize()

    @Inject
    abstract WorkerExecutor getWorkerExecutor()

    @Inject
    abstract FileSystemOperations getFileSystemOperations()

    @Inject
    abstract ObjectFactory getObjects()

    ConvertAsciidocTask() {
        group = 'documentation'
        description = 'Converts the AsciiDoc documents to HTML.'
        includes.convention(['**/*.adoc'])
        excludes.convention(['**/_*'])
        attributes.convention([:])
        pathAttributes.convention([:])
        rootDirectory.convention(project.rootProject.layout.projectDirectory)
        String projectVersion = project.version.toString()
        projectAttributes.convention([
                'gradle-project-name'   : project.name,
                'gradle-project-group'  : project.group.toString(),
                'gradle-project-version': projectVersion,
        ] + (projectVersion == Project.DEFAULT_VERSION ? [:] : [revnumber: projectVersion]))
        // JRuby reaches into these packages when AsciidoctorJ starts it
        jvmArgs.convention(['--add-opens', 'java.base/sun.nio.ch=ALL-UNNAMED', '--add-opens', 'java.base/java.io=ALL-UNNAMED'])
    }

    @TaskAction
    void convert() {
        File source = sourceDir.get().asFile
        File output = outputDir.get().asFile

        fileSystemOperations.delete { it.delete(output) }
        fileSystemOperations.copy {
            it.from(new File(source, 'images'))
            it.into(new File(output, 'images'))
        }

        PatternSet patterns = new PatternSet().include(includes.get()).exclude(excludes.get())
        List<String> documents = []
        objects.fileTree().from(source).matching(patterns).visit { FileVisitDetails details ->
            if (!details.directory) {
                documents << details.relativePath.pathString
            }
        }
        Map<String, String> attributeValues = [includedir: source.absolutePath] + projectAttributes.get()
        attributes.get().each { String name, Object value ->
            attributeValues.put(name, value == null ? '' : value.toString())
        }
        pathAttributes.get().each { String name, File path ->
            attributeValues.put(name, path.absolutePath.replace(File.separatorChar, '/' as char))
        }
        List<String> workerJvmArgs = jvmArgs.get()
        String workerHeap = maxHeapSize.getOrNull()

        WorkQueue queue = workerExecutor.processIsolation { spec ->
            spec.forkOptions { options ->
                options.jvmArgs(workerJvmArgs)
                if (workerHeap) {
                    options.maxHeapSize = workerHeap
                }
            }
        }
        queue.submit(ConvertAsciidocWorkAction) { ConvertAsciidocWorkParameters params ->
            params.sourceDir.set(source)
            params.outputDir.set(output)
            params.documents.set(documents)
            params.attributes.set(attributeValues)
        }
    }
}
