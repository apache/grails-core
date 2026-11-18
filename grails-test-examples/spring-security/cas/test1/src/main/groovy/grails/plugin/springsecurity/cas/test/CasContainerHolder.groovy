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
import groovy.util.logging.Slf4j
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

import java.time.Duration

/**
 * Starts a single Apereo CAS server in a container and shares it with every caller in the JVM.
 *
 * <p>The stock {@code apereo/cas} image only authorises {@code https} services, so a service
 * definition permitting {@code http} is copied in and the JSON service registry is initialised from
 * it. That definition also carries the proxy policy, which is what allows CAS to issue
 * proxy-granting tickets.</p>
 *
 * <p>Do not add {@code attributeReleasePolicy.authorizedToReleaseProxyGrantingTicket} to that
 * definition. Despite the name it is a different feature - releasing the ticket id as an encrypted
 * attribute - it needs a service public key, and without one CAS drops the
 * {@code <cas:proxyGrantingTicket>} element from the validation response entirely, which silently
 * breaks proxy authentication.</p>
 */
@Slf4j
@CompileStatic
class CasContainerHolder {

    static final String DEFAULT_CAS_VERSION = '7.3.6'
    static final String CONTEXT_PATH = '/cas'

    private static final int CAS_PORT = 8080
    private static final String SERVICE_DEFINITION = 'grailsTest-10000001.json'
    private static final String SERVICE_REGISTRY_DIR = '/etc/cas/services'

    private static GenericContainer container

    static synchronized GenericContainer getContainer() {
        if (container?.running) {
            return container
        }
        GenericContainer started = createContainer()
        started.start()
        started.followOutput(new Slf4jLogConsumer(log))
        container = started
        started
    }

    /**
     * The CAS base URL as seen from this JVM. Used for ticket validation and proxy retrieval, and
     * for the login redirect the test client follows.
     */
    static String getServerUrlPrefix() {
        GenericContainer running = getContainer()
        "http://${running.host}:${running.getMappedPort(CAS_PORT)}${CONTEXT_PATH}"
    }

    private static GenericContainer createContainer() {
        String version = System.getProperty('casContainerVersion') ?: DEFAULT_CAS_VERSION
        GenericContainer cas = new GenericContainer(DockerImageName.parse("apereo/cas:${version}"))
        cas.withExposedPorts(CAS_PORT)
        cas.withEnv(environment())
        cas.withCopyFileToContainer(
                MountableFile.forClasspathResource("cas/services/${SERVICE_DEFINITION}"),
                "${SERVICE_REGISTRY_DIR}/${SERVICE_DEFINITION}")
        // CAS has to reach back into the host for single logout and the proxy callback. 'host-gateway'
        // is resolved by the container runtime, so no port needs to be registered up front - the app
        // port is not known until the embedded server has bound.
        cas.withExtraHost(CasTestConfig.CONTAINER_VISIBLE_HOST, 'host-gateway')
        cas.waitingFor(Wait.forHttp("${CONTEXT_PATH}/login").forPort(CAS_PORT).forStatusCode(200))
        cas.withStartupTimeout(Duration.ofMinutes(5))
        cas
    }

    private static Map<String, String> environment() {
        [
                SERVER_SSL_ENABLED                    : 'false',
                SERVER_PORT                           : CAS_PORT.toString(),
                CAS_AUTHN_ACCEPT_ENABLED              : 'true',
                // Must match the users created in BootStrap, since the CAS principal is looked up in GORM
                CAS_AUTHN_ACCEPT_USERS                : 'user::user,admin::admin',
                // Permits the http proxy callback URL into the host
                CAS_HTTP_CLIENT_ALLOW_LOCAL_URLS      : 'true',
                CAS_SERVICE_REGISTRY_CORE_INIT_FROM_JSON: 'true',
                CAS_SERVICE_REGISTRY_JSON_LOCATION    : "file:${SERVICE_REGISTRY_DIR}".toString(),
                // The default of 10s is too tight for a test that boots an app between issue and validation
                CAS_TICKET_ST_TIME_TO_KILL_IN_SECONDS : '60'
        ]
    }
}
