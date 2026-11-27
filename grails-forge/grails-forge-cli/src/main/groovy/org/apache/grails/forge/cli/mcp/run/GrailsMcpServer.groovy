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
package org.apache.grails.forge.cli.mcp.run

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.IntConsumer
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.CommandStreams
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.ToolCommand
import org.apache.grails.forge.cli.mcp.Lane
import org.apache.grails.forge.cli.mcp.McpCallContext
import org.apache.grails.forge.cli.mcp.McpException
import org.apache.grails.forge.cli.mcp.McpHandler
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.McpTool
import org.apache.grails.forge.cli.mcp.McpToolSet
import org.apache.grails.forge.cli.mcp.protocol.CallThreads
import org.apache.grails.forge.cli.mcp.tools.McpResources
import org.apache.grails.forge.cli.mcp.tools.ToolProjection
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import org.grails.forge.util.VersionInfo

/**
 * What {@code grails mcp} serves: the commands of the command line of its directory, and the tools of the
 * application and its plugins, as tools of the protocol, each call of which is one command line that the
 * server runs in its own process, as the interactive console runs a line.
 *
 * <ul>
 * <li><strong>The tools</strong> are made from {@code help --json}, run in this process, and made anew
 * when the directory changes ({@link ChangeWatcher}).</li>
 * <li><strong>A call</strong> waits for its lane ({@link CallLanes}), and runs
 * {@code grails --plain-output --non-interactive} and the command line of the call with
 * {@link ForgeCli#execute(ForgeContext, CommandStreams, String[])}, which writes to streams of the call's
 * own. Its result is made by {@link CommandOutput} from the exit code and what was written, and the whole
 * output is kept in the project ({@link OutputLog}). While it runs, the client is told how it goes: that it
 * runs, then every so often how many lines it wrote and the last of them, and while it waits for its lane,
 * which tool holds the lane.</li>
 * <li><strong>Cancelling a call</strong> is interrupting it. A call of the build lane runs on the thread
 * the client's cancel interrupts, so that Gradle and the tools are stopped. A call of the other two lanes,
 * which write files and are short, runs on a thread of its own that nothing interrupts: the call stops
 * waiting for it, and what it gives is dropped, but it is not stopped halfway through writing a file. Its
 * lane is free when it has ended.</li>
 * <li><strong>A call that does not end</strong> {@linkplain Settings#abandonAfter long} after it was
 * cancelled is given up on: one line on the standard error of the process says so, its lane takes other
 * calls again, and after a build the next build gets a new connection to Gradle. After
 * {@linkplain Settings#abandonments a few} such calls the server ends with the exit code 1, and the
 * client starts it again.</li>
 * <li><strong>The resources</strong> are those of {@link McpResources}, and the whole output of each call
 * of the project, read by the URI its result links.</li>
 * </ul>
 *
 * @since 9.0
 */
@CompileStatic
final class GrailsMcpServer implements McpHandler, Closeable {

    /** Where Grails is on the web, which the server says it is. */
    public static final String WEBSITE = 'https://grails.apache.org'

    /** The most characters of the instructions; Claude Code cuts them at 4,096. */
    public static final int MAX_INSTRUCTIONS = 1500

    /** The most characters of the last line a message of progress has. */
    public static final int MAX_PROGRESS_LINE = 200

    private static final String HELP = 'help'

    private final ForgeContext context
    private final Path directory
    private final boolean readOnly
    private final Settings settings
    private final PrintStream diagnostics
    private final CallLanes lanes
    private final OutputLog logs
    private final ChangeWatcher watcher
    private final ExecutorService commands
    private final ScheduledExecutorService timers
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>()
    private final String instructions
    private final AtomicInteger abandoned = new AtomicInteger()
    private final AtomicBoolean ending = new AtomicBoolean()
    private volatile CallThreads calls
    private volatile IntConsumer exit

