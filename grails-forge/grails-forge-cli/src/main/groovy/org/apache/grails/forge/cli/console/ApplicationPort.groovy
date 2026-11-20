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

import java.nio.charset.StandardCharsets
import groovy.transform.CompileStatic

/**
 * Asks whether an application answers on a port of this computer. Only the loopback addresses are
 * asked, {@code 127.0.0.1} and {@code ::1}: nothing is sent anywhere else, and no name is looked up.
 *
 * @since 9.0
 */
@CompileStatic
final class ApplicationPort {

    private static final int CONNECT_MILLIS = 1000
    private static final int ANSWER_MILLIS = 2000
    private static final int MAX_ANSWER = 8192
    private static final String PHASE = 'grails-startup-phase'
    private static final List<byte[]> LOOPBACK = [[127, 0, 0, 1] as byte[], [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1] as byte[]]

    private ApplicationPort() {
    }

    /**
     * @param host the address an application is bound to, as it was given
     * @return whether an application bound to it answers on a loopback address: it is not given, is
     * {@code localhost}, or is the text of a loopback address or of the address that stands for all
     */
    static boolean isReachedOnLoopback(String host) {
        if (host == null || host.isEmpty() || host.equalsIgnoreCase('localhost')) {
            return true
        }
        // only the text of an address is read, which asks no name server
        if (!(host ==~ /[0-9.]{1,15}/) && !(host ==~ /\[?[0-9A-Fa-f:.]{2,45}]?/ && host.contains(':'))) {
            return false
        }
        try {
            InetAddress address = InetAddress.getByName(host)
            return address.isLoopbackAddress() || address.isAnyLocalAddress()
        } catch (UnknownHostException ignored) {
            return false
        }
    }

    /**
     * @param port the port
     * @return whether anything accepts a connection on the port
     */
    static boolean isOpen(int port) {
        for (byte[] address : LOOPBACK) {
            Socket socket = new Socket()
            try {
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(address), port), CONNECT_MILLIS)
                return true
            } catch (IOException ignored) {
                // nothing there
            } finally {
                close(socket)
            }
        }
        return false
    }

    /**
     * Whether the application answers on the port. An application that shows a page while it starts
     * answers before it is ready and marks each answer with the {@code Grails-Startup-Phase} header:
     * such an answer is none yet. A port that accepts the connection and does not answer plain HTTP,
     * as one with TLS does, counts as ready, since that it accepts connections is all there is to go on.
     *
     * @param port the port
     * @return whether the application is ready
     */
    static boolean isReady(int port) {
        for (byte[] address : LOOPBACK) {
            Socket socket = new Socket()
            try {
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(address), port), CONNECT_MILLIS)
            } catch (IOException ignored) {
                close(socket)
                continue
            }
            try {
                socket.setSoTimeout(ANSWER_MILLIS)
                socket.getOutputStream().write("HEAD / HTTP/1.0\r\nHost: localhost:${port}\r\n\r\n".toString().getBytes(StandardCharsets.ISO_8859_1))
                socket.getOutputStream().flush()
                String phase = phaseOf(socket.getInputStream())
                return phase == null || phase.equalsIgnoreCase('READY')
            } catch (IOException ignored) {
                return true
            } finally {
                close(socket)
            }
        }
        return false
    }

    // the value of the header in the first lines of an answer, of which only so much is read
    private static String phaseOf(InputStream answer) throws IOException {
        StringBuilder line = new StringBuilder()
        boolean first = true
        for (int read = 0; read < MAX_ANSWER; read++) {
            int next = answer.read()
            if (next != -1 && next != (int) ('\n' as char)) {
                line.append((char) next)
                continue
            }
            String header = line.toString().trim()
            line.setLength(0)
            if (first && !header.startsWith('HTTP/')) {
                return null
            }
            first = false
            int colon = header.indexOf(':')
            if (colon > 0 && header.substring(0, colon).trim().equalsIgnoreCase(PHASE)) {
                return header.substring(colon + 1).trim()
            }
            if (next == -1 || header.isEmpty()) {
                return null
            }
        }
        return null
    }

    private static void close(Socket socket) {
        try {
            socket.close()
        } catch (IOException ignored) {
            // nothing depends on it
        }
    }
}
