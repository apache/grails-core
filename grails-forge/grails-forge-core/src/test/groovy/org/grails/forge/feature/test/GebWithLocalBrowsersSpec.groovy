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
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class GebWithLocalBrowsersSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test dependencies'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['geb-with-local-browsers'])
        def buildGradle = output['build.gradle']

        expect:
        buildGradle.contains('integrationTestImplementation testFixtures("org.apache.grails:grails-geb")')
        buildGradle.contains('testImplementation "org.seleniumhq.selenium:selenium-api"')
        buildGradle.contains('testImplementation "org.seleniumhq.selenium:selenium-support"')
        buildGradle.contains('testImplementation "org.seleniumhq.selenium:selenium-remote-driver"')
        buildGradle.contains('testRuntimeOnly "org.seleniumhq.selenium:selenium-firefox-driver"')
    }

    void 'test GebConfig.groovy file is present'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['geb-with-local-browsers'])

        expect:
        output.containsKey('src/integration-test/resources/GebConfig.groovy')
    }

    @Unroll
    void 'test feature geb is not supported for #applicationType application'(ApplicationType applicationType) {
        when:
        generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS), ['geb-with-local-browsers'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The requested feature does not exist: geb-with-local-browsers'

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.REST_API]
    }

    void 'test the former feature name geb-with-webdriver-binaries is not available'() {
        when:
        generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['geb-with-webdriver-binaries'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The requested feature does not exist: geb-with-webdriver-binaries'
    }

    void 'test build does not apply the webdriver binaries gradle plugin'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['geb-with-local-browsers'])
        def buildGradle = output['build.gradle']

        expect:
        !buildGradle.contains('org.ysb33r.webdriver-binaries')
        !buildGradle.contains('webdriverBinaries')
    }

    void 'test geb.env system property is passed to the test tasks'() {
        given:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['geb-with-local-browsers'])
        def buildGradle = output['build.gradle']

        expect:
        buildGradle.contains("""\
            tasks.withType(Test).configureEach {
                useJUnitPlatform()
                def gebEnv = providers.systemProperty('geb.env')
                if (gebEnv.present) {
                    systemProperty('geb.env', gebEnv.get())
                }
            }""".stripIndent())
    }

    @Unroll
    void 'test functional spec extends GebSpec for #applicationType application'(ApplicationType applicationType) {
        given:
        def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS), ['geb-with-local-browsers'])
        def spec = output['src/integration-test/groovy/example/grails/FooSpec.groovy']

        expect:
        spec.contains('import geb.spock.GebSpec')
        spec.contains('class FooSpec extends GebSpec')
        !spec.contains('ContainerGebSpec')

        where:
        applicationType << [ApplicationType.WEB, ApplicationType.WEB_PLUGIN]
    }

    void 'test feature replaces the default geb-with-testcontainers feature'() {
        when:
        def features = getFeatures(['geb-with-local-browsers'])

        then:
        features.contains('geb-with-local-browsers')
        !features.contains('geb-with-testcontainers')
    }

    void 'test feature cannot be combined with geb-with-testcontainers'() {
        when:
        getFeatures(['geb-with-local-browsers', 'geb-with-testcontainers'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith('There can only be one of the following features selected:')
        e.message.contains('geb-with-local-browsers')
        e.message.contains('geb-with-testcontainers')
    }
}
