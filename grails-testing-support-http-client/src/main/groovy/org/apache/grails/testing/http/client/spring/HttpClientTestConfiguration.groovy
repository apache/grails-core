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
package org.apache.grails.testing.http.client.spring

import groovy.transform.CompileStatic

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy

import grails.testing.spring.IntegrationTestAutoConfiguration
import org.apache.grails.testing.http.client.HttpClient

/**
 * Auto-configuration that registers the test HTTP client bean for integration tests.
 *
 * <p>The configuration is discovered through Grails integration-test auto-configuration
 * imports and creates a lazily initialized {@link HttpClient} targeting the embedded
 * test server.</p>
 *
 * @since 7.1
 */
@CompileStatic
@IntegrationTestAutoConfiguration
class HttpClientTestConfiguration {

    /**
     * Builds the test {@link HttpClient} base URL from the local server port and optional
     * servlet context path.
     *
     * <p>The context path is normalized so that:</p>
     * <ul>
     *   <li>blank values and {@code /} map to an empty suffix,</li>
     *   <li>non-empty values always start with {@code /}.</li>
     * </ul>
     *
     * @param localServerPort local embedded server port injected by Spring Boot tests
     * @param contextPath servlet context path (defaults to {@code /})
     * @return configured test HTTP client
     */
    @Lazy @Bean
    HttpClient http(
            @Value('${local.server.port}') int localServerPort,
            @Value('${server.servlet.context-path:/}') String contextPath
    ) {
        def path = contextPath?.trim()
        path = (!path || path == '/') ? '' : path.startsWith('/') ? path : "/$path"
        new HttpClient("http://localhost:$localServerPort$path")
    }
}
