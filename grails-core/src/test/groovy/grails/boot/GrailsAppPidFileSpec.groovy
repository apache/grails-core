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

import org.springframework.boot.context.ApplicationPidFileWriter
import org.springframework.context.annotation.Configuration
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Verifies that {@link GrailsApp} registers Spring Boot's {@link ApplicationPidFileWriter} only when
 * the CLI supplies a PID file path via the {@code grails.cli.pid.file} system property. This is the
 * producer side of the {@code run-app}/{@code stop-app} PID file contract: a forked development
 * application writes its PID so {@code stop-app} can terminate it, while a normally deployed
 * application (where the property is absent) is unaffected.
 */
@RestoreSystemProperties
class GrailsAppPidFileSpec extends Specification {

    private static final String CLI_PID_FILE_PROPERTY = 'grails.cli.pid.file'

    void "ApplicationPidFileWriter is registered when grails.cli.pid.file is set"() {
        given:
        System.setProperty(CLI_PID_FILE_PROPERTY, new File('build/run-app.pid').absolutePath)
        GrailsApp app = new GrailsApp(PidFileTestConfiguration)

        when:
        app.configureCliPidFileWriter()

        then:
        app.listeners.any { it instanceof ApplicationPidFileWriter }
    }

    void "ApplicationPidFileWriter is not registered when grails.cli.pid.file is absent"() {
        given:
        System.clearProperty(CLI_PID_FILE_PROPERTY)
        GrailsApp app = new GrailsApp(PidFileTestConfiguration)

        when:
        app.configureCliPidFileWriter()

        then:
        !app.listeners.any { it instanceof ApplicationPidFileWriter }
    }

    void "ApplicationPidFileWriter is not registered for a blank grails.cli.pid.file"() {
        given:
        System.setProperty(CLI_PID_FILE_PROPERTY, '')
        GrailsApp app = new GrailsApp(PidFileTestConfiguration)

        when:
        app.configureCliPidFileWriter()

        then:
        !app.listeners.any { it instanceof ApplicationPidFileWriter }
    }
}

@Configuration
class PidFileTestConfiguration {
}
