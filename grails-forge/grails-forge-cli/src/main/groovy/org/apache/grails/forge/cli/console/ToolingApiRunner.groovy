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
package org.apache.grails.forge.cli.console

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import picocli.CommandLine

/**
 * Runs a build of a project through the Tooling API of Gradle, from the JVM of the command line: no
 * process is started for it, which is what a run of the Gradle wrapper costs each time, and in the
 * interactive console every build after the first is asked of a connection that is there already.
 *
 * <p><strong>Which Gradle.</strong> The one the Gradle wrapper of the project names, in
 * {@code gradle/wrapper/gradle-wrapper.properties} beside the wrapper script, and no other: the
 * distribution, where it is kept and what its checksum is are read from that file by the Tooling API
 * as the wrapper reads them. The wrapper is the one {@link ConsoleProject#gradleWrapper} finds, after
 * every check that is made of a wrapper that is run, and a project that has none is refused as it is
 * where the wrapper is run: the Tooling API is never left to choose a Gradle by itself, which it does
 * for a build without that file by taking the version it is of. The script and the jar of the wrapper
 * are not run.</p>
 *
 * <p><strong>Where the wrapper is run instead.</strong> The Tooling API looks for that file in one
 * place, the directory it takes for the root of the build. For a project whose wrapper is elsewhere,
 * which is one whose script is a link or stands above a project that has settings of its own, and for
 * arguments that the program {@code gradle} reads itself before there is a build, as {@code --no-daemon}
 * and {@code --gradle-user-home}, the build is handed to the {@link GradleRunner} this was given.</p>
 *
 * <p><strong>What the build is given.</strong> The arguments as the command put them together, tasks
 * and options alike, in their order. Of the system properties of this JVM none: a build has those that
 * are given to it with {@code -D}. The environment of this JVM, and its input unless nothing may wait
 * for what a user types. The JVM options of the build are no arguments here: they are those of the
 * Gradle daemon, which {@code org.gradle.jvmargs} sets.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ToolingApiRunner extends GradleBuild {

    private static final String PROPERTIES = 'gradle/wrapper/gradle-wrapper.properties'
    private static final String DISTRIBUTION = 'distributionUrl'
    private static final String SETTINGS = 'settings.gradle'
    private static final String BUILD_SOURCES = 'buildSrc'
    private static final int MAX_PROPERTIES = 64 * 1024
    private static final int STOP_SECONDS = 10
    // what the program gradle reads itself, before there is a build to give an argument to
    private static final List<String> OF_THE_PROGRAM = ['--daemon', '--no-daemon', '--status', '--stop', '--foreground', '--gui',
                                                        '-g', '--gradle-user-home', '-p', '--project-dir', '-b', '--build-file', '-c', '--settings-file']
    private static final List<String> OF_THE_PROGRAM_WITH_VALUE = ['--gradle-user-home=', '--project-dir=', '--build-file=', '--settings-file=',
                                                                   '-Dorg.gradle.', '-Dgradle.user.home=']

    private final Consumer<String> failure
    private final boolean windows
    private final GradleRunner wrapper

    /**
     * @param failure given the one line that says why the command failed, when it did
     * @param windows whether the project is built on Windows
     * @param wrapper what runs the wrapper of a project for which this cannot name its Gradle to the Tooling API
     */
    ToolingApiRunner(Consumer<String> failure, boolean windows, GradleRunner wrapper) {
        this.failure = failure
        this.windows = windows
        this.wrapper = wrapper
    }

    @Override
    int run(ConsoleProject project, List<String> arguments, Consumer<GradleBuild.Running> started) throws GradleRunner.RefusedArgumentException {
        ConsoleProject.Wrapper found = project.gradleWrapper(windows)
        if (found.file == null) {
            failure.accept("${found.refusal}. Gradle was not run, since the command runs the Gradle of the project and no other".toString())
            return 1
        }
        if (arguments.any { String argument -> isOfTheProgram(argument) } || !isWhereGradleLooks(project, found.file)) {
            return wrapper.run(project, arguments, started)
        }
        Path properties = found.file.getParent().resolve(PROPERTIES)
        if (!namesDistribution(properties)) {
            failure.accept("The Gradle wrapper ${GradleRunner.line(found.file)} names no Gradle: there is no ${PROPERTIES} beside it that has a ${DISTRIBUTION}. ".toString() +
                    'Gradle was not run, since the command runs the Gradle of the project and no other')
            return 1
        }
        ProjectConnection connection
        try {
            connection = GradleConnector.newConnector().forProjectDirectory(project.directory.toFile()).useBuildDistribution().connect()
        } catch (GradleConnectionException | IllegalStateException e) {
            failure.accept("Gradle could not be run for the project in ${GradleRunner.line(project.directory)}: ${reason(e)}".toString())
            return 1
        }
        try {
            return run(connection, arguments, started)
        } finally {
            close(connection)
        }
    }

    /**
     * @param argument an argument for Gradle
     * @return whether it is one the program {@code gradle} reads itself and a build is not given: one
     * that says which daemon runs the build or whether one does, where Gradle keeps what is its own,
     * or which build is meant
     */
    static boolean isOfTheProgram(String argument) {
        return argument in OF_THE_PROGRAM || OF_THE_PROGRAM_WITH_VALUE.any { String start -> argument.startsWith(start) }
    }

    // Whether the wrapper is where the Tooling API looks for what names the Gradle of a build: a script
    // that is a file, in the directory Gradle takes for the root of the build of the project, which is
    // the nearest one from the project upwards that has settings, and the project where none has.
    private static boolean isWhereGradleLooks(ConsoleProject project, Path wrapper) {
        if (Files.isSymbolicLink(wrapper)) {
            // the script of a link is run where its file is, and has its jar and its properties there
            return false
        }
        try {
            Path root = null
            Path directory = project.directory.toAbsolutePath().normalize()
            if (directory.getFileName()?.toString() == BUILD_SOURCES) {
                root = directory
            }
            for (Path each = directory; root == null && each != null; each = each.getParent()) {
                if (hasSettings(each)) {
                    root = each
                }
            }
            root = root ?: directory
            return Files.isSameFile(root, wrapper.getParent())
        } catch (IOException | RuntimeException ignored) {
            return false
        }
    }

    // by its name, in whatever language the settings are written: more is taken for settings here than Gradle does, never less
    private static boolean hasSettings(Path directory) throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, SETTINGS + '*')) {
            return entries.iterator().hasNext()
        }
    }

    private static boolean namesDistribution(Path properties) {
        try {
            if (!Files.isRegularFile(properties) || Files.size(properties) > MAX_PROPERTIES) {
                return false
            }
            Properties read = new Properties()
            Files.newInputStream(properties).withCloseable { InputStream stream -> read.load(stream) }
            String named = read.getProperty(DISTRIBUTION)
            return named != null && !named.trim().isEmpty()
        } catch (IOException | IllegalArgumentException ignored) {
            return false
        }
    }

    private int run(ProjectConnection connection, List<String> arguments, Consumer<GradleBuild.Running> started) {
        PrintStream out = System.out
        PrintStream err = System.err
        CancellationTokenSource cancellation = GradleConnector.newCancellationTokenSource()
        BuildLauncher launcher = connection.newBuild()
        // tasks and their options together, as on a command line: an option of a task is one only after its task
        launcher.withArguments(PlainOutput.forGradle(arguments) + arguments)
        launcher.withSystemProperties(Collections.<String, String>emptyMap())
        launcher.withCancellationToken(cancellation.token())
        launcher.setStandardOutput(out)
        launcher.setStandardError(err)
        // Gradle draws its colours and its progress when it is told that a terminal reads them, which it cannot see for itself here
        launcher.setColorOutput(isOutputOfProcess() && !PlainOutput.isOn() && CommandLine.Help.Ansi.AUTO.enabled())
        if (isNonInteractive()) {
            // Gradle and what it starts read the end of their input at once, and are told why
            launcher.setStandardInput(new ByteArrayInputStream(new byte[0]))
            Map<String, String> environment = new LinkedHashMap<String, String>(System.getenv())
            environment.put(NON_INTERACTIVE, 'true')
            launcher.setEnvironmentVariables(environment)
        } else {
            launcher.setStandardInput(System.in)
        }
        CountDownLatch ended = new CountDownLatch(1)
        AtomicReference<GradleConnectionException> failed = new AtomicReference<GradleConnectionException>()
        AtomicBoolean stopped = new AtomicBoolean()
        AtomicBoolean said = new AtomicBoolean()
        // said once and by whoever comes first: this JVM can end before the thread of the command gets to say it
        Runnable sayStopped = {
            if (said.compareAndSet(false, true)) {
                failure.accept(STOPPED)
            }
        } as Runnable
        Runnable stop = {
            stopped.set(true)
            cancellation.cancel()
        } as Runnable
        // when this JVM is ended while Gradle runs, the build is not left running without it: it is cancelled,
        // and given a while to end what it started. A daemon whose client is gone cancels the build as well.
        Thread whenEnded = new Thread({
            sayStopped.run()
            stop.run()
            giveTime(ended)
        } as Runnable, 'grails-gradle-stop')
        Runtime.getRuntime().addShutdownHook(whenEnded)
        waiting()
        try {
            out.flush()
            err.flush()
            launcher.run(new ResultHandler<Void>() {

                @Override
                void onComplete(Void result) {
                    ended.countDown()
                }

                @Override
                void onFailure(GradleConnectionException e) {
                    failed.set(e)
                    ended.countDown()
                }
            })
            if (started != null) {
                started.accept(new GradleBuild.Running(ProcessHandle.current(), true, stop))
            }
            ended.await()
            out.flush()
            err.flush()
            GradleConnectionException e = failed.get()
            if (e == null) {
                return 0
            }
            if (stopped.get() || e instanceof BuildCancelledException || Thread.currentThread().isInterrupted()) {
                sayStopped.run()
                return 1
            }
            failure.accept(e instanceof BuildException ? 'Gradle did not succeed: the build failed' : "Gradle could not run the build: ${reason(e)}".toString())
            return 1
        } catch (InterruptedException ignored) {
            sayStopped.run()
            stop.run()
            giveTime(ended)
            Thread.currentThread().interrupt()
            return 1
        } catch (IllegalStateException e) {
            failure.accept("Gradle could not run the build: ${reason(e)}".toString())
            return 1
        } finally {
            waited()
            try {
                Runtime.getRuntime().removeShutdownHook(whenEnded)
            } catch (IllegalStateException ignored) {
                // the JVM is shutting down, and the hook runs
            }
        }
    }

    // a build that was cancelled ends what it started, and is given this long for it
    private static void giveTime(CountDownLatch ended) {
        boolean interrupted = Thread.interrupted()
        try {
            ended.await(STOP_SECONDS, TimeUnit.SECONDS)
        } catch (InterruptedException ignored) {
            interrupted = true
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
        }
    }

    private static void close(ProjectConnection connection) {
        try {
            connection.close()
        } catch (RuntimeException ignored) {
            // nothing is asked of it again
        }
    }

    // what a failure that is not the failure of a build says: its own line and the one of what caused it
    private static String reason(Throwable e) {
        Throwable cause = e
        while (cause.getCause() != null && !cause.getCause().is(cause)) {
            cause = cause.getCause()
        }
        String first = firstLine(e.getMessage()) ?: e.getClass().getSimpleName()
        String last = cause.is(e) ? null : (firstLine(cause.getMessage()) ?: cause.getClass().getSimpleName())
        return GradleRunner.line(last == null || first.contains(last) ? first : "${first} ${last}")
    }

    private static String firstLine(String message) {
        if (message == null) {
            return null
        }
        List<String> lines = message.readLines().findAll { String line -> !line.trim().isEmpty() }
        return lines.isEmpty() ? null : lines.first().trim()
    }
}
