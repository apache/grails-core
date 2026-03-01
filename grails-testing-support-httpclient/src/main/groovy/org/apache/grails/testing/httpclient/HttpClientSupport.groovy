/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.testing.httpclient

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import org.junit.jupiter.api.AfterAll

import org.springframework.beans.factory.annotation.Value

/**
 * Test support trait that provides a lazily-created Micronaut {@link HttpClient} for HTTP-based tests.
 * <p>
 * The client is stored in a {@link ThreadLocal} so that tests can reuse it safely (for example, when
 * running in parallel across threads).
 * <p>
 * Typical usage in a Grails Spock integration specification (where {@code serverPort} is provided):
 * <pre>
 * import grails.testing.mixin.integration.Integration
 * import spock.lang.Specification
 *
 * @Integration
 * class MySpec extends Specification implements HttpClientSupport {
 *
 *     void 'health endpoint responds OK'() {
 *         when:
 *         def rsp = httpClient.toBlocking().exchange('/health')
 *
 *         then:
 *         rsp.status.code == 200
 *     }
 * }
 * </pre>
 * <p>
 * If your test doesn't provide a {@code serverPort} property, override {@code getHttpClientURL()} to
 * point the client at the desired server.
 */
@CompileStatic
trait HttpClientSupport {

    @Value('${local.server.port}')
    int assignedPort = -1

    /**
     * Holds the shared client for the current thread so it can be reused and
     * automatically closed after all tests have run.
     */
    private static final ThreadLocal<HttpClient> sharedClientHolder = new ThreadLocal<>()

    /**
     * Holds any clients created via {@code newHttpClient(HttpClientConfiguration, String)}
     * for the current thread so they can be automatically closed after the test.
     */
    private static final ThreadLocal<List<HttpClient>> customClientsHolder = new ThreadLocal<>()

    private HttpClient initClient(HttpClientConfiguration config = null, String url = null) {
        config ?
                HttpClient.create(new URL(url), config) :
                HttpClient.create(new URL(url))
    }

    /**
     * @return the shared (per-thread) client instance,
     *         as a blocking client, creating it on first access.
     */
    BlockingHttpClient getHttpClient() {
        asyncClient.toBlocking()
    }

    /**
     * @return the shared (per-thread) client instance
     */
    HttpClient getAsyncClient() {
        def client = sharedClientHolder.get()
        if (!client) {
            client = initClient(httpClientConfig, httpClientURL)
            sharedClientHolder.set(client)
        }
        client
    }

    /**
     * Creates a new client instance.
     * <p>
     * The returned client instance is not stored as the shared {@code httpClient}, but it is tracked
     * for the current thread and will be closed automatically by after the spec.
     * <p>
     * This is useful for tests that need an isolated client configuration.
     * <p>
     * If {@code config} is {@code null}, {@code getHttpClientConfig()} will be used.
     * If {@code url} is {@code null}, {@code getHttpClientURL()} will be used.
     */
    HttpClient newHttpClient(HttpClientConfiguration config = null, String url = null) {
        def client = initClient(
                config ?: null,
                url ?: httpClientURL
        )
        storeCustomClient(client)
        client
    }

    private void storeCustomClient(HttpClient client) {
        if (customClientsHolder.get() == null) {
            customClientsHolder.set([])
        }
        customClientsHolder.get().add(client)
    }

    /**
     * Override to provide custom Micronaut client configuration.
     *
     * @return the configuration to use when creating the shared client
     */
    HttpClientConfiguration getHttpClientConfig() {
        HttpClientConfigurationUtils.fromSystemProperties()
    }

    /**
     * Base URL used when creating the client.
     * <p>
     * By default this targets localhost and uses the {@code serverPort} property from the test.
     */
    String getHttpClientURL() {
        if (assignedPort == -1) {
            throw new IllegalStateException(
                    'No server port assigned. ' +
                    'Did you remember to annotate your test ' +
                    'class with @Integration or @SpringBootTest?'
            )
        }
        "http://localhost:$assignedPort"
    }

    /**
     * Closes any clients created by this support trait and clears internal {@link ThreadLocal}s.
     */
    @AfterAll
    @CompileDynamic
    static void cleanupClients() {
        sharedClientHolder.get()?.close()
        def customClients = customClientsHolder.get()
        customClients?.each {
            try {
                it.close()
            } catch (ignored) {

            }
        }
        sharedClientHolder.remove()
        customClientsHolder.remove()
    }
}
