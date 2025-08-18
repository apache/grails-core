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

package org.grails.forge.build.gradle

import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.fixture.ContextFixture
import org.grails.forge.fixture.ProjectFixture
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Specification

@Slf4j
class GradleSpec extends Specification implements ProjectFixture, ContextFixture, CommandOutputFixture {

    ApplicationContext beanContext

    void setup() {
        beanContext = ApplicationContext.run()
    }

    void cleanup() {
        beanContext.close()
        beanContext = null
    }

    void "test build properties"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final String gradleProps = output["gradle.properties"]

        expect:
        gradleProps.contains("org.gradle.caching=true")
        gradleProps.contains("org.gradle.daemon=true")
        gradleProps.contains("org.gradle.parallel=true")
        gradleProps.contains("org.gradle.jvmargs=-Dfile.encoding=UTF-8 -Xmx1024M")
    }

    void "test build gradle"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        final String buildGradle = output["build.gradle"]

        expect:
        buildGradle.contains("eclipse")
        buildGradle.contains("idea")
        buildGradle.contains("war")
    }

    void "test settings.gradle"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK), ["gradle-settings-file"])
        final String settingsGradle = output["settings.gradle"]

        expect:
        settingsGradle.contains("rootProject.name")
    }

    void "test buildSrc/build.gradle"() {
        given:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK), ["gradle-build-src"])
        String buildSrcGradle = output["buildSrc/build.gradle"]

        expect:
        if (!buildSrcGradle.contains('repositories')) {
            // this test randomly fails.  adding logging for when it fails to debug further
            log.error("Output: {}", output)
            log.error("Build Src: {}", buildSrcGradle)
        }
        buildSrcGradle.contains('repositories')
        buildSrcGradle.contains('dependencies')
    }

    void "no settings.gradle file is created without the 'gradle-settings-file' feature"() {
        given:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        String settingsGradle = output["settings.gradle"]

        expect:
        !settingsGradle
    }

    void "no buildSrc/build.gradle file is created without the 'gradle-build-src' feature"() {
        given:
        def output = generate(ApplicationType.WEB, new Options(TestFramework.SPOCK))
        String buildSrcGradle = output["buildSrc/settings.gradle"]

        expect:
        !buildSrcGradle
    }
}
