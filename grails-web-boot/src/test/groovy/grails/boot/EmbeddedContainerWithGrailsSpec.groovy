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
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory
import org.springframework.context.annotation.Bean
import spock.lang.Specification

/**
 * Created by graemerocher on 28/05/14.
 */
class EmbeddedContainerWithGrailsSpec extends Specification {

    AnnotationConfigServletWebServerApplicationContext context

    void cleanup() {
        context.close()
    }

    void "Test that you can load Grails in an embedded server config"() {
        when:"An embedded server config is created"
            this.context = new AnnotationConfigServletWebServerApplicationContext(Application)

        then:"The context is valid"
            context != null
            new URL("http://localhost:${context.webServer.port}/foo/bar").text == 'hello world'
            new URL("http://localhost:${context.webServer.port}/foos").text == 'all foos'
    }

    @EnableAutoConfiguration
    static class Application extends GrailsAutoConfiguration {
        @Bean
        ConfigurableServletWebServerFactory webServerFactory() {
            new TomcatServletWebServerFactory(0)
        }
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
        "/foos"(controller:'foo', action:"list")
    }
}

