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
import java.security.GeneralSecurityException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
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
    // asked of the loopback address only, where the certificate is the one of development
    private static final X509TrustManager ANY_CERTIFICATE = new X509TrustManager() {

        @Override
        void checkClientTrusted(X509Certificate[] chain, String type) {
        }

        @Override
        void checkServerTrusted(X509Certificate[] chain, String type) {
        }

        @Override
        X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0]
        }
    }
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
     * Whether the application answers on the port: it accepts a connection and answers a request
     * with a response of HTTP, whatever the status. A port that only accepts connections is not an
     * application that answers. When the answer to plain HTTP is none, the request is made again
     * over TLS, with any certificate taken, as the self-signed one of development is: the address is
     * this computer's, and nothing is sent but the request line.
     *
     * <p>An application that shows a page while it starts answers before it is ready and marks each
     * answer with the {@code Grails-Startup-Phase} header: such an answer is none yet.</p>
     *
     * @param port the port
     * @return whether the application is ready
     */
    static boolean isReady(int port) {
        for (byte[] address : LOOPBACK) {
            InetSocketAddress loopback = new InetSocketAddress(InetAddress.getByAddress(address), port)
            Answer plain = ask(loopback, false)
            if (plain == null) {
                continue
            }
            return plain == Answer.READY || (plain == Answer.NONE && ask(loopback, true) == Answer.READY)
        }
        return false
    }

    private enum Answer {
        NONE, STARTING, READY
    }

    // null when nothing accepts a connection
    private static Answer ask(InetSocketAddress loopback, boolean tls) {
        Socket socket = new Socket()
        try {
            socket.connect(loopback, CONNECT_MILLIS)
        } catch (IOException ignored) {
            close(socket)
            return null
        }
        try {
            socket.setSoTimeout(ANSWER_MILLIS)
            if (tls) {
                SSLContext context = SSLContext.getInstance('TLS')
                context.init(null, [ANY_CERTIFICATE] as TrustManager[], null)
                socket = context.getSocketFactory().createSocket(socket, 'localhost', loopback.getPort(), true)
                ((SSLSocket) socket).startHandshake()
            }
            socket.getOutputStream().write("HEAD / HTTP/1.0\r\nHost: localhost:${loopback.getPort()}\r\n\r\n".toString().getBytes(StandardCharsets.ISO_8859_1))
            socket.getOutputStream().flush()
            return answerOf(socket.getInputStream())
        } catch (IOException | GeneralSecurityException | RuntimeException ignored) {
            return Answer.NONE
        } finally {
            close(socket)
        }
    }

    // what the first lines of an answer say, of which only so much is read
    private static Answer answerOf(InputStream answer) throws IOException {
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
            if (first && !(header ==~ /HTTP\/[0-9.]{1,5} [0-9]{3}( .*)?/)) {
                return Answer.NONE
            }
            first = false
            int colon = header.indexOf(':')
            if (colon > 0 && header.substring(0, colon).trim().equalsIgnoreCase(PHASE)) {
                return header.substring(colon + 1).trim().equalsIgnoreCase('READY') ? Answer.READY : Answer.STARTING
            }
            if (next == -1 || header.isEmpty()) {
                return Answer.READY
            }
        }
        return Answer.READY
    }

    private static void close(Socket socket) {
        try {
            socket.close()
        } catch (IOException ignored) {
            // nothing depends on it
        }
    }
}
