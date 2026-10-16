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
package org.apache.grails.startup

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Decides how the startup progress answers a request, and answers it, for both of the servers that answer
 * for it: the one that holds the application's port until the web server starts, and the filter inside the
 * application once the web server has the port. Each hands its requests over as an {@link Exchange}.
 *
 * <p>The two differ only in which requests they take. The server that holds the port before the web server
 * starts takes every request, since nothing else could answer one. The filter takes the startup progress's
 * own paths and, while the application starts and the progress page is served, browser page loads; every
 * other request, such as an API call or a request the application makes to itself from {@code BootStrap},
 * reaches the application as it always has.</p>
 *
 * <p>A request taken is answered the same way by either:</p>
 * <ul>
 *     <li>at the status path, with the status the progress page polls for, or once the application is ready,
 *     with only that it is ready</li>
 *     <li>at the report path, with the report as data to a client asking for JSON, and once the application is
 *     ready, with the report page</li>
 *     <li>when it carries the token from the log, by signing the browser in and sending it on to the address
 *     without the token</li>
 *     <li>otherwise, while the application starts, with the progress page for a browser and a plain
 *     {@code 503} for anything else</li>
 * </ul>
 */
@CompileStatic
final class StartupProgressResponder {

    /** Which requests a server takes, beyond the startup progress's own paths. */
    enum Takes {

        /** Every request, as the server that holds the port before the web server starts does. */
        EVERY_REQUEST,

        /** Browser page loads while the application starts, as the filter does when the progress page is served. */
        PAGE_LOADS,

        /** None, as the filter does when only the report is served. */
        OWN_PATHS_ONLY
    }

    /** A request and its response, as a server that answers for the startup progress hands them over. */
    interface Exchange {

        String method()

        /** The path asked for, undecoded, with the context path. */
        String path()

        /** The query, undecoded, or {@code null} when there is none. */
        String query()

        String header(String name)

        List<String> headers(String name)

        void setHeader(String name, String value)

        /**
         * Sends the status, headers and body, all of it before returning.
         *
         * @param contentType the type of the body, or {@code null} when there is no body
         */
        void send(int status, String contentType, byte[] body) throws IOException
    }

    private static final byte[] NO_BODY = new byte[0]

    private final StartupProgress progress

    private final StartupProgressPage page

    private final StartupAccess access

    private final String statusPath

    private final String reportPath

    /**
     * @param statusPath the full path the progress page polls
     * @param reportPath the full path of the startup report, or {@code null} when it is not served
     */
    @PackageScope
    StartupProgressResponder(StartupProgress progress, StartupProgressPage page, StartupAccess access, String statusPath, String reportPath) {
        this.progress = progress
        this.page = page
        this.access = access
        this.statusPath = statusPath
        this.reportPath = reportPath
    }

    /**
     * Answers the request if the server takes it, and says whether it did. A request left unanswered is the
     * application's.
     */
    @PackageScope
    boolean respond(Exchange exchange, Takes takes) throws IOException {
        String path = exchange.path()
        boolean starting = progress.isReporting()
        boolean status = path.equals(statusPath)
        boolean report = path.equals(reportPath)
        if (status && !starting) {
            // a page still polling once the application is ready is told so, rather than the poll reaching an
            // application that has no such path and logs every request it cannot map
            sendReady(exchange)
            return true
        }
        boolean everyRequest = takes == Takes.EVERY_REQUEST
        if ((everyRequest || starting || report) && signIn(exchange)) {
            return true
        }
        boolean pageLoad = isPageLoad(exchange)
        if (!everyRequest && !report && !(starting && (status || takes == Takes.PAGE_LOADS && pageLoad))) {
            return false
        }
        boolean showDetails = access.showsDetails(exchange.headers('Cookie'))
        boolean signInForDetails = access.isSignInRequired() && !showDetails
        if (status) {
            StartupProgress.Status current = progress.report(showDetails, signInForDetails)
            exchange.setHeader(StartupProgress.PHASE_HEADER, current.phase().name())
            send(exchange, 200, 'application/json', current.json())
            progress.delivered(current)
        }
        else if (report && StartupProgressPage.wantsJson(exchange.header('Accept'), exchange.query())) {
            send(exchange, 200, 'application/json', progress.snapshot(showDetails, signInForDetails).json())
        }
        else if (report && !starting) {
            setPageHeaders(exchange)
            send(exchange, 200, 'text/html', page.renderReport(progress.snapshot(showDetails, signInForDetails).json(), showDetails, signInForDetails))
        }
        else {
            sendStarting(exchange, pageLoad, showDetails, signInForDetails)
        }
        return true
    }

    /**
     * Whether the request is a browser loading a page, which browsers say with the fetch metadata headers
     * they send on every navigation. Browsers only send those to {@code localhost} and over HTTPS, so a
     * request without them is a page load when it asks to be upgraded to HTTPS, which browsers ask on every
     * navigation and nothing else does. A client that sends neither, such as an HTTP client in application
     * code, is never mistaken for one.
     */
    private static boolean isPageLoad(Exchange exchange) {
        String mode = exchange.header('Sec-Fetch-Mode')
        if (mode != null) {
            return 'navigate'.equals(mode) && 'document'.equals(exchange.header('Sec-Fetch-Dest'))
        }
        return '1'.equals(exchange.header('Upgrade-Insecure-Requests'))
    }

    /** Signs a browser in when its request carries the token from the log, and says whether it did. */
    private boolean signIn(Exchange exchange) throws IOException {
        String method = exchange.method()
        String signedIn = 'GET'.equals(method) || 'HEAD'.equals(method) ? access.signInRedirect(exchange.path(), exchange.query()) : null
        if (signedIn == null) {
            return false
        }
        exchange.setHeader('Cache-Control', 'no-store')
        exchange.setHeader('Referrer-Policy', 'no-referrer')
        exchange.setHeader('Set-Cookie', access.signInCookie())
        exchange.setHeader('Location', signedIn)
        exchange.send(302, null, NO_BODY)
        return true
    }

    private static void sendReady(Exchange exchange) throws IOException {
        exchange.setHeader(StartupProgress.PHASE_HEADER, StartupProgress.Phase.READY.name())
        send(exchange, 200, 'application/json', '{"phase":"READY"}')
    }

    /**
     * Turns a request away while the application starts: a browser gets the progress page, which reloads the
     * address once the application is ready, and anything else a plain answer to try again.
     */
    private void sendStarting(Exchange exchange, boolean pageLoad, boolean showDetails, boolean signInForDetails) throws IOException {
        exchange.setHeader(StartupProgress.PHASE_HEADER, progress.getPhase().name())
        exchange.setHeader('Retry-After', StartupProgressPage.RETRY_AFTER_SECONDS)
        String accept = exchange.header('Accept')
        if (pageLoad || accept != null && accept.contains('text/html')) {
            setPageHeaders(exchange)
            send(exchange, 503, 'text/html', page.render(exchange.method(), showDetails, signInForDetails))
        }
        else {
            send(exchange, 503, 'text/plain', page.plainText())
        }
    }

    private static void setPageHeaders(Exchange exchange) {
        exchange.setHeader('Content-Security-Policy', StartupProgressPage.CONTENT_SECURITY_POLICY)
        exchange.setHeader('Referrer-Policy', 'no-referrer')
    }

    private static void send(Exchange exchange, int status, String contentType, String content) throws IOException {
        exchange.setHeader('Cache-Control', 'no-store')
        exchange.setHeader('X-Content-Type-Options', 'nosniff')
        exchange.send(status, contentType + ';charset=utf-8', content.getBytes(StandardCharsets.UTF_8))
    }
}
