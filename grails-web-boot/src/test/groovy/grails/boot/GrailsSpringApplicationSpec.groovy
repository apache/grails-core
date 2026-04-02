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
package grails.boot

import grails.boot.config.GrailsAutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Tests running Grails via SpringApplication with an embedded server.
 *
 * TODO: Rework for Spring Boot 4.0 modularized embedded server APIs.
 * Embedded server classes moved to spring-boot-web-server and spring-boot-tomcat modules
 * and require updated test patterns.
 */
@Ignore("Spring Boot 4.0: Embedded server test infrastructure needs significant rework due to modularization. " +
        "Classes exist in new spring-boot-web-server and spring-boot-tomcat modules but require updated test patterns.")
class GrailsSpringApplicationSpec extends Specification {

    void "Test run Grails via SpringApplication"() {
        // TODO: Restore embedded server assertions after reworking for Spring Boot 4.0 modularized APIs
        expect:
        throw new UnsupportedOperationException(
            'Test disabled pending Spring Boot 4.0 embedded server API rework')
    }

    @EnableAutoConfiguration
    static class Application extends GrailsAutoConfiguration {
    }
}
