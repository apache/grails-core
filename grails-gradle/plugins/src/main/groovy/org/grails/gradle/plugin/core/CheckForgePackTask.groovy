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
 * line, or with another line beside it, is not a pack that was checked: something ended the JVM before
 * Forge was done, or wrote where only the check is to write. The line keeps an accident from passing for
 * a check. It does not keep a template from writing the line itself and ending the JVM: the templates of
 * a pack are code of its author, who is the one running the check.</p>
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
    private static final int MAX_LISTED = 10
    private static final int MAX_OTHER_LINES = 3
    private static final int MAX_OTHER_LINE = 200
    private static final int FIRST_CLASS_FILE_VERSION = 44
    // the two lines the launcher of Java writes for a main class compiled for a later Java
    private static final Pattern OLDER_JAVA = Pattern.compile('(?m)^Error: LinkageError occurred while loading main class ' +
            Pattern.quote(MAIN_CLASS) + /\s*$\s+^\s*java\.lang\.UnsupportedClassVersionError: .*class file version (\d+)/)
    private static final Pattern NO_MAIN_CLASS = Pattern.compile('(?m)^Error: Could not find or load main class ' +
            Pattern.quote(MAIN_CLASS) + /\s*$/)
    private static final List<String> SLF4J_WITHOUT_PROVIDER = [
            'SLF4J(W): No SLF4J providers were found.',
            'SLF4J(W): Defaulting to no-operation (NOP) logger implementation',
            'SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.',
    ].asImmutable()
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

    /** Written when the pack has no problems, so that a pack that is as it was is not checked again. */
    @OutputFile
    abstract RegularFileProperty getMarkerFile()

    @Inject
    protected abstract ExecOperations getExecOperations()

    @TaskAction
    void check() {
        File marker = markerFile.get().asFile
        marker.delete()
        checkTooling(toolingProblems.getOrNull())
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
            // Forge is published without a logging implementation, which SLF4J says in three lines each time
            spec.systemProperty('slf4j.internal.verbosity', 'ERROR')
            spec.standardOutput = out
            spec.errorOutput = err
            spec.ignoreExitValue = true
        }
        List<String> written = lines(out)
        // of an SLF4J that does not know the property
        List<String> said = lines(err).findAll { String line -> !(line in SLF4J_WITHOUT_PROVIDER) }
        List<String> summaries = written.findAll { String line -> line ==~ CHECKED_LINE }
        if (result.exitValue == VALID && written.size() == 1 && summaries.size() == 1) {
            said.each { String line -> logger.info(line) }
            logger.info('Forge: {}', written[0])
            marker.setText("${packState.get()}\n", StandardCharsets.UTF_8.name())
            return
        }
        if (result.exitValue == PROBLEMS && written) {
            throw new GradleException("The feature pack in ${SOURCE} cannot be used:\n" +
                    written.collect { String problem -> '  - ' + problem }.join('\n'))
        }
        String why
        List<String> shown = said
        if (result.exitValue == VALID && summaries.size() == 1) {
            List<String> others = written - summaries
            why = 'It checked the pack and found no problem, and other lines were written to the standard output, which only the ' +
                    "check itself is to write, so it cannot be told that the check was not ended early: ${some(others.collect { String line -> quoted(line) }, MAX_OTHER_LINES)}"
        } else if (result.exitValue == VALID) {
            why = 'It ended as a check without problems does, without saying that it had checked the pack: a template may have ' +
                    "ended the JVM, or the Forge of the ${TOOLING} configuration is older than this version of the Grails Gradle plugin expects"
            shown = written + said
        } else {
            // with no output of its own, the JVM may not have got as far as Forge
            why = launcherHint(new String(err.toByteArray(), StandardCharsets.UTF_8))
            if (why == null && result.exitValue == PROBLEMS) {
                why = 'It ended as a check that found problems does, without naming one: a template may have ended the JVM'
            }
        }
        throw new GradleException("The feature pack in ${SOURCE} could not be checked (Forge ended with ${result.exitValue})" +
                "${why ? '. ' + why : ''}${shown ? ':\n' + shown.collect { String line -> '  ' + line }.join('\n') : ''}")
    }

    /**
     * Fails for a class path of Forge that is not whole, before anything is started with it.
     */
    private static void checkTooling(ForgeToolingProblems problems) {
        if (problems == null || problems.empty) {
            return
        }
        if (problems.notFound) {
            throw new GradleException("The feature pack in ${SOURCE} could not be checked: Forge, which checks it, was not found in the " +
                    "repositories of the build. The ${TOOLING} configuration could not resolve:\n" +
                    problems.notFound.take(MAX_LISTED).collect { String each -> '  - ' + each }.join('\n') + '\n' +
                    (problems.notFound.size() > MAX_LISTED ? "  and ${problems.notFound.size() - MAX_LISTED} more\n" : '') +
                    'A pack is checked with the Forge of the Grails version of the project. Add a repository that has it, or name ' +
                    "the Forge to check with: dependencies { ${TOOLING} '${GrailsPluginGradlePlugin.FORGE_PACK_TOOLING}:<version>' }")
        }
        if (problems.neededNotFound) {
            int count = problems.neededNotFound.size()
            throw new GradleException("The feature pack in ${SOURCE} could not be checked: Forge, which checks it, was found, and " +
                    "${count} ${count == 1 ? 'module it needs was' : 'modules it needs were'} not: ${some(problems.neededNotFound, MAX_LISTED)}. " +
                    'They are where Grails is: add the repositories that have Grails to the repositories of the build')
        }
        throw new GradleException("The feature pack in ${SOURCE} could not be checked: ${some(problems.notFetched, MAX_LISTED)} could not be " +
                "fetched from the repositories of the build, which have ${problems.notFetched.size() == 1 ? 'its module' : 'their modules'}. " +
                'Forge is not run with a part of what it needs')
    }

    private static String some(List<String> all, int most) {
        List<String> shown = all.take(most)
        return all.size() > shown.size() ? "${shown.join(', ')} and ${all.size() - shown.size()} more" : shown.join(', ')
    }

    private static String quoted(String line) {
        return "'${line.length() > MAX_OTHER_LINE ? line.substring(0, line.offsetByCodePoints(0, line.codePointCount(0, MAX_OTHER_LINE))) + '...' : line}'"
    }

    /**
     * What is known of why the JVM did not get to Forge, from what the launcher of Java itself says and
     * as it says it: at the start of a line. Text that only has those words in it, which a template can
     * write, is not it.
     */
    private String launcherHint(String errors) {
        Matcher version = OLDER_JAVA.matcher(errors)
        if (version.find()) {
            String used = javaLauncher.isPresent() ? "Java ${javaLauncher.get().metadata.languageVersion.asInt()}" : 'the Java of the build'
            return "This Forge needs Java ${Integer.parseInt(version.group(1)) - FIRST_CLASS_FILE_VERSION} or later, and the toolchain of the " +
                    "project runs it with ${used}"
        }
        if (NO_MAIN_CLASS.matcher(errors).find()) {
            return "What the ${TOOLING} configuration resolved has no ${MAIN_CLASS}: it is a Forge older than this version of the " +
                    'Grails Gradle plugin expects, such as an earlier snapshot a repository of the build still has, or it is not Forge'
        }
        return null
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
