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
import java.util.function.Consumer
import java.util.function.IntConsumer
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.CommandStreams
import org.apache.grails.forge.cli.console.ConsoleCommands
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.PlainOutput
import org.apache.grails.forge.cli.console.RunAppCommand
import org.apache.grails.forge.cli.mcp.protocol.McpLifecycle
import org.apache.grails.forge.cli.mcp.protocol.ModernServer
import org.grails.forge.cli.ForgeContext

/**
 * One session of {@code grails mcp}, from its start to its end: the process is made the server of one
 * directory, serves on the standard input and output the guard kept for it, and when it ends closes what
 * the session held, in this order: the commands that still write files are waited for a while, the
 * connection to Gradle is closed, the project generator is closed, and the applications the session
 * started and stays with are stopped.
 *
 * @since 9.0
 */
@CompileStatic
final class McpSession {

    // the exit code is returned, and the program grails ends the JVM with it
    private static final IntConsumer RETURNS = { int exitCode -> } as IntConsumer

    private McpSession() {
    }

    /**
     * Serves until the client closes the input, the process is sent SIGINT or SIGTERM, or the server gives
     * up on too many calls, on the thread that calls this, which is the main thread of the process.
     *
     * @param context the application the commands run on, which the session closes when it ends
     * @param guard what keeps the standard input and output of the process for the protocol
     * @param directory the directory to serve, which is the current directory of the process from now on
     * @param readOnly whether only the tools that change nothing are offered
     * @param settings how long the server waits for what
     * @return the exit code: 0, or 1 where the server gave up on too many calls
     */
    static int serve(ForgeContext context, CommandStreams.Guard guard, Path directory, boolean readOnly, GrailsMcpServer.Settings settings) {
        PrintStream diagnostics = CommandStreams.processErr()
        String userDir = System.getProperty('user.dir')
        // once, for the session: every command of a call reads the current directory from it
        System.setProperty('user.dir', directory.toString())
        Hooks hooks = new Hooks()
        GrailsMcpServer handler = null
        ConsoleCommands.setSession(ConsoleCommands.Session.MCP)
        PlainOutput.enter()
        try {
            handler = new GrailsMcpServer(context, directory, readOnly, settings, diagnostics)
            GrailsMcpServer served = handler
            ModernServer server = new ModernServer(handler, guard.takeInput(), guard.takeOutput())
            McpLifecycle lifecycle = new McpLifecycle(server, diagnostics, RETURNS, hooks)
                    .onShutdown('the commands of the session', { -> served.close() } as Runnable)
                    .onShutdown('the connection to Gradle', { ->
                        if (!GradleBuild.closeConnections()) {
                            diagnostics.println('| Warning grails mcp gave up on the connection to Gradle: a build on it had not ended')
                        }
                    } as Runnable)
                    .onShutdown('the project generator', { -> context.close() } as Runnable)
                    .onShutdown('the applications the session started', { -> RunAppCommand.stopWhatTheShellStarted() } as Runnable)
            handler.start(server.calls, { int exitCode -> lifecycle.requestExit(exitCode) } as IntConsumer)
            return lifecycle.serve()
        } finally {
            // where the session did not get to end in its order, as when the server could not be made; once only
            handler?.close()
            hooks.remove()
            PlainOutput.leave()
            ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
            System.setProperty('user.dir', userDir)
        }
    }

    // what is run when the process is sent SIGINT or SIGTERM: a hook of the JVM, which is taken away again when the
    // session has ended otherwise, so that a JVM that goes on after it is left none
    @CompileStatic
    private static final class Hooks implements Consumer<Runnable> {

        private Thread hook

        @Override
        synchronized void accept(Runnable onSignal) {
            hook = new Thread(onSignal, 'grails-mcp-shutdown')
            Runtime.getRuntime().addShutdownHook(hook)
        }

        synchronized void remove() {
            if (hook == null) {
                return
            }
            try {
                Runtime.getRuntime().removeShutdownHook(hook)
            } catch (IllegalStateException ignored) {
                // the JVM is ending, and runs the hook
            }
            hook = null
        }
    }
}
