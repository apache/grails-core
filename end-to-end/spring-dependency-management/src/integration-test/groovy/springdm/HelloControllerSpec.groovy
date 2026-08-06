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
package springdm

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import spock.lang.Specification
import spock.lang.Tag

/**
 * Verifies that a Grails application whose dependency versions are managed by the legacy
 * {@code io.spring.dependency-management} Gradle plugin (rather than the Grails Gradle
 * plugin's native {@code platform(grails-bom)} injection, which is opted out of via
 * {@code grails { bom = null }}) still boots and serves a request. This is regression
 * coverage that the Spring Dependency Management plugin continues to work with Grails 8
 * when applied by hand, as an upgraded Grails 7 application would.
 */
@Integration
@Tag('http-client')
class HelloControllerSpec extends Specification implements HttpClientSupport {

    void 'the application boots with Spring Dependency Management and serves a request'() {
        when:
        def response = http('/hello')

        then:
        response.assertEquals(200, 'Hello from Spring Dependency Management')
    }
}
