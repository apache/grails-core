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
package org.apache.grails.gradle.plugin.aot

import groovy.transform.CompileStatic
import org.gradle.api.GradleException

/**
 * The port a run is going to be reached on, checked before the run is started.
 *
 * <p>Whether the application has started is answered in part by connecting to its port, and a
 * connection cannot tell one application from another. So anything already listening on the port --
 * a second build on the same CI agent, a developer's own server, a run left behind by a build that
 * was cancelled -- answers on the application's behalf: the wait ends immediately, and what is then
 * trained or traced is a recording of somebody else's application. It is reported as a success,
 * because from the task's point of view nothing went wrong.</p>
 *
 * <p>Binding the port first turns that into a failure that names the port, before anything is run.
 * It is not proof against a race -- something can take the port between the check and the run --
 * but the case worth catching is a port that is already busy, which this catches every time.</p>
 */
@CompileStatic
final class RunPort {

    private RunPort() {
    }

    /**
     * Whether what listens on the port says it is not ready yet. A connection and the log saying the application
     * started both come before its {@code ApplicationStartedEvent}, and a filter that holds requests until then, such
     * as the Spring Security plugin's, answers them with 503, which a run would record in place of the page.
     *
     * <p>Only a 503 that carries {@code Retry-After}, which is how a 503 says the unavailability is temporary, and
     * how the Spring Security plugin's says it. A 503 without it is the application's own answer, which waiting will
     * not change, and a run that waited for it would sit out its whole start timeout before going on.</p>
     *
     * @param port the port the run is reached on
     * @return whether it answers with 503 and {@code Retry-After}, and not where nothing answers at all
     * @since 9.0
     */
    static boolean answersNotReady(int port) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URI("http://localhost:${port}/").toURL().openConnection()
            connection.requestMethod = 'HEAD'
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 500
            connection.readTimeout = 2000
            try {
                return connection.responseCode == HttpURLConnection.HTTP_UNAVAILABLE &&
                        connection.getHeaderField('Retry-After') != null
            }
            finally {
                connection.disconnect()
            }
        }
        catch (IOException ignored) {
            return false
        }
    }

    /**
     * Refuses where something is already listening on the port.
     *
     * @param port the port the run will be reached on
     * @param what what is being attempted, to open the message with
     * @param setting the build setting that names the port, so the message says what to change
     */
    static void refuseWhereTaken(int port, String what, String setting) {
        try {
            new ServerSocket().withCloseable { ServerSocket socket ->
                socket.bind(new InetSocketAddress(InetAddress.getByName('localhost'), port), 1)
            }
        }
        catch (IOException ignored) {
            throw new GradleException("${what} needs port ${port}, and something is already " +
                    'listening on it. Whatever answers there would be taken for the application ' +
                    "and recorded in its place. Stop what is using the port, or set ${setting} to " +
                    'one that is free.')
        }
    }
}
