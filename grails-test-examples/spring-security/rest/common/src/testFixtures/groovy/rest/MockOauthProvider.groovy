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

package rest

import groovy.transform.CompileStatic

import io.github.cjstehno.ersatz.GroovyErsatzServer

import org.springframework.test.context.DynamicPropertyRegistry

/**
 * The OAuth 2.0 provider of the Spring Security REST test applications: an Ersatz server that stands in for the
 * authorization, token and user-info endpoints of a real provider, so that no spec needs the network.
 *
 * <p>The server listens on a free port and is shared by every spec of a test JVM, because the application under test
 * is started once per JVM and reads the provider URLs when it starts. Each feature sets its own expectations, see
 * {@link #reset()}. {@link MockOauthProviderExtension} stops the server when the specs are done.</p>
 */
@CompileStatic
final class MockOauthProvider {

    /** The name of the provider in {@code grails.plugin.springsecurity.rest.oauth}, as used in {@code /oauth/authenticate/mock}. */
    static final String NAME = 'mock'

    static final String AUTHORIZATION_PATH = '/oauth2/authorize'
    static final String TOKEN_PATH = '/oauth2/token'
    static final String PROFILE_PATH = '/oauth2/userinfo'

    private static final String CONFIG_PREFIX = "grails.plugin.springsecurity.rest.oauth.${NAME}"

    private static GroovyErsatzServer server

    private MockOauthProvider() {
    }

    /**
     * @return the running server, started on first use
     */
    static synchronized GroovyErsatzServer getServer() {
        if (server == null) {
            GroovyErsatzServer started = new GroovyErsatzServer({ })
            started.start()
            server = started
        }
        server
    }

    /**
     * Points the provider named {@link #NAME} of the application under test at this server. What else the provider
     * needs (the pac4j client class, the client id and secret, the default roles) is in the configuration of the
     * application.
     */
    static void register(DynamicPropertyRegistry registry) {
        registry.add("${CONFIG_PREFIX}.authUrl".toString()) { getServer().httpUrl(AUTHORIZATION_PATH) }
        registry.add("${CONFIG_PREFIX}.tokenUrl".toString()) { getServer().httpUrl(TOKEN_PATH) }
        registry.add("${CONFIG_PREFIX}.profileUrl".toString()) { getServer().httpUrl(PROFILE_PATH) }
    }

    /**
     * Removes the expectations of the previous feature and returns the server.
     */
    static synchronized GroovyErsatzServer reset() {
        GroovyErsatzServer running = getServer()
        running.clearExpectations()
        running
    }

    static synchronized void stop() {
        server?.close()
        server = null
    }
}
