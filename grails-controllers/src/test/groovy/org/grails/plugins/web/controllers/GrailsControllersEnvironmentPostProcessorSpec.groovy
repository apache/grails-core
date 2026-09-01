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
package org.grails.plugins.web.controllers

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import spock.lang.Specification

class GrailsControllersEnvironmentPostProcessorSpec extends Specification {

    private final GrailsControllersEnvironmentPostProcessor processor =
            new GrailsControllersEnvironmentPostProcessor()

    void 'legacy multipart configuration fails before context creation and identifies configured keys'() {
        given:
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', [
                'grails.controllers.upload.maxFileSize': 20000000,
                'grails.controllers.upload.unused': true
        ]))

        when:
        processor.postProcessEnvironment(environment, null)

        then:
        def exception = thrown(IllegalStateException)
        exception.message ==
                "Configuration properties under 'grails.controllers.upload' are no longer supported. " +
                "Use Spring Boot's 'spring.servlet.multipart' configuration instead. For example, set " +
                "'spring.servlet.multipart.maxFileSize=200MB' and " +
                "'spring.servlet.multipart.maxRequestSize=200MB'. Found: [maxFileSize, unused]"
    }

    void 'an application without legacy multipart configuration passes the guard'() {
        expect:
        processor.postProcessEnvironment(new StandardEnvironment(), null)
    }
}
