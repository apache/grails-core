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

import java.nio.charset.StandardCharsets

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
     * Returns true if the server is available, which is once the application's web server answers on the port.
     * An application that uses the {@code grails-startup-progress} module answers on the port before its web
     * server starts, marking each response with the {@code Grails-Startup-Phase} header, so a response that
     * carries it means the application is still starting. Once the web server has the port, it answers this
     * request itself, even while {@code BootStrap} runs, as it does for an application without that module.
     * A port that accepts the connection but does not answer plain HTTP, as one with SSL does, counts as
     * available, since that it accepts connections is all there is to go on.
     *
     * <p>The request is made over the socket that connects, so a port that stops accepting connections between
     * two connects, as it does when it changes hands from the progress page to the web server, cannot be
     * taken for one that does not answer plain HTTP, and no proxy set for the JVM can answer for the port.</p>
     *
     * @param host The host
     * @param port The port
     */
    boolean isServerAvailable(String host = 'localhost', int port = 8080) {
        String serverHost = host ?: 'localhost'
        int serverPort = port ?: 8080
        Socket socket = new Socket()
        try {
            socket.connect(new InetSocketAddress(serverHost, serverPort), 1000)
        } catch (IOException ignored) {
            socket.close()
            return false
        }
        try {
            socket.soTimeout = 2000
            socket.outputStream.write("HEAD / HTTP/1.0\r\nHost: ${serverHost}:${serverPort}\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            String phase = startupPhase(socket.inputStream)
            return phase == null || phase == 'READY'
        } catch (IOException ignored) {
            // the port accepts connections but does not answer plain HTTP, as with SSL
            return true
        } finally {
            socket.close()
        }
    }

    /**
     * The {@code Grails-Startup-Phase} header of the response, or {@code null} when the response is not HTTP or
     * does not carry one.
     */
    private String startupPhase(InputStream response) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(response, StandardCharsets.ISO_8859_1))
        String line = reader.readLine()
        if (line == null || !line.startsWith('HTTP/')) {
            return null
        }
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':')
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase('Grails-Startup-Phase')) {
                return line.substring(colon + 1).trim()
            }
        }
        return null
    }
}
