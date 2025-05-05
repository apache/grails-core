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

package org.grails.forge.feature.test

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class GebWithTestcontainersSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test dependencies'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        def buildGradle = output['build.gradle']

        expect:
        buildGradle.contains('integrationTestImplementation testFixtures("org.apache.grails:grails-geb")')
    }

    @Unroll
    void 'test feature geb-with-testcontainers is not supported for #applicationType application'(ApplicationType applicationType) {
        when:
        generate(applicationType, new Options(TestFramework.SPOCK), ['geb-with-testcontainers'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The requested feature does not exist: geb-with-testcontainers'

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.REST_API]
    }
}
