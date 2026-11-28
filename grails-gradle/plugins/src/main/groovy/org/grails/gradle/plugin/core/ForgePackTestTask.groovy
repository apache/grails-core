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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Tests the Forge feature pack of a plugin with applications: Forge generates one for each fixture,
 * with the feature of the pack selected, and each is then built with its own Gradle wrapper.
 *
 * <p>Forge is started as {@link CheckForgePackTask} starts it, in a JVM of its own that has the class
 * path of the {@code grailsForgePackTooling} configuration. It is told the directory of the pack, the
 * directory to generate in and, when the build names fixtures, a file that lists them; without one it
 * generates an application for each type of application the pack is for. It writes a line for each
 * fixture, {@link #GENERATED_LINE} or {@link #REFUSED_LINE}, or the problems of a pack it cannot read,
 * and then the one line {@link #SUMMARY_LINE}. It ends with 0 when every fixture was generated, with 1
 * when one was refused or the pack has problems, and with anything else when it could not generate. An
 * end without the last line is not a run that got to its end.</p>
 *
 * <p>The repositories the generated builds resolve from are given to Forge in the environment variable
 * {@code GRAILS_REPO_URL}, as they are given to the commands that create an application. The first of
 * them, when the project has Maven publications, is a repository of this task that has them as this
 * build has just built them ({@link #getPublicationLayout()}): an application gets the plugin that is
 * tested, whatever another repository has of the same version, and a change of the plugin is a change
 * of what the task tests. A build of a
 * fixture uses the Gradle user home of this build, so what it downloads is downloaded once, and the
 * Java of the toolchain of the project. All of what it writes goes to a log of the fixture, and it
 * has {@link #getFixtureTimeout()} to be done in.</p>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'What is tested is that applications can be generated and built here')
abstract class ForgePackTestTask extends ForgePackToolTask {

    /** The class of Forge that generates the fixtures of a pack. */
    public static final String MAIN_CLASS = 'org.apache.grails.forge.core.pack.PackFixtures'

    /** What Forge writes for a fixture it generated: a pattern, with the name of the fixture. */
    public static final String GENERATED_LINE = /generated ([a-z][a-z0-9-]*)/

    /** What Forge writes for a fixture the generator refused: a pattern, with the name of the fixture and why. */
    public static final String REFUSED_LINE = /refused ([a-z][a-z0-9-]*): (.+)/

    /** What Forge writes for a problem of a pack it cannot read: a pattern, with the problem. */
    public static final String PACK_PROBLEM_LINE = /pack: (.+)/

    /** The last line Forge writes when it got to its end: a pattern, with the numbers of fixtures. */
    public static final String SUMMARY_LINE = /feature pack fixtures: generated=(\d+) refused=(\d+)/

    /** The log of the build of a fixture, in the directory of the fixture below the report directory. */
    public static final String LOG_NAME = 'build.log'

    /** What became of the fixtures, in the report directory. */
    public static final String REPORT_NAME = 'report.txt'

    /** What Forge ends with when it generated every fixture. */
    public static final int GENERATED = 0

    /** What Forge ends with when the generator refused a fixture or the pack has problems. */
    public static final int REFUSED = 1

    private static final int STOP_SECONDS = 30
    private static final int SECONDS_OF_MINUTE = 60
    // the user and the password of an address, with the at sign after them
    private static final Pattern USER_INFO = Pattern.compile('(?<=://)[^/\\s@]*@')
    private static final int MAX_OTHER_LINES = 3
    private static final int MAX_TAIL_BYTES = 64 * 1024
    private static final int MAX_TAIL_LINES = 30
    private static final int MAX_TAIL_LINE = 300
    private static final int MAX_FAILED_TASKS = 5
    private static final Pattern FAILED_TASK = Pattern.compile(/^> Task (:\S+) FAILED\s*$/)

    /**
     * The fixtures, each as the line Forge reads: a name, a type of application, and then options and
     * features. Without any, Forge generates an application for each type the pack is for.
     */
    @Input
    abstract ListProperty<String> getFixtures()

    /** The tasks that are run in each generated application. */
    @Input
    abstract ListProperty<String> getBuildTasks()

    /** Further arguments for the Gradle of each generated application. */
    @Input
    abstract ListProperty<String> getBuildArguments()

    /**
     * The repositories the generated applications resolve from before the ones every generated
     * application has, as entries of {@code GRAILS_REPO_URL}. An address can hold a password, so they
     * are no input of the task, which Gradle would keep as text in the history of the project:
     * {@link #getRepositoriesDigest()} is. This task does not show them. Forge writes them, as they
     * are, into the build files of the applications.
     */
    @Internal
    abstract ListProperty<String> getRepositories()

    /** A digest of {@link #getRepositories()}, so that other repositories are another test. */
    @Input
    abstract Property<String> getRepositoriesDigest()

    /**
     * What is said, each time the task runs, of the repositories of the build that the applications
     * do not get: each a line that names a repository without the user and the password it has.
     */
    @Internal
    abstract ListProperty<String> getRepositoriesLeftOut()

    /**
     * How long the build of one application can take. One that takes longer is stopped, with every
     * process it started, and is a fixture that could not be built.
     */
    @Input
    abstract Property<Duration> getFixtureTimeout()

    /** Whether the generated applications are built with {@code --offline}. */
    @Input
    abstract Property<Boolean> getOffline()

    @Option(option = 'offline-fixtures', description = 'Builds the generated applications with --offline.')
    void setOfflineFixtures(boolean offline) {
        this.offline.set(offline)
    }

    /** The Gradle user home the generated applications are built with: that of this build. */
    @Internal
    abstract Property<String> getGradleUserHome()

    /** Where the applications are generated, each in the directory of its fixture. */
    @Internal
    abstract DirectoryProperty getFixturesDirectory()

    /**
     * The files of the Maven publications of the project, by their paths in a Maven repository, see
     * {@link ForgePackPublications}. They are put into {@link #getPublicationRepository()}, which the
     * applications resolve from before the other repositories. Nothing when the project has no Maven
     * publication.
     */
    @Internal
    abstract MapProperty<String, String> getPublicationLayout()

    /** The paths of the files of the publications in the repository: other coordinates are another test. */
    @Input
    abstract ListProperty<String> getPublicationPaths()

    /** The files of the publications: a plugin that was built again with other content is another test. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    abstract ConfigurableFileCollection getPublicationFiles()

    /** The repository the publications of the project are put into, for the applications. */
    @Internal
    abstract DirectoryProperty getPublicationRepository()

    /** Where the log of each fixture is written, in the directory of the fixture, and what became of them all. */
    @OutputDirectory
    abstract DirectoryProperty getReportDirectory()

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations()

    @TaskAction
    void test() {
        File applications = fixturesDirectory.get().asFile
        File reports = reportDirectory.get().asFile
        File published = publicationRepository.get().asFile
        fileSystemOperations.delete { DeleteSpec spec -> spec.delete(applications, reports, published) }
        reports.mkdirs()
        requirePackWithoutLinks()
        requireTooling('tested', 'tests', 'test')
        repositoriesLeftOut.get().each { String line -> logger.warn(withoutUserInfo(line)) }
        if (fixtureTimeout.get() <= Duration.ZERO) {
            throw new GradleException("The feature pack in ${SOURCE} could not be tested: the time a fixture has to be built in is not a time")
        }
        if (buildTasks.get().isEmpty()) {
            throw new GradleException("The feature pack in ${SOURCE} could not be tested: there is no task to run in the applications")
        }
        List<String> names = generate(applications, stagePublications(published))
        List<String> report = []
        List<String> failures = []
        for (String name : names) {
            File log = new File(new File(reports, name), LOG_NAME)
            String failure = build(name, new File(applications, name), log)
            report << withoutUserInfo("${name}: ${failure == null ? 'built' : 'failed: ' + failure}")
            if (failure != null) {
                failures << "  - ${name}: ${failure}\n    the application is in ${new File(applications, name)}\n    the whole log is in ${log}\n".toString() +
                        (whatWentWrong(log) ?: tail(log)).collect { String line -> '      ' + line }.join('\n')
            }
        }
        new File(reports, REPORT_NAME).setText(report.join('\n') + '\n', StandardCharsets.UTF_8.name())
        if (failures) {
            throw new GradleException("The feature pack in ${SOURCE} was generated into ${names.size()} " +
                    "${names.size() == 1 ? 'application' : 'applications'}, of which ${failures.size()} could not be built with " +
                    "${buildTasks.get().join(' ')}:\n" + failures.join('\n'))
        }
        logger.lifecycle('The feature pack in {} was generated into {} and built with {}: {}. The logs are in {}',
                SOURCE, names.size() == 1 ? '1 application' : "${names.size()} applications", buildTasks.get().join(' '), names.join(', '), reports)
    }

    // The repository of the publications of the project, with each of their files as the build has just built
    // it, or null when there is none. A file a publication names that the build did not write, such as module
    // metadata the build was told not to write, is left out, as maven-publish leaves it out.
    private File stagePublications(File repository) {
        Map<String, String> layout = publicationLayout.get()
        List<String> staged = []
        layout.each { String path, String source ->
            File file = new File(source)
            if (file.isFile()) {
                File target = new File(repository, path)
                target.parentFile.mkdirs()
                Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                staged << path
            }
        }
        if (!staged) {
            return null
        }
        logger.info('The applications resolve from {} first, which has the publications of this build: {}', repository, staged.join(', '))
        return repository
    }

    // the names of the fixtures that were generated, which are all of them
    private List<String> generate(File applications, File publications) {
        List<String> arguments = [packDirectory.get().asFile.absolutePath, applications.absolutePath]
        if (!fixtures.get().isEmpty()) {
            File listed = new File(temporaryDir, 'fixtures.txt')
            listed.setText(fixtures.get().join('\n') + '\n', StandardCharsets.UTF_8.name())
            arguments << listed.absolutePath
        }
        // those of this task and no others: what the environment of the build has is not an input of it
        List<String> entries = (publications == null ? [] : [publications.absolutePath]) + repositories.get()
        Launched result = launch(MAIN_CLASS, arguments, [GRAILS_REPO_URL: entries ? entries.join(';') : null])
        List<String> written = result.written
        boolean ended = written && written.last() ==~ SUMMARY_LINE && written.count { String line -> line ==~ SUMMARY_LINE } == 1
        List<String> fixtureLines = ended ? written.take(written.size() - 1) : written
        List<String> generated = fixtureLines.findAll { String line -> line ==~ GENERATED_LINE }
        List<String> refused = fixtureLines.findAll { String line -> line ==~ REFUSED_LINE }
        List<String> problems = fixtureLines.findAll { String line -> line ==~ PACK_PROBLEM_LINE }
        List<String> others = fixtureLines - generated - refused - problems
        boolean counted = false
        if (ended) {
            Matcher summary = written.last() =~ SUMMARY_LINE
            counted = summary.matches() && summary.group(1) == String.valueOf(generated.size()) && summary.group(2) == String.valueOf(refused.size())
        }
        if (result.exitValue == GENERATED && counted && generated && !refused && !problems && !others) {
            result.said.each { String line -> logger.info(withoutUserInfo(line)) }
            logger.info('Forge: {}', written.last())
            return generated.collect { String line -> group(GENERATED_LINE, line, 1) }
        }
        if (result.exitValue == REFUSED && counted && !others && problems && !generated && !refused) {
            throw new GradleException("The feature pack in ${SOURCE} cannot be used:\n" +
                    problems.collect { String problem -> '  - ' + withoutUserInfo(group(PACK_PROBLEM_LINE, problem, 1)) }.join('\n'))
        }
        if (result.exitValue == REFUSED && counted && !others && !problems && refused) {
            throw new GradleException("The feature pack in ${SOURCE} could not be tested: of ${generated.size() + refused.size()} fixtures " +
                    "the generator refused ${refused.size()}, and none was built:\n" +
                    refused.collect { String line ->
                        "  - ${group(REFUSED_LINE, line, 1)}: ${withoutUserInfo(group(REFUSED_LINE, line, 2))}".toString()
                    }.join('\n'))
        }
        String why
        List<String> shown = result.said
        if (ended && (result.exitValue == GENERATED || result.exitValue == REFUSED)) {
            why = 'It said that it had generated the fixtures, and what else was written to the standard output is not what it writes ' +
                    "for them, so it cannot be told what was generated: ${some((others ?: fixtureLines).collect { String line -> quoted(line) }, MAX_OTHER_LINES)}"
        } else if (result.exitValue == GENERATED || result.exitValue == REFUSED) {
            why = launcherHint(result.errors, MAIN_CLASS) ?: 'It ended as a run that generated the fixtures or was refused some does, without saying ' +
                    "that it had got to its end: a template may have ended the JVM, or the Forge of the ${TOOLING} configuration is not the one " +
                    'this version of the Grails Gradle plugin expects'
            shown = written + result.said
        } else {
            why = launcherHint(result.errors, MAIN_CLASS)
        }
        throw new GradleException("The feature pack in ${SOURCE} could not be tested (Forge ended with ${result.exitValue})" +
                withoutUserInfo("${why ? '. ' + why : ''}${shown ? ':\n' + shown.collect { String line -> '  ' + line }.join('\n') : ''}"))
    }

    private static String group(String pattern, String line, int group) {
        Matcher matcher = Pattern.compile(pattern).matcher(line)
        return matcher.matches() ? matcher.group(group) : ''
    }

    /**
     * The command that builds a generated application: its wrapper, the tasks, and the arguments. Each
     * element is one argument of the process, whatever spaces it has. On Windows the wrapper is the
     * batch file itself and not {@code cmd /c} with a line made of it: the quoting of such a line does
     * not survive a wrapper and a Gradle user home that both have a space in their path.
     *
     * @param application the directory of the application
     * @param windows whether the wrapper is the batch file
     * @param tasks the tasks to run
     * @param gradleUserHome the Gradle user home to build with
     * @param offline whether to build with {@code --offline}
     * @param arguments further arguments
     */
    static List<String> wrapperCommand(File application, boolean windows, List<String> tasks, String gradleUserHome, boolean offline,
                                       List<String> arguments) {
        List<String> command = [new File(application, windows ? 'gradlew.bat' : 'gradlew').absolutePath]
        command.addAll(tasks)
        // the log is read by people and by this task, and the home is where this build has its dependencies
        command.addAll(['--console=plain', '--gradle-user-home', gradleUserHome])
        if (offline) {
            command << '--offline'
        }
        command.addAll(arguments)
        return command
    }

    // why the application could not be built, or null
    private String build(String name, File application, File log) {
        log.parentFile.mkdirs()
        List<String> command = wrapperCommand(application, Os.isFamily(Os.FAMILY_WINDOWS), buildTasks.get(), gradleUserHome.get(),
                offline.get(), buildArguments.get())
        File wrapper = new File(command[0])
        if (!wrapper.isFile()) {
            log.setText("The application has no ${wrapper.name}\n", StandardCharsets.UTF_8.name())
            return "the application was generated without ${wrapper.name}, the Gradle wrapper it is built with"
        }
        Duration limit = fixtureTimeout.get()
        logger.lifecycle('Building the fixture {} of the feature pack: {}', name, buildTasks.get().join(' '))
        // Straight into the file, with no pipe to this JVM: a pipe is open for as long as anything
        // the wrapper started has it, such as a daemon, and the task would wait for that
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(application)
                .redirectErrorStream(true)
                .redirectOutput(log)
        if (javaLauncher.isPresent()) {
            builder.environment().put('JAVA_HOME', javaLauncher.get().metadata.installationPath.asFile.absolutePath)
        }
        Process process
        try {
            process = builder.start()
        } catch (IOException e) {
            return "its ${wrapper.name} could not be started: ${clean(e.message ?: e.class.simpleName)}".toString()
        }
        // a build is not asked anything
        process.outputStream.close()
        boolean ended
        try {
            ended = process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)
        } catch (InterruptedException e) {
            stop(process)
            Thread.currentThread().interrupt()
            throw new GradleException("The build of the fixture ${name} was stopped with the build of the plugin", e)
        }
        if (!ended) {
            stop(process)
            return "the build was not done after ${shown(limit)} and was stopped, with everything it had started".toString()
        }
        if (process.exitValue() == 0) {
            return null
        }
        List<String> failed = failedTasks(log)
        return failed ? "${failed.size() == 1 ? 'task' : 'tasks'} ${failed.join(', ')} failed (Gradle ended with ${process.exitValue()})".toString() :
                "Gradle ended with ${process.exitValue()} and named no task that failed".toString()
    }

    // the wrapper and everything below it, the children first as they are now: none is left to run on
    private static void stop(Process process) {
        List<ProcessHandle> below = process.descendants().collect(Collectors.toList())
        process.destroyForcibly()
        below.each { ProcessHandle each -> each.destroyForcibly() }
        process.waitFor(STOP_SECONDS, TimeUnit.SECONDS)
    }

    private static String shown(Duration limit) {
        long seconds = limit.seconds
        return seconds % SECONDS_OF_MINUTE == 0 ? "${seconds.intdiv(SECONDS_OF_MINUTE)} ${seconds == SECONDS_OF_MINUTE ? 'minute' : 'minutes'}".toString() :
                "${seconds} ${seconds == 1 ? 'second' : 'seconds'}".toString()
    }

    /**
     * A line without the user and the password an address in it has: what this task shows and writes
     * down is not where a password is to be read.
     */
    static String withoutUserInfo(String line) {
        return USER_INFO.matcher(line).replaceAll('')
    }

    private static List<String> failedTasks(File log) {
        List<String> failed = []
        log.withReader(StandardCharsets.UTF_8.name()) { Reader reader ->
            String line
            while (failed.size() < MAX_FAILED_TASKS && (line = reader.readLine()) != null) {
                Matcher task = FAILED_TASK.matcher(line)
                if (task.matches()) {
                    failed << clean(task.group(1))
                }
            }
        }
        return failed
    }

    /**
     * What Gradle says went wrong, which with a stack trace asked for is far from the end of the log:
     * the lines from the first {@code * What went wrong:} to the advice or the stack trace after it,
     * in bounds. Nothing for a log without it.
     */
    private static List<String> whatWentWrong(File log) {
        List<String> said = []
        log.withReader(StandardCharsets.UTF_8.name()) { Reader reader ->
            String line
            boolean within = false
            while (said.size() < MAX_TAIL_LINES && (line = reader.readLine()) != null) {
                if (within && (line.startsWith('* Try:') || line.startsWith('* Exception is:'))) {
                    break
                }
                within = within || line.startsWith('* What went wrong:')
                if (within && !line.trim().isEmpty()) {
                    said << clean(line)
                }
            }
        }
        return said
    }

    // the end of a log, for a build that did not say what went wrong: in bounds, without control characters
    private static List<String> tail(File log) {
        byte[] bytes = new RandomAccessFile(log, 'r').withCloseable { RandomAccessFile file ->
            long length = file.length()
            byte[] end = new byte[(int) Math.min(length, (long) MAX_TAIL_BYTES)]
            file.seek(length - end.length)
            file.readFully(end)
            end
        }
        List<String> lines = new String(bytes, StandardCharsets.UTF_8).readLines()
                .collect { String line -> clean(line) }
                .findAll { String line -> !line.trim().isEmpty() }
        return lines.takeRight(MAX_TAIL_LINES)
    }

    private static String clean(String line) {
        String safe = withoutUserInfo(line.replaceAll(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]+/, ' ').stripTrailing())
        return safe.length() > MAX_TAIL_LINE ? safe.substring(0, safe.offsetByCodePoints(0, safe.codePointCount(0, MAX_TAIL_LINE))) + '...' : safe
    }
}
