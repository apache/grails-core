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

package org.grails.forge.feature.spring

import org.grails.forge.BeanContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class SpringBootSpec extends BeanContextSpec implements CommandOutputFixture {

    @Unroll
    void "test spring boot starter dependencies for #applicationType application"() {
        when:
        def output = generate(
                applicationType,
                new Options(TestFramework.SPOCK)
        )
        final String build = output['build.gradle']

        then:
        build.contains("implementation \"org.springframework.boot:spring-boot-starter\"")
        build.contains("implementation \"org.springframework.boot:spring-boot-starter-actuator\"")
        build.contains("implementation \"org.springframework.boot:spring-boot-starter-logging\"")
        build.contains("implementation \"org.springframework.boot:spring-boot-starter-validation\"")
        build.contains("implementation \"org.springframework.boot:spring-boot-autoconfigure\"")

        where:
        applicationType << [ApplicationType.WEB, ApplicationType.REST_API, ApplicationType.WEB_PLUGIN]
    }

    void "test spring boot starter dependencies for PLUGIN application"() {
        when:
        def output = generate(
                ApplicationType.PLUGIN,
                new Options(TestFramework.SPOCK)
        )
        final String build = output['build.gradle']

        then:
        !build.contains("implementation \"org.springframework.boot:spring-boot-starter\"")
        !build.contains("implementation \"org.springframework.boot:spring-boot-starter-actuator\"")
        build.contains("implementation \"org.springframework.boot:spring-boot-starter-logging\"")
        build.contains("implementation \"org.springframework.boot:spring-boot-starter-validation\"")
        build.contains("implementation \"org.springframework.boot:spring-boot-autoconfigure\"")
        !build.contains("implementation \"org.springframework.boot:spring-boot-starter-tomcat\"")
    }
}
