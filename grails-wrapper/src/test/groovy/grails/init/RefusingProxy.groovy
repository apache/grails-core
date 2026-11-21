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
package grails.init

import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A proxy on the loopback address that refuses every request and remembers which host it was asked for.
 * A test that lets the wrapper go on to its remote repositories names this proxy to the JVM, so that
 * nothing leaves the machine and the repositories that were tried can be told.
 */
class RefusingProxy implements Closeable {

    private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.loopbackAddress)
    private final List<String> requested = new CopyOnWriteArrayList<>()
    private final Thread thread

    RefusingProxy() {
        thread = new Thread({ serve() }, 'refusing-proxy')
        thread.daemon = true
        thread.start()
    }

    String getHost() {
        socket.inetAddress.hostAddress
    }

    int getPort() {
        socket.localPort
    }

    /**
     * @return the hosts that were asked for, in the order of the requests
     */
    List<String> getRequestedHosts() {
        new ArrayList<>(requested)
    }

    /**
     * @return the system properties that send the HTTPS requests of a JVM to this proxy
     */
    Map<String, String> getSystemProperties() {
        ['https.proxyHost': host, 'https.proxyPort': port as String]
    }

    private void serve() {
        while (!socket.closed) {
            try {
                socket.accept().withCloseable { Socket connection ->
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.inputStream, StandardCharsets.ISO_8859_1))
                    String requestLine = reader.readLine()
                    String header = requestLine
                    while (header) {
                        header = reader.readLine()
                    }
                    if (requestLine) {
                        // CONNECT repo1.maven.org:443 HTTP/1.1
                        requested.add(requestLine.split(' ')[1].split(':')[0])
                    }
                    connection.outputStream.write('HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n'.getBytes(StandardCharsets.ISO_8859_1))
                    connection.outputStream.flush()
                }
            } catch (IOException ignored) {
                // the socket was closed, or a client went away
            }
        }
    }

    @Override
    void close() {
        socket.close()
        thread.join()
    }
}
