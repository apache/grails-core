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

import groovy.transform.CompileStatic

/**
 * Shared naming for the CAS test setup.
 *
 * <p>The app is reachable under two different host names depending on who is doing the reaching:
 * the test client and the app itself use {@code localhost}, while the CAS server runs in a
 * container and must call back into the host via {@code host.testcontainers.internal}. Anything
 * CAS is told to call (the service URL it redirects to and later posts single-logout requests to,
 * and the proxy callback URL) therefore uses the container-visible host name.</p>
 */
@CompileStatic
class CasTestConfig {

    /** Host name a container uses to reach the Docker host. */
    static final String CONTAINER_VISIBLE_HOST = 'host.testcontainers.internal'

    /** Matches the CAS plugin default {@code filterProcessesUrl}. */
    static final String CAS_FILTER_PROCESSES_URL = '/login/cas'

    static final String PROXY_RECEPTOR_URL = '/secure/receptor'

    /** Runs the app with {@code proxyCallbackUrl} / {@code proxyReceptorUrl} configured. */
    static final String PROXY_TEST_CONFIG = 'casProxy'

    /** Runs the app with both proxy settings left unset. */
    static final String DEFAULT_TEST_CONFIG = 'cas'

    /** Runs the app without opting in to single signout, to assert the shipped default. */
    static final String NO_SINGLE_SIGNOUT_TEST_CONFIG = 'casNoSingleSignout'

    static final List<String> TEST_CONFIGS =
            [DEFAULT_TEST_CONFIG, PROXY_TEST_CONFIG, NO_SINGLE_SIGNOUT_TEST_CONFIG].asImmutable()

    static String getTestConfig() {
        System.getProperty('TESTCONFIG')
    }

    /**
     * Whether a CAS configuration was selected. The specs run only when one was, so that a build
     * covering every Spring Security example does not repeat a configuration that the dedicated
     * per-configuration job already runs. {@code bootRun} does not need one.
     */
    static boolean isConfigured() {
        testConfig in TEST_CONFIGS
    }

    static boolean isProxyEnabled() {
        testConfig == PROXY_TEST_CONFIG
    }

    static boolean isSingleSignoutEnabled() {
        testConfig != NO_SINGLE_SIGNOUT_TEST_CONFIG
    }

    static String serviceUrl(int port) {
        "http://${CONTAINER_VISIBLE_HOST}:${port}${CAS_FILTER_PROCESSES_URL}"
    }

    static String proxyCallbackUrl(int port) {
        "http://${CONTAINER_VISIBLE_HOST}:${port}${PROXY_RECEPTOR_URL}"
    }
}
