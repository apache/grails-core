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

import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.Diagnostics
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler

/**
 * Runs a build of a project through the Tooling API of Gradle, from the JVM of the command line: no
 * process is started for it, which is what a run of the Gradle wrapper costs each time, and in the
 * interactive console every build after the first is asked of a connection that is there already.
 * It is what runs the builds of the interactive console, and those of a command line that runs one
 * command only where the environment asks for it: see {@link GradleBuild#of}.
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
 * <p><strong>The connection.</strong> A command line that runs one command makes a connection for its
 * build and closes it. The interactive console keeps one, for the project it is in: every build after
 * the first is asked of it, which spares what making one costs each time, and it is closed when the
 * console is left. It is replaced when a command is run for another project, and when the file that
 * names the Gradle of the project has changed, as after {@code update-wrapper}. The checks of the
 * wrapper are made before every build all the same, and a build that failed or was cancelled leaves
 * the connection as it was.</p>
 *
 * <p><strong>What the build is given.</strong> The arguments as the command put them together, tasks
 * and options alike, in their order. Of the system properties of this JVM none: a build has those that
 * are given to it with {@code -D}. The environment of this JVM. The JVM options of the build are no
 * arguments here: they are those of the Gradle daemon, which {@code org.gradle.jvmargs} sets.</p>
 *
 * <p><strong>What the build writes.</strong> Plain text, to the output and the error output of the
 * command line as they are when the build starts, each line when Gradle has written it: no colours and
 * no display of progress, on a terminal too. The Tooling API can be told to write for a terminal, and
 * then draws the progress of the build over what it wrote to the error output, which it does not
 * write through the same console; the report of a build that failed loses its last lines that way.</p>
 *
 * <p><strong>What the build reads.</strong> The input of this JVM, where the command line is a program
 * that runs one command and ends, and nothing says that no one may be waited for. No input in the
 * interactive console, where the command line is run in the JVM of a caller, and with
 * {@code --non-interactive}: the Tooling API reads the stream it is given as soon as a build starts,
 * whether or not the build reads, and the thread that does goes on waiting for what is typed after the
 * build has ended, so it would take the next line from the prompt of the console, or from the caller.
 * The build is given no stream rather than one that has ended: Gradle takes a build that is given a
 * stream for one a user types into, and ends a continuous build of that kind when the stream ends,
 * after its first build. A build that is given none reads the end of its input at once all the same.
 * A build that is a session at the terminal, {@code shell}, is for that reason not run here at all, see
 * {@link GradleBuild#of}.</p>
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

    private static final Object KEPT = new Object()
    private static ProjectConnection kept
    private static String keptFor
    private static int connectionsMade

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
        String ofTheProgram = arguments.find { String argument -> isOfTheProgram(argument) }
        if (ofTheProgram != null || !isWhereGradleLooks(project, found.file)) {
            if (Diagnostics.isOn()) {
                // the name of the argument, and not its value, which can be anything
                String named = ofTheProgram == null ? null : ofTheProgram.split('=', 2)[0]
                Diagnostics.say('gradle runner', "${TOOLING_API} falls back to ${PROCESS}: ".toString() + (named != null ?
                        "the argument ${named} is one the program gradle reads itself, which the Tooling API does not take".toString() :
                        'the Gradle wrapper is a link, or stands above a project that has settings of its own, and the Tooling API does not look for the Gradle of the build there'))
            }
            return wrapper.run(project, arguments, started)
        }
        Path properties = found.file.getParent().resolve(PROPERTIES)
        if (!namesDistribution(properties)) {
            failure.accept("The Gradle wrapper ${GradleRunner.line(found.file)} names no Gradle: there is no ${PROPERTIES} beside it that has a ${DISTRIBUTION}. ".toString() +
                    'Gradle was not run, since the command runs the Gradle of the project and no other')
            return 1
        }
        List<String> checked
        try {
            checked = project.pathCheck()
        } catch (IOException e) {
            failure.accept(ProjectPathCheck.notRun(project, e))
            return 1
        }
        ProjectConnection connection
        try {
            connection = connect(project, properties)
        } catch (GradleConnectionException | IllegalStateException | IOException e) {
            failure.accept("Gradle could not be run for the project in ${GradleRunner.line(project.directory)}: ${reason(e)}".toString())
            return 1
        }
        try {
            return run(connection, arguments + checked, started)
        } finally {
            if (!isKept(connection)) {
                close(connection)
            }
        }
    }

    /**
     * Closes the connection the interactive console kept, when there is one. The console does it when it is left.
     */
    static void closeConnections() {
        ProjectConnection closed
        synchronized (KEPT) {
            closed = kept
            kept = null
            keptFor = null
        }
        if (closed != null) {
            close(closed)
        }
    }

    /**
     * @return how many connections were made in this JVM, for a specification
     */
    static int getConnectionsMade() {
        synchronized (KEPT) {
            return connectionsMade
        }
    }

    /**
     * @return whether a connection is kept for the next build, for a specification
     */
    static boolean isConnectionKept() {
        synchronized (KEPT) {
            return kept != null
        }
    }

    // The connection for a build: in the interactive console the one that is kept, when it is for this
    // project and for what names its Gradle now, and otherwise a new one, which is kept in its place there.
    private static ProjectConnection connect(ConsoleProject project, Path properties) throws IOException {
        if (!ConsoleCommands.isInteractive()) {
            ProjectConnection made = GradleConnector.newConnector().forProjectDirectory(project.directory.toFile()).useBuildDistribution().connect()
            int count
            synchronized (KEPT) {
                count = ++connectionsMade
            }
            if (Diagnostics.isOn()) {
                connection("made for this build, and closed after it (${count} made in this JVM)".toString())
            }
            return made
        }
        String wanted = "${project.directory.toRealPath()}\n${new String(Files.readAllBytes(properties), StandardCharsets.ISO_8859_1)}".toString()
        ProjectConnection replaced = null
        try {
            synchronized (KEPT) {
                if (kept != null && keptFor == wanted) {
                    if (Diagnostics.isOn()) {
                        connection("reused: the one the console keeps for this project and its Gradle (${connectionsMade} made in this JVM)".toString())
                    }
                    return kept
                }
                replaced = kept
                kept = null
                keptFor = null
                kept = GradleConnector.newConnector().forProjectDirectory(project.directory.toFile()).useBuildDistribution().connect()
                keptFor = wanted
                connectionsMade++
                if (Diagnostics.isOn()) {
                    connection((replaced == null ? 'made, and kept for the next build of the console' :
                            'made in the place of the one the console kept, which was for another project or another Gradle') +
                            " (${connectionsMade} made in this JVM)".toString())
                }
                return kept
            }
        } finally {
            if (replaced != null) {
                close(replaced)
            }
        }
    }

    private static void connection(String said) {
        Diagnostics.say('gradle connection', said)
    }

    private static boolean isKept(ProjectConnection connection) {
        synchronized (KEPT) {
            return connection.is(kept)
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
        // Gradle writes the two in the order they are to be read, and they are one terminal or one file as a rule:
        // what is written to one is not kept back while the other goes on
        launcher.setStandardOutput(new WrittenAtOnce(out))
        launcher.setStandardError(new WrittenAtOnce(err))
        // Plain text, on a terminal too. Told that a terminal reads its output, Gradle draws its progress below what it
        // writes and moves the cursor up to draw it again, as if nothing else wrote there: but it writes the standard
        // error beside that, not through it, so a line of an error, the report of a failed build among them, is
        // drawn over. Given a terminal by the wrapper, Gradle writes both through the one console; here it cannot.
        launcher.setColorOutput(false)
        // Where the build may not read the input of the command line it is given none, and not an input that has
        // ended: Gradle takes a build that is given an input for one a user types into, and a continuous build of that
        // kind ends when its input does, after its first build. Given none, Gradle and what it starts read the end of
        // their input at once all the same.
        if (isNonInteractive()) {
            // and are told why
            Map<String, String> environment = new LinkedHashMap<String, String>(System.getenv())
            environment.put(NON_INTERACTIVE, 'true')
            launcher.setEnvironmentVariables(environment)
        } else if (!ConsoleCommands.isInteractive() && isProgram()) {
            // The input of a program that runs one command. That of the console, or of a caller, is not given: whatever
            // reads it for a build would still read it when the build has ended.
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

    /** Gives a stream what it is given and has it written before anything else is: the output of the JVM keeps what it is given for a while. */
    private static final class WrittenAtOnce extends OutputStream {

        private final OutputStream to

        WrittenAtOnce(OutputStream to) {
            this.to = to
        }

        @Override
        void write(int b) throws IOException {
            to.write(b)
            to.flush()
        }

        @Override
        void write(byte[] bytes, int offset, int length) throws IOException {
            to.write(bytes, offset, length)
            to.flush()
        }

        @Override
        void flush() throws IOException {
            to.flush()
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