    /**
     * @param context the application the commands run on, which starts the project generator when a command needs it
     * @param directory the directory the server serves, which is the current directory of the process
     * @param readOnly whether only the tools that change nothing are offered
     * @param settings how long the server waits for what
     * @param diagnostics the standard error of the process, where what the server says of itself goes
     */
    GrailsMcpServer(ForgeContext context, Path directory, boolean readOnly, Settings settings, PrintStream diagnostics) {
        this.context = Objects.requireNonNull(context, 'context')
        this.directory = Objects.requireNonNull(directory, 'directory')
        this.readOnly = readOnly
        this.settings = Objects.requireNonNull(settings, 'settings')
        this.diagnostics = Objects.requireNonNull(diagnostics, 'diagnostics')
        this.lanes = new CallLanes(settings.waitingEvery)
        this.logs = new OutputLog(directory)
        this.commands = Executors.newCachedThreadPool(daemons('grails-mcp-command'))
        this.timers = Executors.newScheduledThreadPool(1, daemons('grails-mcp-timer'))
        this.watcher = new ChangeWatcher(directory, { String fingerprint -> project(fingerprint) } as ChangeWatcher.Projection, settings.pollEvery,
                settings.firstList, { String list -> told(list) } as Consumer<String>, diagnostics)
        this.instructions = instructionsFor(directory, inProject(directory), readOnly)
    }

    /**
     * Starts serving: makes the first tools, watches the directory, and gives up on the calls that do not
     * end after they were cancelled.
     *
     * @param calls the calls that are running, as the protocol has them
     * @param exit what ends the server with an exit code, when it gives up on too many calls
     */
    void start(CallThreads calls, IntConsumer exit) {
        this.calls = Objects.requireNonNull(calls, 'calls')
        this.exit = Objects.requireNonNull(exit, 'exit')
        watcher.start()
        long every = Math.max(100L, Math.min(1000L, Math.floorDiv(settings.abandonAfter.toMillis(), 10L)))
        timers.scheduleWithFixedDelay({ -> giveUpOnStuckCalls() } as Runnable, every, every, TimeUnit.MILLISECONDS)
    }

    @Override
    Map<String, Object> serverInfo() {
        Map<String, Object> info = new LinkedHashMap<>()
        info.put('name', McpNames.SERVER_NAME)
        info.put('title', McpNames.SERVER_TITLE)
        info.put('version', VersionInfo.getGrailsVersion())
        info.put('websiteUrl', WEBSITE)
        return info
    }

    @Override
    String instructions() {
        return instructions
    }

    @Override
    McpToolSet tools() {
        return watcher.tools()
    }

    @Override
    Map<String, Object> callTool(String name, Map<String, Object> arguments, McpCallContext call) throws McpException {
        McpTool tool = tools().tools.find { McpTool each -> each.name == name }
        if (tool == null) {
            throw new McpException(McpException.INVALID_PARAMS, "Unknown tool: ${name}".toString())
        }
        List<String> commandLine
        try {
            commandLine = new ArrayList<String>(ToolProjection.ROOT_OPTIONS) + tool.commandLine(arguments)
        } catch (McpException e) {
            if (e.code != McpException.INVALID_PARAMS) {
                throw e
            }
            return refused(e.message)
        }
        Execution ran
        try {
            ran = execute(tool.lane, tool.name, commandLine, call, "Running: ${tool.title}".toString())
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
            return CommandOutput.cancelled()
        }
        if (ran.cancelled || call.isCancelled()) {
            return CommandOutput.cancelled()
        }
        CommandOutput.Ran output = new CommandOutput.Ran()
        output.title = tool.title
        output.commandLine = commandLine
        output.exitCode = ran.exitCode
        output.durationMs = ran.durationMs
        output.merged = ran.streams.merged()
        output.output = ran.streams.standardOutput()
        output.errors = ran.streams.standardError()
        output.json = tool.jsonResult(arguments)
        output.log = logs.keep(tool.name, CommandOutput.cleaned(output.merged))
        output.note = tool.name == 'create-tool' && ran.exitCode == 0 ? CommandOutput.LISTED_AFTER_COMPILE : null
        Map<String, Object> result = CommandOutput.result(output)
        if (tool.annotations.get('readOnlyHint') != Boolean.TRUE) {
            // at once, and not at the next look: what the call changed is in the list before its result is
            changedSince(tool.name)
        }
        return result
    }

    @Override
    List<Map<String, Object>> listResources() {
        return McpResources.list(tools())
    }

    @Override
    List<Map<String, Object>> listResourceTemplates() {
        return McpResources.templates()
    }

    @Override
    Map<String, Object> readResource(String uri) throws McpException {
        Map<String, Object> log = logs.read(uri)
        if (log != null) {
            return log
        }
        return McpResources.read(tools(), uri, { List<String> commandLine -> document(commandLine) } as McpResources.Documents)
    }

    @Override
    void onListChanged(Consumer<String> listener) {
        listeners.add(Objects.requireNonNull(listener, 'listener'))
    }

