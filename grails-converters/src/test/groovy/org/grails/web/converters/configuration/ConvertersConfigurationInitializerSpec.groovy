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
package org.grails.web.converters.configuration

import spock.lang.Specification

import grails.core.DefaultGrailsApplication
import org.grails.core.lifecycle.ShutdownOperations

class ConvertersConfigurationInitializerSpec extends Specification {

    PrintStream err

    ByteArrayOutputStream captured = new ByteArrayOutputStream()

    void setup() {
        // as after an application shuts down, so that the notice may be logged again
        ShutdownOperations.runOperations()
        // the test logger writes to System.err as it is when each line is logged
        err = System.err
        System.setErr(new PrintStream(captured, true))
    }

    void cleanup() {
        System.setErr(err)
        ConvertersConfigurationHolder.clear()
    }

    void "a warning is logged once when grails.converters.json.legacy is not set"() {
        when: 'converters are initialized twice, as they are for each application context in a JVM'
        initialize([:])
        initialize([:])

        then:
        warnings().size() == 1
        warnings()[0].contains('grails.converters.json.legacy is not set, so the JSON converter renders JSON as Grails 8 did.')
        ConvertersConfigurationHolder.isLegacyJson()
    }

    void "the warning is logged again once the application shuts down, as it does for a restart"() {
        when:
        initialize([:])
        ShutdownOperations.runOperations()
        initialize([:])

        then:
        warnings().size() == 2
    }

    void "no warning is logged when grails.converters.json.legacy is set to #legacy"() {
        when:
        initialize(['grails.converters.json.legacy': legacy])

        then:
        warnings().empty
        ConvertersConfigurationHolder.isLegacyJson() == legacy

        where:
        legacy << [true, false]
    }

    private static void initialize(Map<String, Object> settings) {
        def application = new DefaultGrailsApplication()
        settings.each { String key, Object value -> application.config.setAt(key, value) }
        new ConvertersConfigurationInitializer(grailsApplication: application).initialize()
    }

    private List<String> warnings() {
        captured.toString().readLines().findAll {
            it.contains('WARN') && it.contains(ConvertersConfigurationInitializer.SETTING_CONVERTERS_JSON_LEGACY)
        }
    }
}
