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
import java.util.regex.Matcher
import java.util.regex.Pattern

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
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
 * configuration and nothing else. It is not loaded into the build: Forge needs the Groovy and the GSP
 * compiler of the Grails it belongs to, which are not those of Gradle, and the configuration is left out
 * of what the Grails plugins do to the versions of every other configuration of the project. The JVM is
 * told the directory of the pack and ends with 0 for a pack without problems, having written the one
 * line {@link #CHECKED_LINE} to its standard output; with 1 for a pack with problems, having written
 * them there, one on a line; and with anything else when it could not check. An end with 0 without that
 * line is not a pack that was checked: something ended the JVM before Forge was done.</p>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'A pack is checked faster than its result would be fetched')
abstract class CheckForgePackTask extends DefaultTask {

    /** The class of Forge that checks a pack. */
    public static final String MAIN_CLASS = 'org.apache.grails.forge.core.pack.PackCheck'

    /** What Forge writes, and nothing else, for a pack it checked and found no problem with: a pattern. */
    public static final String CHECKED_LINE = /feature pack checked: templates=\d+ applications=\d+/

    private static final int VALID = 0
    private static final int PROBLEMS = 1
    private static final int MAX_LINES = 60
    private static final int MAX_LINE = 2000
    private static final int MAX_UNRESOLVED = 10
    private static final int MAX_REASON_LINES = 8
    private static final int FIRST_CLASS_FILE_VERSION = 44
    private static final Pattern CLASS_FILE_VERSION = Pattern.compile(/class file version (\d+)/)
    private static final String SOURCE = GrailsPluginGradlePlugin.FORGE_PACK_SOURCE_DIR
    private static final String TOOLING = GrailsPluginGradlePlugin.FORGE_PACK_TOOLING_CONFIGURATION

    /** The directory of the pack. What of it is an input is in {@link #getPackState()}. */
    @Internal
    abstract DirectoryProperty getPackDirectory()

    /** A digest of the pack as Forge would read it, see {@link ForgePackStateValueSource}. */
    @Input
    abstract Property<String> getPackState()

    /**
     * Forge and what it needs to run: the files of the tooling configuration that could be resolved.
     * What could not be is in {@link #getToolingResolution()}, and fails the task with words of its own.
     */
    @Classpath
    abstract ConfigurableFileCollection getToolingClasspath()

    /** What the tooling configuration resolved to, for what of it could not be resolved. */
    @Internal
    abstract Property<ResolvedComponentResult> getToolingResolution()

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
        File marker = markerFile.get().asFile
        marker.delete()
        List<String> unresolved = unresolved(toolingResolution.getOrNull())
        if (unresolved) {
            throw new GradleException("The feature pack in ${SOURCE} could not be checked: Forge, which checks it, was not found in the " +
                    "repositories of the build. The ${TOOLING} configuration could not resolve:\n" +
                    unresolved.collect { String each -> '  - ' + each }.join('\n') + '\n' +
                    'A pack is checked with the Forge of the Grails version of the project. Add a repository that has it, or name ' +
                    "the Forge to check with: dependencies { ${TOOLING} '${GrailsPluginGradlePlugin.FORGE_PACK_TOOLING}:<version>' }")
        }
        if (toolingClasspath.empty) {
            throw new GradleException("The feature pack in ${SOURCE} could not be checked: the ${TOOLING} configuration has nothing " +
                    'to check it with')
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        File directory = packDirectory.get().asFile
        File workingDirectory = temporaryDir
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
        List<String> written = lines(out)
        List<String> said = lines(err)
        if (result.exitValue == VALID && written.size() == 1 && written[0] ==~ CHECKED_LINE) {
            said.each { String line -> logger.info(line) }
            logger.info('Forge: {}', written[0])
            marker.setText("${packState.get()}\n", StandardCharsets.UTF_8.name())
            return
        }
        if (result.exitValue == PROBLEMS && written) {
            throw new GradleException("The feature pack in ${SOURCE} cannot be used:\n" +
                    written.collect { String problem -> '  - ' + problem }.join('\n'))
        }
        String why = hint(result.exitValue, new String(err.toByteArray(), StandardCharsets.UTF_8))
        List<String> shown = result.exitValue == VALID ? written + said : said
        throw new GradleException("The feature pack in ${SOURCE} could not be checked (Forge ended with ${result.exitValue})" +
                "${why ? '. ' + why : ''}${shown ? ':\n' + shown.collect { String line -> '  ' + line }.join('\n') : ''}")
    }

    /**
     * What is known of why Forge did not check, where its own words would not tell the author of a
     * plugin what to do.
     */
    private String hint(int exit, String errors) {
        if (errors.contains('UnsupportedClassVersionError')) {
            Matcher version = CLASS_FILE_VERSION.matcher(errors)
            String needed = version.find() ? "Java ${Integer.parseInt(version.group(1)) - FIRST_CLASS_FILE_VERSION} or later" : 'a later Java'
            String used = javaLauncher.isPresent() ? "Java ${javaLauncher.get().metadata.languageVersion.asInt()}" : 'the Java of the build'
            return "This Forge needs ${needed}, and the toolchain of the project runs it with ${used}"
        }
        if (errors.contains('Could not find or load main class') || errors.contains("ClassNotFoundException: ${MAIN_CLASS}")) {
            return "What the ${TOOLING} configuration resolved has no ${MAIN_CLASS}: it is a Forge older than this version of the " +
                    'Grails Gradle plugin expects, such as an earlier snapshot a repository of the build still has, or it is not Forge'
        }
        if (exit == VALID) {
            return 'It ended as a check without problems does, without saying that it had checked the pack: a template may have ' +
                    "ended the JVM, or the Forge of the ${TOOLING} configuration is older than this version of the Grails Gradle plugin expects"
        }
        return null
    }

    /**
     * What of the tooling could not be resolved, each with the first lines of why.
     */
    private static List<String> unresolved(ResolvedComponentResult root) {
        List<String> unresolved = []
        if (root == null) {
            return unresolved
        }
        Set<ResolvedComponentResult> seen = new HashSet<>()
        Deque<ResolvedComponentResult> pending = new ArrayDeque<>()
        pending.add(root)
        while (!pending.isEmpty() && unresolved.size() < MAX_UNRESOLVED) {
            ResolvedComponentResult component = pending.poll()
            if (!seen.add(component)) {
                continue
            }
            for (DependencyResult dependency : component.dependencies) {
                if (dependency instanceof UnresolvedDependencyResult) {
                    UnresolvedDependencyResult missing = (UnresolvedDependencyResult) dependency
                    // with where it was looked for, which Gradle says on the lines after the first
                    List<String> why = (missing.failure?.message ?: '').readLines()
                            .collect { String line -> line.replaceAll(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]+/, ' ').trim() }
                            .findAll { String line -> !line.isEmpty() }.take(MAX_REASON_LINES)
                    String each = "${missing.attempted.displayName}${why ? ': ' + why.join('\n      ') : ''}".toString()
                    if (!unresolved.contains(each)) {
                        unresolved << each
                    }
                } else if (dependency instanceof ResolvedDependencyResult) {
                    pending.add(((ResolvedDependencyResult) dependency).selected)
                }
            }
        }
        return unresolved
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
