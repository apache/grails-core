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
 * Asks whether an application answers on a port of this computer. Only loopback addresses are asked,
 * and no name is looked up: the one the application is bound to, where that is a loopback address, as
 * {@code 127.0.0.2} or {@code ::1} are; and {@code 127.0.0.1} and {@code ::1} where it is bound to
 * none that is given, to {@code localhost} or to the address that stands for all. Nothing is sent
 * anywhere else.
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
    private static final List<InetAddress> LOOPBACK = [InetAddress.getByAddress([127, 0, 0, 1] as byte[]),
                                                       InetAddress.getByAddress([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1] as byte[])]

    private ApplicationPort() {
    }

    /**
     * @param host the address an application is bound to, as it was given
     * @return whether an application bound to it answers on a loopback address: it is not given, is
     * {@code localhost}, or is the text of a loopback address or of the address that stands for all
     */
    static boolean isReachedOnLoopback(String host) {
        return !asked(host).isEmpty()
    }

    /**
     * @param host the address an application is bound to, as it was given
     * @return the one loopback address an application bound to it answers on, as {@code 127.0.0.2} for
     * {@code 127.0.0.2} or {@code ::ffff:127.0.0.2} and {@code ::1} for {@code [::1]}; {@code null} where
     * it answers on {@code 127.0.0.1} and {@code ::1} alike, as it does bound to no address that is
     * given, to {@code localhost} or to the address that stands for all, and where it answers on no
     * loopback address
     */
    static String boundLoopback(String host) {
        List<InetAddress> asked = asked(host)
        return asked.size() == 1 ? textOf(asked[0]) : null
    }

    /**
     * @param host the address an application is bound to, as it was given
     * @return how a client names the address it answers on: the one loopback address it is bound to, as
     * {@code 127.0.0.2} or {@code [::1]}, and {@code localhost} otherwise
     */
    static String urlHost(String host) {
        List<InetAddress> asked = asked(host)
        if (asked.size() != 1) {
            return 'localhost'
        }
        return asked[0] instanceof Inet6Address ? "[${textOf(asked[0])}]".toString() : textOf(asked[0])
    }

    /**
     * @param port the port
     * @return whether anything accepts a connection on the port, at {@code 127.0.0.1} or {@code ::1}
     */
    static boolean isOpen(int port) {
        return isOpen(port, null)
    }

    /**
     * @param port the port
     * @param host the address an application is bound to, as it was given; {@code null} for none
     * @return whether anything accepts a connection on the port where an application bound to that
     * address answers; {@code false} where that is no loopback address
     */
    static boolean isOpen(int port, String host) {
        for (InetAddress address : asked(host)) {
            Socket socket = new Socket()
            try {
                socket.connect(new InetSocketAddress(address, port), CONNECT_MILLIS)
                return true
            } catch (IOException ignored) {
                // nothing there
            } finally {
                close(socket)
            }
        }
        return false
    }

    // The loopback addresses an application bound to the address answers on, which are the ones asked:
    // only the text of an address is read, which asks no name server, and it is read as the application
    // reads it (Spring Boot, with InetAddress), so that an IPv4 address written as one of IPv6 is that
    // address of IPv4. None where it is bound to an address that is not of the loopback.
    private static List<InetAddress> asked(String host) {
        if (host == null || host.isEmpty() || host.equalsIgnoreCase('localhost')) {
            return LOOPBACK
        }
        if (!(host ==~ /[0-9.]{1,15}/) && !(host ==~ /\[?[0-9A-Fa-f:.]{2,45}]?/ && host.contains(':'))) {
            return []
        }
        try {
            InetAddress address = InetAddress.getByName(host)
            if (address.isAnyLocalAddress()) {
                return LOOPBACK
            }
            // the address alone, without the text it was read from
            return address.isLoopbackAddress() ? [InetAddress.getByAddress(address.getAddress())] : []
        } catch (UnknownHostException ignored) {
            return []
        }
    }

    // the one loopback address of IPv6 is ::1, which InetAddress writes out in full
    private static String textOf(InetAddress address) {
        return address instanceof Inet6Address ? '::1' : address.getHostAddress()
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
     * @return what is asked, as often as it takes, how far the start of the application on the port is,
     * at {@code 127.0.0.1} and {@code ::1}
     */
    static Start start(int port) {
        return start(port, null)
    }

    /**
     * @param port the port
     * @param host the address the application is bound to, as it was given; {@code null} for none
     * @return what is asked, as often as it takes, how far the start of the application on the port is,
     * where an application bound to that address answers
     */
    static Start start(int port, String host) {
        return new Start(port, asked(host))
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
        private final List<InetAddress> addresses
        private boolean reported
        // once an answer came over TLS, plain HTTP is not tried again
        private boolean tls

        private Start(int port, List<InetAddress> addresses) {
            this.port = port
            this.addresses = addresses
        }

        /**
         * @return how far the start is now
         */
        Started ask() {
            for (InetAddress address : addresses) {
                Started started = ask(new InetSocketAddress(address, port))
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
