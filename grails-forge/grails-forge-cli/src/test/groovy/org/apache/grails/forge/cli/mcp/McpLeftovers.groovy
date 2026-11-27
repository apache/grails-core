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

import org.apache.grails.forge.cli.console.ConsoleCommands
import org.apache.grails.forge.cli.console.PlainOutput

/**
 * What a specification that runs {@code grails mcp}, or its command lines, in this JVM leaves behind for
 * the specifications that run after it in the same JVM: nothing. Each such specification checks it when a
 * feature has ended and it has put back what it changed itself: no command of a server still runs on a
 * thread of the server's own, the output is no longer plain, which every command line of a call makes it
 * while it runs, and the commands run in no session. A command that is still running in the JVM, as one
 * that a server gave up on, would otherwise make the command lines of the next specification plain, and
 * give Gradle {@code --console=plain}.
 */
class McpLeftovers {

    /** What the threads that run the commands of the generator and direct lanes are named, followed by a number. */
    static final String COMMAND_THREADS = 'grails-mcp-command-'

    private static final long WAIT_MILLIS = 5_000L

    /**
     * Waits a moment for a thread of a command that has just ended to be gone, and then holds that
     * nothing is left.
     */
    static void assertNoneLeft() {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (!commandThreads().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assert commandThreads().isEmpty(): "commands of grails mcp still run after the feature: ${commandThreads()*.name}"
        assert !PlainOutput.isOn(): 'the output is still plain after the feature: a command line of a call still runs, or plain output was entered and not left'
        assert ConsoleCommands.getSession() == ConsoleCommands.Session.NONE: "the session is still ${ConsoleCommands.getSession()} after the feature"
    }

    private static List<Thread> commandThreads() {
        return Thread.getAllStackTraces().keySet().findAll { Thread thread -> thread.alive && thread.name.startsWith(COMMAND_THREADS) }.toList()
    }
}
