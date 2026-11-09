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
package org.apache.grails.gradle.plugin.aot

import javax.inject.Inject

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations

/**
 * Unpacks an executable jar into the form an AOT cache is read against.
 *
 * <p>An executable jar loads its dependencies through a nested-jar class loader; the extracted form
 * loads them as ordinary jars on the classpath. A cache records the class path it saw, so only the
 * second is a layout a later run can reuse one against.</p>
 *
 * <p>The work is done through injected services rather than through the project, so that nothing is
 * reached at execution time that the configuration cache forbids.</p>
 *
 * @since 8.0
 */
@CacheableTask
@CompileStatic
abstract class ExtractApplicationTask extends DefaultTask {

    /** The archive to unpack, which has to be the one the cache will be trained against. */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getArchiveFile()

    /**
     * The Java runtime to unpack with. Its {@code jarmode} does the extracting, so it is the JDK the
     * application was built for rather than whichever one is running Gradle.
     *
     * <p>Nested rather than the path as an input. A path is where a JDK happens to live on the
     * machine that ran the build, so declaring it as an input puts that machine into this task's
     * cache key -- and a cacheable task that can only ever be hit on the machine that filled it is
     * not one. Nested fingerprints what the launcher says about itself, which is the part that
     * changes the result.</p>
     */
    @Nested
    abstract Property<JavaLauncher> getJavaLauncher()

    @OutputDirectory
    abstract DirectoryProperty getDestination()

    @Inject
    abstract FileSystemOperations getFileSystemOperations()

    @Inject
    abstract ExecOperations getExecOperations()

    @TaskAction
    void extract() {
        File target = destination.get().asFile
        // Emptied rather than deleted: it is this task's declared output, and what was left by a
        // previous archive would otherwise be read as part of this one.
        fileSystemOperations.delete { spec -> spec.delete(target) }
        target.mkdirs()
        execOperations.exec { spec ->
            spec.commandLine(javaLauncher.get().executablePath.asFile.absolutePath,
                    '-Djarmode=tools', '-jar',
                    archiveFile.get().asFile.absolutePath, 'extract',
                    '--destination', target.absolutePath)
        }
    }
}
