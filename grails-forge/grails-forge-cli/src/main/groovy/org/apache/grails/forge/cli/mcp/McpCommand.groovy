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
package org.apache.grails.forge.cli.mcp

import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.function.BooleanSupplier
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.cli.GrailsCli
import org.apache.grails.forge.cli.console.CommandStreams
import org.apache.grails.forge.cli.console.ConsoleCommands
import org.apache.grails.forge.cli.mcp.run.GrailsMcpServer
import org.apache.grails.forge.cli.mcp.run.McpSession
import org.grails.forge.cli.ForgeContext
import org.grails.forge.cli.command.BaseCommand
import picocli.CommandLine

/**
 * {@code grails mcp}: the command line as a server of the Model Context Protocol, which an MCP client,
 * an agent, starts and speaks to on the standard input and output of the process. It serves one
 * directory, the current one or the one {@code --directory} names, and runs each call of a tool in its own
 * process as one command line, as the interactive console runs a line. {@code --read-only} offers only
 * the tools that change nothing.
 *
 * <p>It needs a process of its own, whose standard output nothing but the protocol is written to: the
 * program {@code grails} keeps it so for {@code mcp} before anything else runs. So it is refused at the
 * prompt of the interactive console, with exit code 2, and where the command line is run in a program's
 * own JVM, with exit code 1. When its input is a terminal it says, once, on the standard error, that it is
 * for an agent, and serves all the same.</p>
 *
 * <p>Nothing of the MCP Java SDK is named here: a program that runs the command line in its own JVM, and
 * never this command, can leave the SDK out.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = McpCommand.NAME, hidden = true, description = [
        'Serves the commands of grails, and the tools of the project, to an AI agent over the Model Context Protocol, on the standard input and output.',
        'An MCP client starts it, as with: claude mcp add grails -- grails mcp'
])
@CompileStatic
class McpCommand extends BaseCommand implements Callable<Integer> {

    /** The name of the command. */
    public static final String NAME = 'mcp'

    /** What is said on the standard error when the input of {@code grails mcp} is a terminal. */
    public static final String TERMINAL = 'grails mcp speaks the Model Context Protocol on its standard input and output, for an agent: claude mcp add grails -- grails mcp'

    /** What a refusal at the prompt of the interactive console says. */
    public static final String AT_THE_PROMPT = 'grails mcp is not run at the prompt of the interactive console: it speaks the Model Context Protocol on the standard input ' +
            'and output of a process of its own, which an MCP client starts, as with: claude mcp add grails -- grails mcp'

    /** What a refusal in a JVM that does not keep its standard output for the protocol says. */
    public static final String NOT_ITS_OWN_PROCESS = 'grails mcp needs a process of its own: it speaks the Model Context Protocol on the standard input and output of ' +
            'the process, which the program grails keeps for it when it is started as grails mcp. An MCP client starts it, as with: claude mcp add grails -- grails mcp'

    private static final BooleanSupplier INPUT_OF_A_TERMINAL = { -> inputIsTerminal() } as BooleanSupplier

    private static volatile BooleanSupplier terminal = INPUT_OF_A_TERMINAL

    @CommandLine.Option(names = ['--directory'], paramLabel = 'DIRECTORY',
            description = 'Serve this directory instead of the current one: a project, or where projects are created. Another project gets a grails mcp of its own.')
    String directory

    @CommandLine.Option(names = ['--read-only'], description = 'Offer only the tools that change nothing.')
    boolean readOnly

    private final ForgeContext context

    /**
     * @param context the application of the command line, which the session keeps, and closes when it ends
     */
    McpCommand(ForgeContext context) {
        this.context = context
    }

    @Override
    Integer call() throws Exception {
        if (ConsoleCommands.getSession() == ConsoleCommands.Session.CONSOLE) {
            err(AT_THE_PROMPT)
            return 2
        }
        CommandStreams.Guard guard = CommandStreams.guarded()
        if (guard == null) {
            err(NOT_ITS_OWN_PROCESS)
            return 1
        }
        Path served = served()
        if (context.commandName == null) {
            // what help --json says the program is
            context.commandName = GrailsCli.NAME
        }
        if (terminal.getAsBoolean()) {
            CommandStreams.processErr().println(TERMINAL)
        }
        return McpSession.serve(context, guard, served, readOnly, new GrailsMcpServer.Settings())
    }

    // the directory to serve: the current one, or the one --directory names, as the real path it is
    private Path served() {
        Path current = Paths.get(System.getProperty('user.dir'))
        if (directory == null) {
            return current.toAbsolutePath().normalize()
        }
        Path named
        try {
            named = current.resolve(directory)
        } catch (InvalidPathException ignored) {
            named = null
        }
        if (named == null || !Files.isDirectory(named)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "--directory names no directory: ${directory}".toString())
        }
        return named.toRealPath()
    }

    /**
     * Replaces what tells whether the input of the process is a terminal, for a specification.
     *
     * @param inputIsTerminal what tells it; {@code null} for the console of the JVM
     */
    @PackageScope
    static void setTerminal(BooleanSupplier inputIsTerminal) {
        terminal = inputIsTerminal ?: INPUT_OF_A_TERMINAL
    }

    // Whether a user typed the command at a terminal. Before Java 22 the JVM has a console only where its input and output
    // are a terminal; from Java 22 on it has one in any case, and says whether it is a terminal.
    private static boolean inputIsTerminal() {
        Console console = System.console()
        if (console == null) {
            return false
        }
        try {
            Method isTerminal = Console.getMethod('isTerminal')
            return Boolean.TRUE == isTerminal.invoke(console)
        } catch (NoSuchMethodException ignored) {
            return true
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return false
        }
    }
}
