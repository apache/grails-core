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

import grails.util.Environment
import org.springframework.boot.WebApplicationType
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Verifies the application environment created by {@link GrailsApp} supports the same
 * relaxed property resolution as a plain Spring Boot application, so values resolved
 * directly through {@code environment.getProperty(name, Enum)} accept lenient formats
 * such as lowercase or hyphenated enum names (see issue #15818).
 */
@RestoreSystemProperties
class GrailsAppEnvironmentConversionSpec extends Specification {

    void "environment resolves relaxed enum property values"() {
        setup:
        System.setProperty(Environment.KEY, Environment.TEST.getName())
        System.setProperty('test.access.lowercase', 'unrestricted')
        System.setProperty('test.access.hyphenated', 'read-only')
        GrailsApp app = new GrailsApp(EnvironmentConversionTestConfiguration)
        app.webApplicationType = WebApplicationType.NONE

        when:
        ConfigurableApplicationContext context = app.run()

        then:
        context.environment.conversionService instanceof ApplicationConversionService
        context.environment.getProperty('test.access.lowercase', TestEndpointAccess) == TestEndpointAccess.UNRESTRICTED
        context.environment.getProperty('test.access.hyphenated', TestEndpointAccess) == TestEndpointAccess.READ_ONLY

        cleanup:
        context?.close()
    }

    static enum TestEndpointAccess {
        NONE,
        READ_ONLY,
        UNRESTRICTED
    }
}

@Configuration
class EnvironmentConversionTestConfiguration {
}
