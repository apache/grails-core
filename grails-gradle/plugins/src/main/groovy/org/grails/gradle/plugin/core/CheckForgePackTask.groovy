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
package org.grails.gradle.plugin.core

import java.nio.charset.StandardCharsets

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec
import org.gradle.work.DisableCachingByDefault

/**
 * Checks the Forge feature pack of a plugin with Forge itself: the whole descriptor, every file it
 * names, and every template, which is compiled and rendered for applications that are made up.
 *
 * <p>Forge runs in a JVM of its own, started with the class path of the {@code grailsForgePackTooling}
 * configuration and nothing else. It is not loaded into the build: Forge brings the Groovy and the GSP
 * compiler of the Grails it belongs to, which are not those of Gradle. The JVM is told the directory of
 * the pack; it writes the problems of the pack to its standard output, one on a line, and ends with
 * 0 for a pack without problems, 1 for one with problems and anything else when it could not check.</p>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'A pack is checked faster than its result would be fetched')
abstract class CheckForgePackTask extends DefaultTask {

    /** The class of Forge that checks a pack. */
    public static final String MAIN_CLASS = 'org.apache.grails.forge.core.pack.PackCheck'

    private static final int VALID = 0
    private static final int PROBLEMS = 1
    private static final int MAX_LINES = 60
    private static final int MAX_LINE = 2000

    /** The directory of the pack. What of it is an input is in {@link #getPackState()}. */
    @Internal
    abstract DirectoryProperty getPackDirectory()

    /** A digest of the pack as Forge would read it, see {@link ForgePackStateValueSource}. */
    @Input
    abstract Property<String> getPackState()

    /** Forge and what it needs to run. */
    @Classpath
    abstract ConfigurableFileCollection getToolingClasspath()

    /** The Java that runs Forge: that of the toolchain of the project. */
    @Nested
    @Optional
    abstract Property<JavaLauncher> getJavaLauncher()

    /** Written when the pack has no problems, so that a pack that is as it was is not checked again. */
    @OutputFile
    abstract RegularFileProperty getMarkerFile()

    @Inject
    protected abstract ExecOperations getExecOperations()

    @TaskAction
    void check() {
        if (toolingClasspath.empty) {
            throw new GradleException("The feature pack in ${GrailsPluginGradlePlugin.FORGE_PACK_SOURCE_DIR} cannot be checked: the " +
                    "${GrailsPluginGradlePlugin.FORGE_PACK_TOOLING_CONFIGURATION} configuration has nothing to check it with")
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        File directory = packDirectory.get().asFile
        File workingDirectory = temporaryDir
        File marker = markerFile.get().asFile
        marker.delete()
        String java = javaLauncher.isPresent() ? javaLauncher.get().executablePath.asFile.absolutePath : null
        ExecResult result = execOperations.javaexec { JavaExecSpec spec ->
            if (java != null) {
                spec.executable = java
            }
            spec.classpath = toolingClasspath
            spec.mainClass.set(MAIN_CLASS)
            spec.args(directory.absolutePath)
            spec.workingDir = workingDirectory
            // the problems are read as UTF-8, whatever the platform writes its output in
            spec.systemProperty('stdout.encoding', StandardCharsets.UTF_8.name())
            spec.systemProperty('sun.stdout.encoding', StandardCharsets.UTF_8.name())
            spec.systemProperty('java.awt.headless', 'true')
            spec.standardOutput = out
            spec.errorOutput = err
            spec.ignoreExitValue = true
        }
        List<String> problems = lines(out)
        List<String> said = lines(err)
        if (result.exitValue == VALID) {
            said.each { String line -> logger.info(line) }
            marker.setText("${packState.get()}\n", StandardCharsets.UTF_8.name())
            return
        }
        if (result.exitValue == PROBLEMS && problems) {
            throw new GradleException("The feature pack in ${GrailsPluginGradlePlugin.FORGE_PACK_SOURCE_DIR} cannot be used:\n" +
                    problems.collect { String problem -> '  - ' + problem }.join('\n'))
        }
        throw new GradleException("The feature pack in ${GrailsPluginGradlePlugin.FORGE_PACK_SOURCE_DIR} could not be checked " +
                "(Forge ended with ${result.exitValue})${said ? ':\n' + said.collect { String line -> '  ' + line }.join('\n') : ''}")
    }

    // what another program wrote: a bounded number of lines of bounded length, without control characters
    private static List<String> lines(ByteArrayOutputStream output) {
        List<String> all = new String(output.toByteArray(), StandardCharsets.UTF_8).readLines()
                .collect { String line -> line.replaceAll(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]+/, ' ').trim() }
                .findAll { String line -> !line.isEmpty() }
        List<String> shown = all.take(MAX_LINES).collect { String line ->
            line.length() > MAX_LINE ? line.substring(0, line.offsetByCodePoints(0, line.codePointCount(0, MAX_LINE))) + '...' : line
        }
        if (all.size() > shown.size()) {
            shown << "and ${all.size() - shown.size()} more lines".toString()
        }
        return shown
    }
}
