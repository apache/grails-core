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
package org.grails.forge.feature.build.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.util.VersionInfo
import spock.lang.Unroll

/**
 * The two versions of Gradle the generator and the command line know without the network: the one
 * the wrapper of a generated application names, and the earliest one Grails is built with. Both are
 * written by the build from the gradle.properties of the root of the repository, so that the wrapper
 * of the build of Grails, the wrapper Forge generates and what the command line compares a project
 * with cannot drift apart.
 */
class GradleVersionsSpec extends ApplicationContextSpec implements CommandOutputFixture {

    // the checkout this build is in: the specs run in grails-forge/grails-forge-core
    private static final Path ROOT = Path.of('').toAbsolutePath().resolve('../..').normalize()

    @Unroll
    void "the wrapper of a generated #type names the Gradle of the build of Grails"() {
        when:
        String properties = generate(type, [])['gradle/wrapper/gradle-wrapper.properties']

        then:
        properties.readLines().contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-${VersionInfo.gradleVersion}-bin.zip".toString())

        where:
        type << ApplicationType.values()
    }

    void "the Gradle a generated application names is gradleToolingApiVersion, which the wrapper of Grails and .sdkmanrc name too"() {
        given:
        Properties root = properties(ROOT.resolve('gradle.properties'))
        Properties wrapper = properties(ROOT.resolve('gradle/wrapper/gradle-wrapper.properties'))
        Properties sdkman = properties(ROOT.resolve('.sdkmanrc'))

        expect:
        VersionInfo.gradleVersion == root.getProperty('gradleToolingApiVersion')
        wrapper.getProperty('distributionUrl') == "https://services.gradle.org/distributions/gradle-${VersionInfo.gradleVersion}-bin.zip".toString()
        sdkman.getProperty('gradle') == VersionInfo.gradleVersion
        VersionInfo.minimumGradleVersion == root.getProperty('gradleMinimumVersion')
    }

    void "the SHA-256 of the distribution a generated application names is gradleDistributionSha256Sum, beside gradleToolingApiVersion"() {
        given:
        Properties root = properties(ROOT.resolve('gradle.properties'))
        Properties wrapper = properties(ROOT.resolve('gradle/wrapper/gradle-wrapper.properties'))

        expect:
        VersionInfo.gradleDistributionSha256Sum == root.getProperty('gradleDistributionSha256Sum')

        and: 'the wrapper of Grails, which names the same version, names the same checksum where it names one'
        wrapper.getProperty('distributionSha256Sum') in [null, VersionInfo.gradleDistributionSha256Sum]
    }

    void "the earliest Gradle is a release of Gradle no later than the one applications are generated with"() {
        given:
        List<Integer> minimum = numbers(VersionInfo.minimumGradleVersion)
        List<Integer> generated = numbers(VersionInfo.gradleVersion)

        expect:
        VersionInfo.minimumGradleVersion ==~ /\d+\.\d+(\.\d+)?/
        (0..2).collect { int i -> minimum[i] <=> generated[i] }.find { it != 0 } != 1
    }

    void "the versions of Gradle are no versions of a dependency"() {
        expect:
        !VersionInfo.dependencyVersions.containsKey('gradle.version')
        !VersionInfo.dependencyVersions.containsKey('gradle.minimum')
        !VersionInfo.dependencyVersions.containsKey('gradle.sha256')
        VersionInfo.dependencyVersions.containsKey('grails.version')
    }

    private static Properties properties(Path file) {
        Properties properties = new Properties()
        Files.newBufferedReader(file).withCloseable { properties.load(it) }
        return properties
    }

    private static List<Integer> numbers(String version) {
        List<Integer> numbers = (version.split(/[.-]/).take(3) as List<String>).collect { String each -> each.isInteger() ? each.toInteger() : 0 }
        while (numbers.size() < 3) {
            numbers << 0
        }
        return numbers
    }
}
