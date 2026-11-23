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
 * <p>A {@code run-app} that left the application running recorded the Gradle it started, and that
 * process is ended. A {@code run-app} that stays with its application recorded itself: it is asked to
 * end the build it runs, which it does, and no process is ended by this command.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'stop-app',
        description = [
                'Stops the Grails application that run-app started for this project.',
                'A run-app that stays with the application is asked to end its build, and does.',
                'Of a run-app that left the application running, the Gradle it started is ended, and with it every process below it.',
                'The application ends with the build: the Gradle daemon that runs the build ends what the build started.',
                'The command then waits until nothing takes connections on the port of the application any more,',
                'and says so when the application had not taken its port yet: its end is then not seen.',
                'Nothing else is ended: a process is ended, or asked, only when it is the one run-app recorded in .grails/run.'
        ])
@CompileStatic
class StopAppCommand extends InProjectCommand {

    private static final int STOP_SECONDS = 30
    private static final int PORT_SECONDS = 30
    // between two looks at whether the run-app that was asked has ended, or the port is free: a user waits half of it on average
    private static final int POLL_MILLIS = 50
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
        return running.commandLine ? ask(project, running) : stop(project, running)
    }

    // The run-app that stays with the application runs its build from its own JVM: it is told that the
    // end was asked for, and cancels the build. Nothing is ended here, so a record that names a process
    // which is no such run-app costs a wait and nothing else.
    private int ask(ConsoleProject project, RunningApplication running) {
        out('| Stopping application...')
        boolean answered = running.port > 0 && ApplicationPort.isOpen(running.port, running.address)
        Path directory
        try {
            directory = RunDirectory.existing(project)
            if (directory == null) {
                throw new IOException("${RunDirectory.LOCATION} is not there any more")
            }
            running.askToStop(directory)
        } catch (IOException e) {
            err("The run-app that stays with the application, process ${running.pid}, could not be asked to end it: ${GradleRunner.line(e.getMessage())}. " +
                    'Nothing was stopped; end that command where it runs')
            return 1
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STOP_SECONDS)
        while (running.process.isAlive() && running.isRecorded()) {
            if (System.nanoTime() > deadline || !pause()) {
                running.withdraw(directory)
                err("The run-app that stays with the application, process ${running.pid}, was asked to end it and has not within ${STOP_SECONDS} seconds. " +
                        'Nothing is ended by force: end that command where it runs, with Ctrl-C. The record was kept')
                return 1
            }
        }
        running.withdraw(directory)
        running.forget()
        return seenAtPort(running, answered, 'The build was stopped')
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
        // what the port says afterwards is the end of the application only when it had the port before
        boolean answered = running.port > 0 && ApplicationPort.isOpen(running.port, running.address)
        if (!GradleRunner.stop(running.process, STOP_SECONDS)) {
            err("Process ${running.pid}, or one it started, has not ended. The record was kept: run stop-app again")
            return 1
        }
        running.forget()
        return seenAtPort(running, answered, 'Gradle was stopped')
    }

    // A Gradle daemon is not below what was ended, whether it was there before run-app or was started
    // for it: it cancels the build when the Gradle it worked for is gone or asks for it, and ends the
    // application then. That it did is seen at the port, and only there.
    private int seenAtPort(RunningApplication running, boolean answered, String stopped) {
        if (running.port > 0 && !closed(running.port, running.address)) {
            err("${stopped}, but something answers on port ${running.port} still after ${PORT_SECONDS} seconds: the application may take longer to end, or it is another one. " +
                    'Nothing else was stopped')
            return 1
        }
        if (running.port > 0 && !answered) {
            // an application that was still starting: a port nothing had taken says nothing by being free
            out("| ${stopped}. Nothing took connections on port ${running.port} yet, so the end of the application was not seen there: " +
                    'an application that was starting is ended with its build.')
            return 0
        }
        out(GradleRunner.asksToEnd() ? '| Application stopped.' : "| Application stopped. ${GradleRunner.ENDED_AT_ONCE}")
        return 0
    }

    private static boolean pause() {
        try {
            Thread.sleep(POLL_MILLIS)
            return true
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
            return false
        }
    }

    private static boolean closed(int port, String address) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PORT_SECONDS)
        while (ApplicationPort.isOpen(port, address)) {
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
