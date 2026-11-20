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

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import groovy.transform.CompileStatic
import org.grails.forge.application.OperatingSystem
import picocli.CommandLine

/**
 * {@code stop-app}: ends the application that {@code run-app} started for the project, and nothing
 * else. It goes by the record {@code run-app} wrote and by what the system says of the recorded
 * process, see {@link RunningApplication}; it never looks for whatever listens on a port.
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'stop-app',
        description = [
                'Stops the Grails application that run-app started for this project.',
                'Ends the Gradle that run-app started, and with it every process below it. The application ends with them:',
                'it is one of them, or it is ended by the Gradle daemon that runs the build, when the Gradle it worked for is gone.',
                'The command then waits until nothing answers on the port of the application any more.',
                'Nothing else is ended: a process is ended only when it is the one run-app recorded in .grails/run.'
        ])
@CompileStatic
class StopAppCommand extends InProjectCommand {

    private static final int STOP_SECONDS = 30
    private static final int PORT_SECONDS = 30
    private static final int POLL_MILLIS = 250
    private static final String RECORD = "${RunDirectory.LOCATION}/${RunningApplication.RECORD}"
    private static final String NOT_RUNNING = 'No application that run-app started is running for this project'

    @Override
    protected String notDone() {
        return 'Nothing was stopped'
    }

    @Override
    protected int run(ConsoleProject project) {
        RunningApplication running = RunningApplication.find(project, getOperatingSystem() == OperatingSystem.WINDOWS)
        switch (running.state) {
            case RunningApplication.State.NONE:
                err("${NOT_RUNNING}: there is no record of one, ${RECORD}. Nothing was stopped")
                return 1
            case RunningApplication.State.REFUSED:
                err("${NOT_RUNNING} that this command can know of: ${GradleRunner.line(running.reason)}. Nothing was stopped")
                return 1
            case RunningApplication.State.UNREADABLE:
                boolean removed = running.forget()
                err("${NOT_RUNNING} that this command can know of: ${RECORD} cannot be used, ${GradleRunner.line(running.reason)}. " +
                        "Nothing was stopped${removed ? ', and the record was removed' : ''}")
                return 1
            case RunningApplication.State.ENDED:
                boolean forgotten = running.forget()
                err("${NOT_RUNNING}: ${running.reason}. Nothing was stopped${forgotten ? ', and the record was removed' : ''}")
                return 1
            case RunningApplication.State.UNCONFIRMED:
                err("The application was not stopped: ${running.reason}. End that process yourself if it is the application, and delete ${RECORD}")
                return 1
        }
        return stop(project, running)
    }

    private int stop(ConsoleProject project, RunningApplication running) {
        out('| Stopping application...')
        if (!running.detached) {
            // the run-app that stays with the application is told that its end was asked for
            try {
                Path directory = RunDirectory.existing(project)
                if (directory != null) {
                    running.askToStop(directory)
                }
            } catch (IOException ignored) {
                // then that run-app says that Gradle did not succeed
            }
        }
        if (!GradleRunner.stop(running.process, STOP_SECONDS)) {
            err("Process ${running.pid}, or one it started, has not ended. The record was kept: run stop-app again")
            return 1
        }
        running.forget()
        // A Gradle daemon that was there before run-app is not below what was ended: it cancels the build
        // when the Gradle it worked for is gone, and ends the application then. That it did is seen at
        // the port, and only there.
        if (running.port > 0 && !closed(running.port)) {
            err("Gradle was stopped, but something answers on port ${running.port} still after ${PORT_SECONDS} seconds: the application may take longer to end, or it is another one. " +
                    'Nothing else was stopped')
            return 1
        }
        out('| Application stopped.')
        return 0
    }

    private static boolean closed(int port) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PORT_SECONDS)
        while (ApplicationPort.isOpen(port)) {
            if (System.nanoTime() > deadline) {
                return false
            }
            try {
                Thread.sleep(POLL_MILLIS)
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return true
    }
}
