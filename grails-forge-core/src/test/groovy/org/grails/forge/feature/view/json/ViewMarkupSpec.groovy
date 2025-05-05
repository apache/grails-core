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

package org.grails.forge.feature.view.json

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.*
import spock.lang.Unroll

class ViewMarkupSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test views-json feature"() {
        when:
        final Features features = getFeatures(["views-markup"])

        then:
        features.contains("grails-web")
        features.contains("views-markup")
        !features.contains("views-json")
    }

    void "test dependencies are present for Gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["views-markup"])
                .render()

        then:
        template.contains("apply plugin: \"org.apache.grails.gradle.grails-web\"")
        template.contains("implementation \"org.apache.grails:grails-views-markup\"")
        !template.contains("apply plugin: \"org.apache.grails.gradle.grails-gson\"")
    }

    void "test default gml views are present"() {
        when:
        final def output = generate(ApplicationType.REST_API, new Options(TestFramework.SPOCK), ["views-markup"])

        then:
        output.containsKey("grails-app/views/application/index.gml")
        output.containsKey("grails-app/views/error.gml")
        output.containsKey("grails-app/views/notFound.gml")
        output.containsKey("grails-app/views/errors/_errors.gml")
        output.containsKey("grails-app/views/object/_object.gml")
    }

    @Unroll
    void "test views-markup gradle plugins and dependencies are present for #applicationType application"() {
        when:
        final def output = generate(applicationType, new Options(TestFramework.SPOCK), ["views-markup"])
        final String build = output['build.gradle']

        then:
        build.contains("apply plugin: \"org.apache.grails.gradle.grails-web\"")
        build.contains("implementation \"org.apache.grails:grails-views-markup\"")
        !build.contains("apply plugin: \"org.apache.grails.gradle.grails-gson\"")
        !build.contains("implementation \"org.apache.grails:grails-views-gson\"")
        !build.contains("implementation \"org.apache.grails:grails-testing-support-views-gson\"")

        where:
        applicationType << [ApplicationType.REST_API]
    }
}
