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

package grails.plugin.springsecurity.cas.test

import grails.testing.mixin.integration.Integration
import spock.lang.Requires
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Drives the CAS protocol by hand against the containerised CAS server.
 *
 * <p>The app and CAS are both reached on {@code localhost}, and cookies ignore ports, so a single
 * cookie jar would send the app's session cookie to CAS and vice versa. Each side therefore gets
 * its own client, and redirects are followed explicitly so the right one is used for each hop.</p>
 */
@Integration
@Requires({ isDockerAvailable() })
abstract class AbstractCasSpec extends Specification {

    HttpClient appClient
    HttpClient casClient

    /**
     * A cookie-less client for the calls CAS makes to the application on its own connection, such
     * as the back-channel logout request. Using the authenticated client instead would hide which
     * filter acted: an unconsumed POST to the CAS login path fails authentication and clears the
     * session by itself, which looks the same from outside as single signout working.
     */
    HttpClient backChannelClient

    /** The service ticket CAS issued during the most recent {@link #login} call. */
    String lastServiceTicket

    void setup() {
        appClient = newClient()
        casClient = newClient()
        backChannelClient = newClient()
    }

    /**
     * Mirrors the probe used by the hibernate7 specs. Checking the socket avoids the macOS failure
     * mode where asking Testcontainers for a client throws when the daemon API version differs.
     */
    static boolean isDockerAvailable() {
        List<String> candidates = [
                System.getProperty('user.home') + '/.docker/run/docker.sock',
                '/var/run/docker.sock',
                System.getenv('DOCKER_HOST') ?: ''
        ]
        candidates.any { it && new File(it).exists() }
    }

    String getAppBaseUrl() {
        "http://localhost:${serverPort}"
    }

    String getCasBaseUrl() {
        CasContainerHolder.serverUrlPrefix
    }

    /** CAS redirects to the container-visible host name; the test client has to use localhost. */
    static String toLocalUrl(String url) {
        url.replace(CasTestConfig.CONTAINER_VISIBLE_HOST, 'localhost')
    }

    HttpResponse<String> get(HttpClient client, String url) {
        client.send(HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString())
    }

    HttpResponse<String> postForm(HttpClient client, String url, Map<String, String> form) {
        String body = form.collect { k, v -> "${encode(k)}=${encode(v)}" }.join('&')
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header('Content-Type', 'application/x-www-form-urlencoded')
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    static String location(HttpResponse<?> response) {
        response.headers().firstValue('Location').orElse(null)
    }

    /**
     * Authenticates at CAS and returns the service ticket URL it redirects back to, already
     * rewritten to localhost.
     */
    String authenticateAtCas(String loginUrl, String username, String password) {
        HttpResponse<String> form = get(casClient, loginUrl)
        assert form.statusCode() == 200
        String execution = extractExecution(form.body())
        assert execution, 'CAS login form did not contain an execution token'

        HttpResponse<String> submitted = postForm(casClient, loginUrl,
                [username: username, password: password, execution: execution, _eventId: 'submit'])
        assert submitted.statusCode() == 302,
                "expected CAS to redirect after login but got ${submitted.statusCode()}"
        toLocalUrl(location(submitted))
    }

    /** Full login: hit a secured URL, authenticate at CAS, and follow the ticket back to the app. */
    HttpResponse<String> login(String securedPath, String username, String password) {
        HttpResponse<String> challenge = get(appClient, appBaseUrl + securedPath)
        assert challenge.statusCode() == 302,
                "expected a redirect to CAS but got ${challenge.statusCode()}"
        String ticketUrl = authenticateAtCas(location(challenge), username, password)
        lastServiceTicket = extractTicket(ticketUrl)
        assert lastServiceTicket, "CAS redirect carried no service ticket: ${ticketUrl}"
        followRedirects(get(appClient, ticketUrl))
    }

    HttpResponse<String> followRedirects(HttpResponse<String> response, int limit = 5) {
        HttpResponse<String> current = response
        for (int i = 0; i < limit && current.statusCode() in [301, 302, 303, 307, 308]; i++) {
            current = get(appClient, absolute(location(current)))
        }
        current
    }

    String absolute(String location) {
        String local = toLocalUrl(location)
        local.startsWith('http') ? local : appBaseUrl + local
    }

    /**
     * A container that accepts the connection but never answers would otherwise block
     * {@code send()} forever and hang the build until the CI runner kills the job.
     */
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60)

    /** The message CAS sends to a service on back-channel logout. */
    static String logoutRequest(String serviceTicket) {
        """<samlp:LogoutRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" \
ID="LR-1-${System.nanoTime()}" Version="2.0" IssueInstant="2026-01-01T00:00:00Z">\
<saml:NameID xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">@NOT_USED@</saml:NameID>\
<samlp:SessionIndex>${serviceTicket}</samlp:SessionIndex>\
</samlp:LogoutRequest>"""
    }

    static String extractTicket(String url) {
        def matcher = url =~ /[?&]ticket=([^&]+)/
        matcher.find() ? matcher.group(1) : null
    }

    static String extractExecution(String html) {
        def matcher = html =~ /name="execution"\s+value="([^"]+)"/
        matcher.find() ? matcher.group(1) : null
    }

    private static String encode(String value) {
        URLEncoder.encode(value, 'UTF-8')
    }

    private static HttpClient newClient() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL)
        HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(30))
                .build()
    }
}
