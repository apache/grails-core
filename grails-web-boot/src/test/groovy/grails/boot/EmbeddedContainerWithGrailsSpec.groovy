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

import grails.artefact.Artefact
import grails.boot.config.GrailsAutoConfiguration
import grails.web.Controller
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import spock.lang.PendingFeature
import spock.lang.Specification

/**
 * Tests loading Grails in an embedded server configuration.
 *
 * TODO: Rework for Spring Boot 4.0 modularized embedded server APIs.
 * Embedded server classes moved to spring-boot-web-server and spring-boot-tomcat modules
 * and require updated test patterns.
 */
class EmbeddedContainerWithGrailsSpec extends Specification {

    @PendingFeature(reason = "TODO: BOOT4 - Embedded server test infrastructure needs rework for Spring Boot 4.0 modularized APIs (spring-boot-web-server, spring-boot-tomcat)")
    void "Test that you can load Grails in an embedded server config"() {
        // TODO: Restore embedded server assertions after reworking for Spring Boot 4.0 modularized APIs
        expect:
        false
    }

    @EnableAutoConfiguration
    static class Application extends GrailsAutoConfiguration {
    }

}

@Controller
class FooController {

    def bar() {
        render "hello world"
    }

    def list() {
        render "all foos"
    }

    def closure = {}
}

@Artefact('UrlMappings')
class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"()
        "/foos"(controller: 'foo', action: "list")
    }
}
