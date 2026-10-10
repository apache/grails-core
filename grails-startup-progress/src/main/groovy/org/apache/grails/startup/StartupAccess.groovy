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
import java.security.MessageDigest
import java.security.SecureRandom

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Decides who sees the details of a start: the bean being created, the slowest beans, why a start failed,
 * and which Grails version is starting. They go only to a browser that has signed in with the token the
 * application logs, by opening the logged address once; everyone else sees how far the start has got.
 *
 * <p>Opening the address sets a cookie and sends the browser on to the same address without the token, so
 * the token leaves the address bar and the browser stays signed in while it polls and reloads. The token is
 * made once per JVM, so a page signed in stays signed in when Spring Boot DevTools restarts the
 * application.</p>
 */
@CompileStatic
final class StartupAccess {

    /** The query parameter that carries the token in the logged address. */
    @PackageScope
    static final String TOKEN_PARAMETER = 'grailsStartupToken'

    private static final String TOKEN = newToken()

    private final boolean showDetails

    private volatile String cookieName

    /**
     * @param showDetails whether details are shown at all, to a browser that has signed in
     * @param port the application's port, which names the cookie, since browsers share cookies between the
     *             ports of a host and two applications on one machine would otherwise sign each other out
     */
    @PackageScope
    StartupAccess(boolean showDetails, int port) {
        this.showDetails = showDetails
        usePort(port)
    }

    /**
     * Names the cookie for the port the web server listens on, which for a random port is only known once the
     * web server has started.
     */
    @PackageScope
    void usePort(int port) {
        if (port > 0) {
            cookieName = 'GRAILS_STARTUP_' + port
        }
        else if (cookieName == null) {
            cookieName = 'GRAILS_STARTUP'
        }
    }

    /** Whether a browser has to sign in to see details, which it only does when there are details to see. */
    @PackageScope
    boolean isSignInRequired() {
        return showDetails
    }

    /** The query that signs a browser in, for the address the application logs and opens a browser on. */
    @PackageScope
    String signInQuery() {
        return TOKEN_PARAMETER + '=' + TOKEN
    }

    /**
     * The address to send a browser on to when its request carries the token, which is the address it asked
     * for without the token, or {@code null} when the request does not sign in.
     */
    @PackageScope
    String signInRedirect(String rawPath, String rawQuery) {
        if (!showDetails || rawQuery == null || rawQuery.isEmpty()) {
            return null
        }
        boolean signedIn = false
        List<String> kept = new ArrayList<>()
        for (String parameter : rawQuery.split('&')) {
            if (parameter.startsWith(TOKEN_PARAMETER + '=')) {
                signedIn |= matches(parameter.substring(TOKEN_PARAMETER.length() + 1))
            }
            else if (!parameter.isEmpty()) {
                kept.add(parameter)
            }
        }
        if (!signedIn) {
            return null
        }
        return kept.isEmpty() ? rawPath : rawPath + '?' + String.join('&', kept)
    }

    /** The {@code Set-Cookie} header value that keeps a browser signed in. */
    @PackageScope
    String signInCookie() {
        return cookieName + '=' + TOKEN + '; Path=/; HttpOnly; SameSite=Strict'
    }

    /** Whether a request, by the {@code Cookie} headers it sent, may see details. */
    @PackageScope
    boolean showsDetails(List<String> cookieHeaders) {
        if (!showDetails || cookieHeaders == null) {
            return false
        }
        for (String header : cookieHeaders) {
            for (String cookie : header.split(';')) {
                String trimmed = cookie.trim()
                if (trimmed.startsWith(cookieName + '=') && matches(trimmed.substring(cookieName.length() + 1))) {
                    return true
                }
            }
        }
        return false
    }

    private static boolean matches(String candidate) {
        return MessageDigest.isEqual(TOKEN.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII))
    }

    private static String newToken() {
        byte[] bytes = new byte[32]
        new SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
