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

package org.grails.forge.feature.logging

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.*
import spock.lang.Unroll

class LogbackSpec extends ApplicationContextSpec implements CommandOutputFixture {

    @Unroll
    void "test grails-logging dependency is present for #applicationType application"() {
        when:
        def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))

        then:
        output.containsKey("build.gradle")
        def build = output.get("build.gradle")
        build.contains("implementation \"org.apache.grails:grails-logging\"")

        where:
        applicationType << ApplicationType.values().toList()
    }

    @Unroll
    void "test no logback-spring.xml config file is generated for #applicationType application"() {
        when:
        def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))

        then:
        !output.containsKey("grails-app/conf/logback-spring.xml")

        where:
        applicationType << ApplicationType.values().toList()
    }

    void "test logback-config feature is visible so it can be searched and selected"() {
        when:
        def feature = beanContext.getBean(LogbackConfig)

        then:
        feature.name == "logback-config"
        feature.visible
    }

    @Unroll
    void "test logback-config feature generates logback-spring.xml composing Spring Boot defaults for #applicationType application"() {
        when:
        def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS), ["logback-config"])

        then: "the file reuses Spring Boot's own defaults and console appender instead of duplicating them"
        output.containsKey("grails-app/conf/logback-spring.xml")
        def logback = output.get("grails-app/conf/logback-spring.xml")
        logback.contains('<include resource="org/springframework/boot/logging/logback/defaults.xml"/>')
        logback.contains('<include resource="org/springframework/boot/logging/logback/console-appender.xml"/>')
        logback.contains('<root level="INFO">')
        !logback.contains('<root level="ERROR">')

        and: "environment-specific logging is configured via the development Spring profile"
        logback.contains('<springProfile name="development">')
        logback.contains('<logger name="StackTrace" level="ERROR"/>')

        and: "Jansi is not used; its global System.out replacement breaks console logging after a Spring Boot DevTools restart (issue #15663)"
        !logback.contains("withJansi")

        where:
        applicationType << ApplicationType.values().toList()
    }

    @Unroll
    void "test logback-config feature keeps the grails-logging dependency exactly once for #applicationType application"() {
        when:
        def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS), ["logback-config"])

        then:
        output.containsKey("build.gradle")
        def build = output.get("build.gradle")
        build.count("implementation \"org.apache.grails:grails-logging\"") == 1

        where:
        applicationType << ApplicationType.values().toList()
    }

    @Unroll
    void "test build.gradle does not inline bootRun console color config for #applicationType application"() {
        when:
        def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))

        then: "The Grails Gradle plugin configures bootRun console colors instead of generated end-user build.gradle files (issue #15663)"
        def build = output.get("build.gradle")
        !build.contains("tasks.matching { it.name == 'bootRun' }.configureEach {")
        !build.contains("spring.output.ansi.console-available")

        where:
        applicationType << ApplicationType.values().toList()
    }
}