    /**
     * Stops watching the directory and giving up on calls, and waits a while for the commands that run
     * on threads of their own to end, so that the files they write are written whole.
     */
    @Override
    void close() {
        watcher.close()
        timers.shutdownNow()
        commands.shutdown()
        try {
            if (!commands.awaitTermination(settings.commandsAtShutdown.toMillis(), TimeUnit.MILLISECONDS)) {
                diagnostics.println('| Warning grails mcp ends while a command still writes the files of the project: ' +
                        'what it wrote last may not be whole, and the next command of the project says so where it can')
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
        }
    }

    // a call whose arguments the tool cannot be given: a result that is an error, and says which argument and why
    private static Map<String, Object> refused(String message) {
        Map<String, Object> text = new LinkedHashMap<>()
        text.put('type', 'text')
        text.put('text', message)
        Map<String, Object> result = new LinkedHashMap<>()
        result.put('content', [text])
        result.put('isError', true)
        return result
    }

    // Runs a command line in a lane. A call of the build lane runs it on this thread, which the client's cancel interrupts;
    // a call of another lane on a thread of its own, which nothing interrupts, while this one waits for it and stops
    // waiting when it is interrupted. The lane is given back when the command line has ended.
    private Execution execute(Lane lane, String tool, List<String> commandLine, McpCallContext call, String running) throws InterruptedException {
        Object owner = call != null ? call : new Object()
        CallLanes.Ticket ticket = lanes.take(lane, tool, owner, call == null ? null : { String message -> call.progress(message) } as Consumer<String>)
        CommandStreams streams = CommandStreams.capture()
        if (call != null) {
            call.progress(running)
        }
        ScheduledFuture<?> told = call == null ? null : progress(call, streams)
        long started = System.nanoTime()
        try {
            if (lane == Lane.BUILD) {
                int exitCode
                try {
                    exitCode = inProcess(commandLine, streams)
                } finally {
                    lanes.release(ticket)
                }
                // what runs Gradle or a tool sets the interrupt again when it was stopped by it
                boolean cancelled = Thread.currentThread().isInterrupted() || (call != null && call.isCancelled())
                return new Execution(exitCode, streams, elapsed(started), cancelled)
            }
            Future<Integer> future
            try {
                future = commands.submit({ ->
                    try {
                        return inProcess(commandLine, streams)
                    } finally {
                        lanes.release(ticket)
                    }
                } as Callable<Integer>)
            } catch (RejectedExecutionException ignored) {
                lanes.release(ticket)
                streams.err.println('| Error grails mcp is ending, and runs no more commands')
                return new Execution(1, streams, elapsed(started), false)
            }
            try {
                return new Execution(future.get(), streams, elapsed(started), false)
            } catch (InterruptedException e) {
                // the command goes on, and gives the lane back when it has ended; what it gives is dropped
                ticket.cancelled()
                throw e
            } catch (ExecutionException e) {
                streams.err.println("| Error grails mcp could not run the command: ${failure(e.cause ?: e)}")
                return new Execution(1, streams, elapsed(started), false)
            }
        } finally {
            told?.cancel(false)
        }
    }

    // one command line, in this process, with the class loader of the command line for the thread it runs on
    private int inProcess(List<String> commandLine, CommandStreams streams) {
        Thread thread = Thread.currentThread()
        ClassLoader before = thread.contextClassLoader
        thread.contextClassLoader = GrailsMcpServer.classLoader
        try {
            return ForgeCli.execute(context, streams, commandLine as String[])
        } catch (Exception e) {
            streams.err.println("| Error grails mcp could not run the command: ${failure(e)}")
            return 1
        } finally {
            thread.contextClassLoader = before
        }
    }

    // How the call goes, every so often, while it runs: how many lines it wrote and the last of them, when it wrote
    // more since; and the same again after a while without, so that a client that waits for a long build hears of it.
    private ScheduledFuture<?> progress(McpCallContext call, CommandStreams streams) {
        if (call.progressToken == null) {
            return null
        }
        long every = settings.progressEvery.toMillis()
        long quiet = settings.quietProgress.toNanos()
        int[] said = [0] as int[]
        long[] saidAt = [System.nanoTime()] as long[]
        return timers.scheduleAtFixedRate({ ->
            int lines = streams.lineCount()
            long now = System.nanoTime()
            if (lines > 0 && (lines != said[0] || now - saidAt[0] >= quiet)) {
                said[0] = lines
                saidAt[0] = now
                call.progress(progressLine(lines, streams.lastLine()))
            }
        } as Runnable, every, every, TimeUnit.MILLISECONDS)
    }

    /**
     * @param lines how many lines a command wrote
     * @param last the last of them
     * @return the message of progress that says so
     */
    static String progressLine(int lines, String last) {
        String line = CommandOutput.cleaned(last ?: '').trim()
        if (line.length() > MAX_PROGRESS_LINE) {
            line = line.substring(0, MAX_PROGRESS_LINE - 1) + '…'
        }
        return "${String.format(Locale.ENGLISH, '%,d', lines)} ${lines == 1 ? 'line' : 'lines'}; the last: ${line}".toString()
    }

    // the document help --json writes in the directory, which the tools are made from
    private McpToolSet project(String fingerprint) throws InterruptedException {
        List<String> commandLine = new ArrayList<String>(ToolProjection.ROOT_OPTIONS) + [HELP, '--json']
        Execution ran = execute(Lane.DIRECT, HELP, commandLine, null, null)
        Object document = null
        try {
            document = new JsonSlurper().parseText(ran.streams.standardOutput())
        } catch (JsonException | IllegalArgumentException ignored) {
            // said below
        }
        if (ran.exitCode != 0 || !(document instanceof Map)) {
            String said = ran.streams.standardError().readLines().find { String line -> !line.trim().isEmpty() }
            throw new IllegalStateException("help --json ended with exit code ${ran.exitCode}${said == null ? '' : ': ' + said.trim()}".toString())
        }
        return ToolProjection.of((Map<String, Object>) document, readOnly, fingerprint)
    }

    // what a command writes that is a resource of the project, run as a call of the direct lane is
    private String document(List<String> commandLine) throws McpException {
        List<String> line = new ArrayList<String>(ToolProjection.ROOT_OPTIONS) + commandLine
        Execution ran
        try {
            ran = execute(Lane.DIRECT, commandLine.first(), line, null, null)
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
            throw new McpException(McpException.INTERNAL_ERROR, "grails ${commandLine.join(' ')} was cancelled".toString())
        }
        String written = ran.streams.standardOutput()
        if (written.trim().isEmpty()) {
            String said = ran.streams.standardError().readLines().find { String each -> !each.trim().isEmpty() }
            throw new McpException(McpException.INTERNAL_ERROR, "grails ${commandLine.join(' ')} wrote no document${said == null ? '' : ': ' + CommandOutput.cleaned(said).trim()}".toString())
        }
        return written.trim()
    }

    // the directory may have changed with the call: the tools are made anew now where it did
    private void changedSince(String tool) {
        try {
            watcher.check()
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
        } catch (RuntimeException e) {
            diagnostics.println("| Warning grails mcp could not list the commands again after ${tool}: ${failure(e)}")
        }
    }

    private void told(String list) {
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(list)
            } catch (RuntimeException ignored) {
                // the others are told all the same
            }
        }
    }

    // The calls that were cancelled and have not ended long after: a call of the build lane that is still running on the
    // thread that was interrupted, and a command of another lane that goes on after its call stopped waiting for it.
    private void giveUpOnStuckCalls() {
        try {
            CallThreads running = calls
            if (running != null) {
                for (CallThreads.RunningCall call : running.interruptedFor(settings.abandonAfter)) {
                    if (running.abandon(call)) {
                        CallLanes.Ticket ticket = lanes.heldBy(call.context)
                        ticket?.abandon()
                        gaveUp(call.tool, ticket == null ? null : ticket.lane)
                    }
                }
            }
            for (CallLanes.Ticket ticket : lanes.held()) {
                Duration cancelled = ticket.cancelledFor
                if (cancelled != null && cancelled >= settings.abandonAfter && ticket.abandon()) {
                    gaveUp(ticket.tool, ticket.lane)
                }
            }
        } catch (RuntimeException e) {
            diagnostics.println("| Warning grails mcp could not look for the calls that do not end: ${failure(e)}")
        }
    }

    private void gaveUp(String tool, Lane lane) {
        boolean build = lane == Lane.BUILD
        long seconds = settings.abandonAfter.seconds
        diagnostics.println("| Warning grails mcp gave up on the call of ${tool}: it had not ended ${seconds} ${seconds == 1L ? 'second' : 'seconds'} after it was cancelled. " +
                "Its lane takes other calls again${build ? ', and the next build gets a new connection to Gradle' : ''}.")
        if (build) {
            // it can wait for the build that does not end: not on the thread that looks for the next call to give up on
            Thread closing = new Thread({ -> GradleBuild.closeConnections() } as Runnable, 'grails-mcp-close-gradle')
            closing.daemon = true
            closing.start()
        }
        int count = abandoned.incrementAndGet()
        if (count >= settings.abandonments && ending.compareAndSet(false, true)) {
            diagnostics.println("| Error grails mcp ends: ${count} calls did not end after they were cancelled. The client starts it again.")
            exit.accept(1)
        }
    }

    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
    }

    private static String failure(Throwable e) {
        return GradleRunner.line("${e.class.simpleName}${e.message == null ? '' : ': ' + e.message}")
    }

    private static boolean inProject(Path directory) {
        try {
            return ConsoleProject.find(directory) != null
        } catch (IOException ignored) {
            return false
        }
    }

    /**
     * @param directory the directory the server serves
     * @param project whether it is in a project
     * @param readOnly whether only the tools that change nothing are offered
     * @return the instructions of the server: what it is and which directory it serves, how its tools run,
     * what {@code refresh_tools} is for, the environment and how another is given, that {@code run-app}
     * leaves the application running, that long outputs are shortened and linked, and where the guide is;
     * {@value #MAX_INSTRUCTIONS} characters at most
     */
    static String instructionsFor(Path directory, boolean project, boolean readOnly) {
        String text = "grails mcp is the Grails command line of one directory, ${shown(directory.toString())}, which is " +
                (project ? 'in a Grails project. ' : 'in no Grails project: create-app creates one, and a project gets a grails mcp of its own, started in its directory. ') +
                'Each tool is a grails command, run there with --plain-output --non-interactive, without a terminal: nothing can be typed to it, ' +
                'and its result is what it wrote, an error where it failed. ' +
                'The tools of the application and its plugins are offered once the build has listed them, when the project is compiled; ' +
                'call refresh_tools after a tool was written or a plugin added, since grails mcp never runs a build by itself. ' +
                "They run in the ${ToolCommand.ENVIRONMENT} environment: run-tool with system-properties {\"grails.env\": \"test\"} runs one in another. " +
                'run-app leaves the application running after the call and after grails mcp, until stop-app stops it. ' +
                'A long output is shortened, and the result links the whole of it as a resource. ' +
                'The resource grails://commands lists every command and tool, and says why one is not offered. ' +
                (readOnly ? 'Only the tools that change nothing are offered (--read-only). ' : '') +
                "Guide: https://grails.apache.org/docs/${VersionInfo.getDocumentationVersion()}/guide/commandLine.html#mcp"
        return text.length() <= MAX_INSTRUCTIONS ? text : text.substring(0, MAX_INSTRUCTIONS - 1) + '…'
    }

    // a directory as the instructions show it: a long one by its end
    private static String shown(String directory) {
        int most = 300
        return directory.length() <= most ? directory : '…' + directory.substring(directory.length() - most + 1)
    }

    private static ThreadFactory daemons(String name) {
        AtomicInteger count = new AtomicInteger()
        return { Runnable runnable ->
            Thread thread = new Thread(runnable, "${name}-${count.incrementAndGet()}".toString())
            thread.daemon = true
            thread
        } as ThreadFactory
    }

    /**
     * How long the server waits for what. Every value has the default the design gives it; a specification
     * gives shorter ones.
     *
     * @since 9.0
     */
    @CompileStatic
    static final class Settings {

        /** How long after it was cancelled a call that has not ended is given up on. */
        Duration abandonAfter = Duration.ofSeconds(30)

        /** After how many calls that were given up on the server ends, with the exit code 1. */
        int abandonments = 3

        /** How often the files the tools depend on are looked at. */
        Duration pollEvery = Duration.ofSeconds(2)

        /** How often a call that waits for its lane is told that it does. */
        Duration waitingEvery = Duration.ofSeconds(5)

        /** How long the first tools are waited for when they are asked for. */
        Duration firstList = Duration.ofSeconds(15)

        /** How often, at most, a call is told how many lines it wrote. */
        Duration progressEvery = Duration.ofSeconds(1)

        /** After how long without a line a call is told again how many it wrote. */
        Duration quietProgress = Duration.ofSeconds(30)

        /** How long the commands that write files are waited for when the server ends. */
        Duration commandsAtShutdown = Duration.ofSeconds(5)
    }

    // how a command line that was run ended, and what it wrote
    @CompileStatic
    private static final class Execution {

        final int exitCode
        final CommandStreams streams
        final long durationMs
        final boolean cancelled

        Execution(int exitCode, CommandStreams streams, long durationMs, boolean cancelled) {
            this.exitCode = exitCode
            this.streams = streams
            this.durationMs = durationMs
            this.cancelled = cancelled
        }
    }
}
