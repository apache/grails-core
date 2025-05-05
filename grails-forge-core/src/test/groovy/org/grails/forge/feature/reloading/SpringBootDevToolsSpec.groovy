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

package org.grails.forge.feature.reloading

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture

class SpringBootDevToolsSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test spring-boot-devtools feature"() {

        when:
        final Features features = getFeatures(["spring-boot-devtools"])

        then:
        features.contains("spring-boot-devtools")

    }

    void "test spring-boot-devtools dependency is present for #applicationType application type"() {

        when:
        def output = generate(applicationType, ["spring-boot-devtools"])
        def build = output["build.gradle"]

        then:
        build.contains("developmentOnly \"org.springframework.boot:spring-boot-devtools\"")

        where:
        applicationType << [ApplicationType.WEB, ApplicationType.REST_API]
    }

    void "test there can be only one of Reloading feature"() {
        when:
        getFeatures(["spring-boot-devtools", "jrebel"])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("There can only be one of the following features selected")
    }
}
