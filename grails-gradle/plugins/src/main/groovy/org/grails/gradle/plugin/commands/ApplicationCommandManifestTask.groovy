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

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

/**
 * Writes the commands the application can run with {@code runCommand}, with their descriptions,
 * arguments and options, to a JSON file. Command line tools such as the Grails Forge CLI read the file
 * to offer those commands and complete them without loading the application's classpath themselves.
 *
 * <p>Runs on the classpath {@code runCommand} uses, so the file lists exactly the commands
 * {@code runCommand} can run. The task is up to date while that classpath is unchanged.</p>
 *
 * <p>The file also names, under {@code inputs}, the directories whose files decide the commands without
 * being on the classpath yet: those whose build files decide the build, those that hold the sources
 * of commands or of the build's logic, those of the {@code cli} source sets, where every source can be a
 * command, those of the main sources, where a file named like a command can add one, and the source files
 * of the listed commands found in the projects of the build. A tool reading the file runs the task again when one of their files changes.</p>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'Reading the command metadata is cheaper than restoring it from the build cache')
abstract class ApplicationCommandManifestTask extends JavaExec {

    /** The class in the {@code grails-core-cli} artifact that writes the file */
    public static final String MANIFEST_WRITER_CLASS = 'org.apache.grails.core.cli.ApplicationCommandManifestWriter'

    @Inject
    ApplicationCommandManifestTask() {
        mainClass.set(MANIFEST_WRITER_CLASS)
        buildDirectories.convention([])
        sourceDirectories.convention([])
        commandDirectories.convention([])
        commandFileDirectories.convention([])
        commandSourceRoots.convention([])
    }

    /**
     * @return the JSON file the commands are written to
     */
    @OutputFile
    abstract RegularFileProperty getManifestFile()

    /**
     * @return the directories whose build files, such as {@code build.gradle} and
     * {@code gradle.properties}, decide the build: those of every project of the build
     */
    @Input
    abstract ListProperty<String> getBuildDirectories()

    /**
     * @return the directories whose files, at any depth, decide the commands or the build: the sources of
     * the commands of every project, the build's {@code gradle} and {@code buildSrc} directories, and
     * every included build
     */
    @Input
    abstract ListProperty<String> getSourceDirectories()

    /**
     * @return the directories of the {@code cli} source set of every project of the build, which hold
     * commands and what they use, so a command added there, which registers itself, counts too
     */
    @Input
    abstract ListProperty<String> getCommandDirectories()

    /**
     * @return the directories of the main Java and Groovy sources of every project of the build, where a
     * command compiled from a new file, named after its class as a command is, registers itself; a tool
     * watches the names of those files, not what they hold
     */
    @Input
    abstract ListProperty<String> getCommandFileDirectories()

    /**
     * @return the source directories of every project of the build, where the source of a command the
     * manifest lists is looked for by its class, so a change to it counts too
     */
    @Input
    abstract ListProperty<String> getCommandSourceRoots()

    /**
     * The source files of the listed commands found in the projects of the build; a command of a plugin
     * from a repository has none.
     */
    private List<String> commandSources(Object commands) {
        List<String> found = []
        for (Object command : (commands instanceof List ? (List) commands : [])) {
            Object className = command instanceof Map ? ((Map) command).className : null
            if (!(className instanceof String)) {
                continue
            }
            // a nested class is in the file of its outermost class
            String path = ((String) className).replaceFirst(/\$.*/, '').replace('.', '/')
            for (String root : commandSourceRoots.get()) {
                for (String extension : ['.groovy', '.java', '.kt']) {
                    File source = new File(root, path + extension)
                    if (source.isFile()) {
                        found << source.absolutePath
                    }
                }
            }
        }
        found.unique().sort()
    }

    @Override
    @TaskAction
    void exec() {
        // the file is the task's output, so it is given to the writer here rather than as an
        // argument input, which would make the absolute path part of the task's inputs
        File file = manifestFile.get().asFile
        // written and completed next to the file, then put in its place at once, so a tool reading the
        // file meanwhile finds the whole manifest before or after, and a failure leaves the one before
        file.parentFile.mkdirs()
        File written = File.createTempFile('grails-command-manifest-', '.tmp', file.parentFile)
        try {
            args(written.absolutePath)
            super.exec()
            Map<String, Object> manifest = (Map<String, Object>) new JsonSlurper().parse(written, 'UTF-8')
            manifest.inputs = [buildDirectories: buildDirectories.get(), sourceDirectories: sourceDirectories.get(),
                               commandDirectories: commandDirectories.get(),
                               commandFileDirectories: commandFileDirectories.get(), sourceFiles: commandSources(manifest.commands)]
            written.setText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)), 'UTF-8')
            try {
                Files.move(written.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(written.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        finally {
            Files.deleteIfExists(written.toPath())
        }
    }
}
