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
package org.grails.cli.profile.commands.io

import groovy.transform.CompileStatic

/**
 * Methods to aid interacting with the server from the CLI
 *
 * @author Graeme Rocher
 * @since 3.0.3
 */
@CompileStatic
trait ServerInteraction {

    /**
     * Waits for the server to startup
     *
     * @param host The host
     * @param port The port
     */
    void waitForStartup(String host = 'localhost', int port = 8080) {
        while (!isServerAvailable(host, port)) {
            sleep(100)
        }
        try {
            new URL("http://${host ?: 'localhost'}:${port ?: 8080}/is-tomcat-running").text
        } catch (ignored) {
            // ignore
        }
    }

    /**
     * Returns true if the server is available, which is once the application answers on the port. While a
     * Grails application starts, its startup progress page can answer on the port before the application does,
     * marking each response with the {@code Grails-Startup-Phase} header, so a response that carries it means the
     * application is still starting.
     *
     * @param host The host
     * @param port The port
     */
    boolean isServerAvailable(String host = 'localhost', int port = 8080) {
        try {
            new Socket(host, port).close()
        } catch (e) {
            return false
        }
        HttpURLConnection connection = null
        try {
            connection = (HttpURLConnection) URI.create("http://${host}:${port}/").toURL().openConnection()
            connection.requestMethod = 'HEAD'
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 1000
            connection.readTimeout = 2000
            connection.responseCode
            String phase = connection.getHeaderField('Grails-Startup-Phase')
            return phase == null || phase == 'READY'
        } catch (e) {
            // the port does not answer plain HTTP, as with SSL, so that it accepts connections is all there is to go on
            return true
        } finally {
            connection?.disconnect()
        }
    }
}
