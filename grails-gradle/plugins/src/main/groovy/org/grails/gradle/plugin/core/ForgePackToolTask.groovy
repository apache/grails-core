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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec
import org.gradle.work.DisableCachingByDefault

/**
 * A task that has Forge do something with the Forge feature pack of a plugin: what the tasks that
 * check a pack and that test one have in common.
 *
 * <p>Forge runs in a JVM of its own, started with the class path of the {@code grailsForgePackTooling}
 * configuration and nothing else. It is not loaded into the build: Forge needs the Groovy and the GSP
 * compiler of the Grails it belongs to, which are not those of Gradle, and the configuration is left out
 * of what the Grails plugins do to the versions of every other configuration of the project. A class
 * path of Forge that the repositories of the build gave only a part of is not started.</p>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'Each task of a pack tool says for itself why its result is not kept')
abstract class ForgePackToolTask extends DefaultTask {

    protected static final String SOURCE = GrailsPluginGradlePlugin.FORGE_PACK_SOURCE_DIR
    protected static final String TOOLING = GrailsPluginGradlePlugin.FORGE_PACK_TOOLING_CONFIGURATION

    /** The most lines of the standard output of Forge that are read: what Forge writes for a pack has to fit. */
    public static final int MAX_LINES = 60

    private static final int MAX_LINE = 2000
    private static final int MAX_LISTED = 10
    private static final int MAX_OTHER_LINE = 200
    private static final int FIRST_CLASS_FILE_VERSION = 44
    private static final List<String> SLF4J_WITHOUT_PROVIDER = [
            'SLF4J(W): No SLF4J providers were found.',
            'SLF4J(W): Defaulting to no-operation (NOP) logger implementation',
            'SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.',
    ].asImmutable()

    /** The directory of the pack. What of it is an input is in {@link #getPackState()}. */
    @Internal
    abstract DirectoryProperty getPackDirectory()

    /** A digest of the pack as Forge would read it, see {@link ForgePackStateValueSource}. */
    @Input
    abstract Property<String> getPackState()

    /**
     * Forge and what it needs to run: the files of the tooling configuration that could be resolved.
     * What could not be is in {@link #getToolingProblems()}, and fails the task with words of its own
     * before Forge is started.
     */
    @Classpath
    abstract ConfigurableFileCollection getToolingClasspath()

    /** What of the tooling configuration could not be resolved or fetched. */
    @Internal
    abstract Property<ForgeToolingProblems> getToolingProblems()

    /** The Java that runs Forge: that of the toolchain of the project. */
    @Nested
    @Optional
    abstract Property<JavaLauncher> getJavaLauncher()

    @Inject
    protected abstract ExecOperations getExecOperations()

    /**
     * What a class of Forge that was started ended with and wrote.
     */
    protected static final class Launched {

        final int exitValue

        /** The lines of the standard output, see {@link ForgePackToolTask#lines} */
        final List<String> written

        /** The lines of the standard error, without those of an SLF4J that has no provider */
        final List<String> said

        /** The standard error as it was written */
        final String errors

        Launched(int exitValue, List<String> written, List<String> said, String errors) {
            this.exitValue = exitValue
            this.written = written
            this.said = said
            this.errors = errors
        }
    }

    /**
     * Fails for a pack whose directory is a link or that has a link under its templates, before Forge is
     * started: the jar of the plugin is not built of such a pack either, see {@link ForgePackLinks}.
     */
    protected void requirePackWithoutLinks() {
        ForgePackLinks.refuse(packDirectory.get().asFile)
    }

    /**
     * Fails for a class path of Forge that is not whole or has nothing, before anything is started with it.
     *
     * @param done what was to be done with the pack, as in "could not be checked"
     * @param does what Forge does with the pack, as in "Forge, which checks it"
     * @param todo the same as in "the Forge to check with"
     */
    protected void requireTooling(String done, String does, String todo) {
        ForgeToolingProblems problems = toolingProblems.getOrNull()
        if (problems != null && !problems.empty) {
            if (problems.notFound) {
                throw new GradleException("The feature pack in ${SOURCE} could not be ${done}: Forge, which ${does} it, was not found in the " +
                        "repositories of the build. The ${TOOLING} configuration could not resolve:\n" +
                        problems.notFound.take(MAX_LISTED).collect { String each -> '  - ' + each }.join('\n') + '\n' +
                        (problems.notFound.size() > MAX_LISTED ? "  and ${problems.notFound.size() - MAX_LISTED} more\n" : '') +
                        "A pack is ${done} with the Forge of the Grails version of the project. Add a repository that has it, or name " +
                        "the Forge to ${todo} with: dependencies { ${TOOLING} '${GrailsPluginGradlePlugin.FORGE_PACK_TOOLING}:<version>' }")
            }
            if (problems.neededNotFound) {
                int count = problems.neededNotFound.size()
                throw new GradleException("The feature pack in ${SOURCE} could not be ${done}: Forge, which ${does} it, was found, and " +
                        "${count} ${count == 1 ? 'module it needs was' : 'modules it needs were'} not: ${some(problems.neededNotFound, MAX_LISTED)}. " +
                        'They are where Grails is: add the repositories that have Grails to the repositories of the build')
            }
            throw new GradleException("The feature pack in ${SOURCE} could not be ${done}: ${some(problems.notFetched, MAX_LISTED)} could not be " +
                    "fetched from the repositories of the build, which have ${problems.notFetched.size() == 1 ? 'its module' : 'their modules'}. " +
                    'Forge is not run with a part of what it needs')
        }
        if (toolingClasspath.empty) {
            throw new GradleException("The feature pack in ${SOURCE} could not be ${done}: the ${TOOLING} configuration has nothing " +
                    "to ${todo} it with")
        }
    }

    /**
     * Starts a class of Forge in a JVM of the toolchain that has the tooling class path, and waits for it.
     *
     * @param mainClass the class to start
     * @param arguments its arguments
     * @param environment variables of the environment to set, or with a {@code null} value to take away
     */
    protected Launched launch(String mainClass, List<String> arguments, Map<String, String> environment = [:]) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        File workingDirectory = temporaryDir
        String java = javaLauncher.isPresent() ? javaLauncher.get().executablePath.asFile.absolutePath : null
        ExecResult result = execOperations.javaexec { JavaExecSpec spec ->
            if (java != null) {
                spec.executable = java
            }
            spec.classpath = toolingClasspath
            spec.mainClass.set(mainClass)
            spec.args(arguments)
            spec.workingDir = workingDirectory
            environment.each { String name, String value ->
                if (value == null) {
                    spec.environment.remove(name)
                } else {
                    spec.environment(name, value)
                }
            }
            // what Forge writes is read as UTF-8, whatever the platform writes its output in: both outputs,
            // since what it says of a pack, which can have any letters, is on the standard error
            spec.systemProperty('stdout.encoding', StandardCharsets.UTF_8.name())
            spec.systemProperty('sun.stdout.encoding', StandardCharsets.UTF_8.name())
            spec.systemProperty('stderr.encoding', StandardCharsets.UTF_8.name())
            spec.systemProperty('sun.stderr.encoding', StandardCharsets.UTF_8.name())
            spec.systemProperty('java.awt.headless', 'true')
            // Forge is published without a logging implementation, which SLF4J says in three lines each time
            spec.systemProperty('slf4j.internal.verbosity', 'ERROR')
            spec.standardOutput = out
            spec.errorOutput = err
            spec.ignoreExitValue = true
        }
        // of an SLF4J that does not know the property
        List<String> said = lines(err).findAll { String line -> !(line in SLF4J_WITHOUT_PROVIDER) }
        return new Launched(result.exitValue, lines(out), said, new String(err.toByteArray(), StandardCharsets.UTF_8))
    }

    /**
     * What is known of why the JVM did not get to Forge, from what the launcher of Java itself says and
     * as it says it: at the start of a line. Text that only has those words in it, which a template can
     * write, is not it.
     */
    protected String launcherHint(String errors, String mainClass) {
        // the two lines the launcher of Java writes for a main class compiled for a later Java
        Matcher version = Pattern.compile('(?m)^Error: LinkageError occurred while loading main class ' +
                Pattern.quote(mainClass) + /\s*$\s+^\s*java\.lang\.UnsupportedClassVersionError: .*class file version (\d+)/).matcher(errors)
        if (version.find()) {
            String used = javaLauncher.isPresent() ? "Java ${javaLauncher.get().metadata.languageVersion.asInt()}" : 'the Java of the build'
            return "This Forge needs Java ${Integer.parseInt(version.group(1)) - FIRST_CLASS_FILE_VERSION} or later, and the toolchain of the " +
                    "project runs it with ${used}"
        }
        if (Pattern.compile('(?m)^Error: Could not find or load main class ' + Pattern.quote(mainClass) + /\s*$/).matcher(errors).find()) {
            return "What the ${TOOLING} configuration resolved has no ${mainClass}: it is a Forge older than this version of the " +
                    'Grails Gradle plugin expects, such as an earlier snapshot a repository of the build still has, or it is not Forge'
        }
        return null
    }

    protected static String some(List<String> all, int most) {
        List<String> shown = all.take(most)
        return all.size() > shown.size() ? "${shown.join(', ')} and ${all.size() - shown.size()} more" : shown.join(', ')
    }

    protected static String quoted(String line) {
        return "'${line.length() > MAX_OTHER_LINE ? line.substring(0, line.offsetByCodePoints(0, line.codePointCount(0, MAX_OTHER_LINE))) + '...' : line}'"
    }

    // what another program wrote: a bounded number of lines of bounded length, without control characters
    protected static List<String> lines(ByteArrayOutputStream output) {
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
