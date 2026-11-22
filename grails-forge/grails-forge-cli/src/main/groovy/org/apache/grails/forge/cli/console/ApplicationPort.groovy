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
    private static final String ROOT = '/'
    // the status a web server that expects TLS answers a plain request with
    private static final String TLS_EXPECTED = '400'
    private static final String STATUS_PATH = '/[A-Za-z0-9._~%!$&\'()*+,;=:@/-]{1,255}'

    /** The request line; {@code {path}} is the path asked. */
    static final String REQUEST_LINE = 'HEAD {path} HTTP/1.0'

    /** The header in which the startup progress of an application names the phase of its start. */
    static final String PHASE_HEADER = 'Grails-Startup-Phase'

    /** The header in which it names the path its page polls, which is asked so that a start that fails says so. */
    static final String STATUS_PATH_HEADER = 'Grails-Startup-Status-Path'

    /** The phase of an application that has started. */
    static final String READY_PHASE = 'READY'

    /** The phase of a start that has failed. */
    static final String FAILED_PHASE = 'FAILED'

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
     * Whether the application answers on the port, asked once: see {@link Start}.
     *
     * @param port the port
     * @return whether the web server of the application answers
     */
    static boolean isReady(int port) {
        return start(port).ask() == Started.READY
    }

    /**
     * @param port the port
     * @return what is asked, as often as it takes, how far the start of the application on the port is
     */
    static Start start(int port) {
        return new Start(port)
    }

    /** How far the start of an application is, as its port says. */
    enum Started {

        /** Nothing takes a connection, or what does answers no HTTP request. */
        NO_ANSWER,

        /** The server of the startup progress of the application has the port, and names a phase before the last. */
        STARTING,

        /** The web server of the application answers. */
        READY,

        /** The startup progress of the application says that the start has failed. */
        FAILED
    }

    /**
     * The start of an application on a port, as whoever waits for it asks: once and again, until the
     * answer is one to stop waiting on.
     *
     * <p>An application answers when it accepts a connection and answers a request with a response
     * of HTTP, whatever the status: a port that only accepts connections is no application that
     * answers. When the answer to plain HTTP is none, or the refusal of a server that expects TLS,
     * the request is made over TLS, with any certificate taken, as the self-signed one of
     * development is: the address is this computer's, and nothing is sent but the request line.</p>
     *
     * <p>An application with {@code grails-startup-progress} has a server answer on its port before
     * its own web server does. Every answer of that server names the phase of the start in the
     * header {@value #PHASE_HEADER}, and such an answer is none of the application yet. The
     * application has started when its own web server answers, which is an answer without a phase or
     * with the phase {@value #READY_PHASE}: that can be while {@code BootStrap} still runs, and
     * whether it does is not asked.</p>
     *
     * <p>A start that fails says so only to who asks as the progress page does: it holds the port
     * for a moment for a client that asked, a moment ago, the path the page polls, and lets go of it
     * at once otherwise. So while the server of startup progress answers, the path it names in
     * {@value #STATUS_PATH_HEADER} is asked as well, for nothing but that: the answer there counts
     * only when its phase is {@value #FAILED_PHASE}. An application without startup progress, and
     * the web server of one with it, are never asked for that path.</p>
     *
     * <p>Only the status line and the headers of an answer are read, of them only so much, and
     * nothing of them is shown: the phase is taken as one of three things, and a status path as a
     * path to ask on the same port, or not at all. The names and values are those of
     * {@code run-app-protocol.properties} in the sources of {@code grails-startup-progress}, which
     * the specifications of both sides read.</p>
     */
    static final class Start {

        private final int port
        private boolean reported
        // once an answer came over TLS, plain HTTP is not tried again
        private boolean tls

        private Start(int port) {
            this.port = port
        }

        /**
         * @return how far the start is now
         */
        Started ask() {
            for (byte[] address : LOOPBACK) {
                Started started = ask(new InetSocketAddress(InetAddress.getByAddress(address), port))
                if (started != null) {
                    return started
                }
            }
            return Started.NO_ANSWER
        }

        /**
         * @return whether the server of startup progress has answered, so that the port was not only taken
         */
        boolean isReported() {
            return reported
        }

        // null when nothing accepts a connection
        private Started ask(InetSocketAddress loopback) {
            Answer answer = request(loopback, tls, ROOT)
            if (answer == null) {
                return null
            }
            if (!tls && (!answer.http || answer.refused)) {
                Answer secure = request(loopback, true, ROOT)
                if (secure != null && secure.http) {
                    answer = secure
                    tls = true
                }
            }
            if (!answer.http) {
                return Started.NO_ANSWER
            }
            if (answer.phase == null || answer.phase == READY_PHASE) {
                // the web server of the application answers
                return Started.READY
            }
            reported = true
            if (answer.phase == FAILED_PHASE) {
                return Started.FAILED
            }
            if (answer.statusPath != null) {
                // who asked there a moment ago is who a start that fails holds the port for, to say so
                Answer status = request(loopback, tls, answer.statusPath)
                if (status != null && status.phase == FAILED_PHASE) {
                    return Started.FAILED
                }
            }
            return Started.STARTING
        }
    }

    /** What was read of an answer. */
    private static final class Answer {

        /** Whether it began as a response of HTTP does. */
        boolean http

        /** Whether its status is the one a server that expects TLS refuses a plain request with. */
        boolean refused

        /** The phase startup progress names, or {@code null}. */
        String phase

        /** The path startup progress names, when it is a path that can be asked. */
        String statusPath
    }

    // null when nothing accepts a connection
    private static Answer request(InetSocketAddress loopback, boolean tls, String path) {
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
            String request = REQUEST_LINE.replace('{path}', path) + '\r\nHost: localhost:' + loopback.getPort() + '\r\n\r\n'
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1))
            socket.getOutputStream().flush()
            return answerOf(socket.getInputStream())
        } catch (IOException | GeneralSecurityException | RuntimeException ignored) {
            return new Answer()
        } finally {
            close(socket)
        }
    }

    // what the status line and the headers of an answer say, of which only so much is read
    private static Answer answerOf(InputStream stream) throws IOException {
        Answer answer = new Answer()
        StringBuilder line = new StringBuilder()
        boolean first = true
        for (int read = 0; read < MAX_ANSWER; read++) {
            int next = stream.read()
            if (next != -1 && next != (int) ('\n' as char)) {
                line.append((char) next)
                continue
            }
            String header = line.toString().trim()
            line.setLength(0)
            if (first) {
                if (!(header ==~ /HTTP\/[0-9.]{1,5} [0-9]{3}( .*)?/)) {
                    return answer
                }
                answer.http = true
                answer.refused = header.split(' ')[1] == TLS_EXPECTED
                first = false
            }
            int colon = header.indexOf(':')
            String name = colon > 0 ? header.substring(0, colon).trim() : ''
            String value = colon > 0 ? header.substring(colon + 1).trim() : ''
            if (name.equalsIgnoreCase(PHASE_HEADER) && answer.phase == null && value ==~ /[A-Z_]{1,40}/) {
                answer.phase = value
            }
            // a path of this port and nothing else: no address, no query, nothing that ends the request line
            if (name.equalsIgnoreCase(STATUS_PATH_HEADER) && answer.statusPath == null && value ==~ STATUS_PATH && !value.startsWith('//')) {
                answer.statusPath = value
            }
            if (next == -1 || header.isEmpty()) {
                break
            }
        }
        // a path is believed only of what names a phase
        answer.statusPath = answer.phase == null ? null : answer.statusPath
        return answer
    }

    private static void close(Socket socket) {
        try {
            socket.close()
        } catch (IOException ignored) {
            // nothing depends on it
        }
    }
}
