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
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Specification

@Slf4j
class GradleSpec extends Specification implements ProjectFixture, ContextFixture, CommandOutputFixture {

    private static final String MAVEN_CENTRAL = 'mavenCentral()'
    private static final String PLUGIN_PORTAL = 'gradlePluginPortal()'
    private static final String GRAILS_RESTRICTED = 'https://repo.grails.org/grails/restricted'

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
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String gradleProps = output["gradle.properties"]

        expect:
        gradleProps.contains("org.gradle.caching=true")
        gradleProps.contains("org.gradle.daemon=true")
        gradleProps.contains("org.gradle.parallel=true")
        gradleProps.contains("org.gradle.jvmargs=-Dfile.encoding=UTF-8 -Xmx1024M")
    }

    void "test build gradle"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String buildGradle = output["build.gradle"]

        expect:
        buildGradle.contains("eclipse")
        buildGradle.contains("idea")
        buildGradle.contains("war")
    }

    void "test settings.gradle"() {
        given:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ["gradle-settings-file"])
        final String settingsGradle = output["settings.gradle"]

        expect:
        settingsGradle.contains("rootProject.name")
    }

    void "test buildSrc/build.gradle"() {
        given:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ["gradle-build-src"])
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
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        String settingsGradle = output["settings.gradle"]

        expect:
        !settingsGradle
    }

    void "no buildSrc/build.gradle file is created without the 'gradle-build-src' feature"() {
        given:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        String buildSrcGradle = output["buildSrc/settings.gradle"]

        expect:
        !buildSrcGradle
    }

    void 'the build script resolves its plugins from the Gradle Plugin Portal before repo.grails.org'() {
        given:
        final output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))

        expect:
        repositoriesIn(output['build.gradle'], 'buildscript {') == [MAVEN_CENTRAL, PLUGIN_PORTAL, GRAILS_RESTRICTED]
    }

    void 'buildSrc resolves its plugins from the Gradle Plugin Portal before repo.grails.org'() {
        given:
        final output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['gradle-build-src'])

        expect:
        repositoriesIn(output['buildSrc/build.gradle']) == [MAVEN_CENTRAL, PLUGIN_PORTAL, GRAILS_RESTRICTED]
    }

    void 'the project repositories leave out the Gradle Plugin Portal'() {
        given:
        final output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))

        expect:
        repositoriesIn(output['build.gradle']) == [MAVEN_CENTRAL, GRAILS_RESTRICTED]
    }

    void 'repo.grails.org is asked only for releases in the #repositories'() {
        given:
        final output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), features)

        expect:
        mavenBlockOf(GRAILS_RESTRICTED, repositoriesBlock(output[file], enclosingBlock))*.trim() == [
                'maven {',
                "url = '${GRAILS_RESTRICTED}'",
                'mavenContent {',
                'releasesOnly()',
                '}',
                '}',
        ]

        where:
        repositories                | features             | file                    | enclosingBlock
        'build script repositories' | []                   | 'build.gradle'          | 'buildscript {'
        'buildSrc repositories'     | ['gradle-build-src'] | 'buildSrc/build.gradle' | null
        'project repositories'      | []                   | 'build.gradle'          | null
    }

    /**
     * The lines of a repositories block. Without an enclosing block, the top-level repositories block of the script.
     */
    private static List<String> repositoriesBlock(String script, String enclosingBlock = null) {
        final lines = script.readLines()
        final from = enclosingBlock ? lines.findIndexOf { it.trim() == enclosingBlock } : 0
        assert from >= 0, "No ${enclosingBlock} in:\n${script}"
        final start = lines.findIndexOf(from) { enclosingBlock ? it.trim() == 'repositories {' : it == 'repositories {' }
        assert start >= 0, "No repositories block in:\n${script}"
        final closing = lines[start].takeWhile { it == ' ' } + '}'
        lines[start..lines.findIndexOf(start) { it == closing }]
    }

    /**
     * The Maven Central, Gradle Plugin Portal and repo.grails.org entries of a repositories block, in order.
     */
    private static List<String> repositoriesIn(String script, String enclosingBlock = null) {
        repositoriesBlock(script, enclosingBlock).join('\n')
                .findAll(/mavenCentral\(\)|gradlePluginPortal\(\)|url = '([^']+)'/) { String match, String url -> url ?: match }
                .findAll { it in [MAVEN_CENTRAL, PLUGIN_PORTAL, GRAILS_RESTRICTED] }
    }

    /**
     * The lines of the maven block that declares a repository URL in a repositories block.
     */
    private static List<String> mavenBlockOf(String url, List<String> repositories) {
        final urlLine = repositories.findIndexOf { it.trim() == "url = '${url}'" }
        assert urlLine > 0, "No ${url} in:\n${repositories.join('\n')}"
        final opening = urlLine - 1
        final closing = repositories[opening].takeWhile { it == ' ' } + '}'
        repositories[opening..repositories.findIndexOf(opening) { it == closing }]
    }
}
