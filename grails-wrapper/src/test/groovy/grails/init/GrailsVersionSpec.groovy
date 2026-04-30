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
package grails.init

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import uk.org.webcompere.systemstubs.SystemStubs

import java.nio.file.Path

class GrailsVersionSpec extends Specification {

    @TempDir
    Path tempDir

    def "allowed release types - specified valid"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper)

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.GRAILS_WRAPPER_ALLOWED_TYPES_ENV, 'SNAPSHOT, RC').execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        noExceptionThrown()

        and:
        allowedTypes == [GrailsReleaseType.SNAPSHOT, GrailsReleaseType.RC] as LinkedHashSet

        and:
        0 * mockVersion._
    }

    def "allowed release types - specified invalid"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper)

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.GRAILS_WRAPPER_ALLOWED_TYPES_ENV, 'SNAPSHOT, FOO').execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        def ie = thrown(IllegalStateException)
        ie.message == "Invalid Value in ${GrailsVersion.GRAILS_WRAPPER_ALLOWED_TYPES_ENV}: FOO"

        and:
        0 * mockVersion._
    }

    def "allowed release types - with preferred version defined and allowed types override"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper)

        and:
        GrailsVersion preferredVersion = new GrailsVersion('7.0.0-RC1')

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.GRAILS_WRAPPER_ALLOWED_TYPES_ENV, 'SNAPSHOT, RC').execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(preferredVersion, mockVersion)
        }

        then:
        noExceptionThrown()

        and: 'due to override, both are returned'
        allowedTypes == [GrailsReleaseType.SNAPSHOT, GrailsReleaseType.RC] as LinkedHashSet

        and:
        0 * mockVersion._
    }

    def "allowed release types - with preferred version defined and no types override"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper)

        and:
        GrailsVersion preferredVersion = new GrailsVersion('7.0.0-M1')

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.GRAILS_WRAPPER_ALLOWED_TYPES_ENV, null).execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(preferredVersion, mockVersion)
        }

        then:
        noExceptionThrown()

        and:
        allowedTypes == [GrailsReleaseType.RELEASE, GrailsReleaseType.RC, GrailsReleaseType.MILESTONE] as LinkedHashSet

        and:
        0 * mockVersion._
    }

    def "allowed release types - no preferred version - development"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper) {
            1 * getVersion() >> '7.0.0-SNAPSHOT'
        }

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.GRAILS_WRAPPER_ALLOWED_TYPES_ENV, null).execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        noExceptionThrown()

        and: 'only later versions since its not snapshot'
        allowedTypes == [GrailsReleaseType.RELEASE, GrailsReleaseType.RC, GrailsReleaseType.MILESTONE, GrailsReleaseType.SNAPSHOT] as LinkedHashSet
    }

    def "allowed release types - no preferred version - non-development for non-release"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper) {
            1 * getVersion() >> '7.0.0-RC2'
        }

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.GRAILS_WRAPPER_ALLOWED_TYPES_ENV, null).execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        noExceptionThrown()

        and: 'only later versions since its not snapshot'
        allowedTypes == [GrailsReleaseType.RELEASE, GrailsReleaseType.RC] as LinkedHashSet
    }

    def "allowed release types - no preferred version - non-development for release"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper) {
            1 * getVersion() >> '7.0.0'
        }

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.GRAILS_WRAPPER_ALLOWED_TYPES_ENV, null).execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        noExceptionThrown()

        and: 'only later versions since its not snapshot'
        allowedTypes == [GrailsReleaseType.RELEASE] as LinkedHashSet
    }

    def "preferred version - gradle.properties with grailsVersion wins over env var"() {
        given:
        File gradleProperties = tempDir.resolve('gradle.properties').toFile()
        gradleProperties.text = 'grailsVersion=7.0.11-SNAPSHOT'

        when:
        GrailsVersion resolved = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.PREFERRED_GRAILS_VERSION_ENV, '8.0.0-SNAPSHOT').execute {
            resolved = GrailsVersion.getPreferredGrailsVersion(gradleProperties)
        }

        then:
        resolved == new GrailsVersion('7.0.11-SNAPSHOT')
    }

    def "preferred version - env var used when gradle.properties is missing"() {
        given:
        File missing = tempDir.resolve('does-not-exist.properties').toFile()

        when:
        GrailsVersion resolved = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.PREFERRED_GRAILS_VERSION_ENV, '7.0.11-SNAPSHOT').execute {
            resolved = GrailsVersion.getPreferredGrailsVersion(missing)
        }

        then:
        resolved == new GrailsVersion('7.0.11-SNAPSHOT')
    }

    def "preferred version - env var used when gradle.properties has no grailsVersion key"() {
        given:
        File gradleProperties = tempDir.resolve('gradle.properties').toFile()
        gradleProperties.text = 'projectVersion=7.0.11-SNAPSHOT'

        when:
        GrailsVersion resolved = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.PREFERRED_GRAILS_VERSION_ENV, '7.0.11-SNAPSHOT').execute {
            resolved = GrailsVersion.getPreferredGrailsVersion(gradleProperties)
        }

        then:
        resolved == new GrailsVersion('7.0.11-SNAPSHOT')
    }

    def "preferred version - env var trimmed and whitespace-only treated as unset"() {
        given:
        File missing = tempDir.resolve('does-not-exist.properties').toFile()

        when:
        GrailsVersion resolved = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.PREFERRED_GRAILS_VERSION_ENV, '   ').execute {
            resolved = GrailsVersion.getPreferredGrailsVersion(missing)
        }

        then:
        resolved == null
    }

    def "preferred version - returns null when neither gradle.properties nor env var is set"() {
        given:
        File missing = tempDir.resolve('does-not-exist.properties').toFile()

        when:
        GrailsVersion resolved = null
        SystemStubs.withEnvironmentVariable(GrailsVersion.PREFERRED_GRAILS_VERSION_ENV, null).execute {
            resolved = GrailsVersion.getPreferredGrailsVersion(missing)
        }

        then:
        resolved == null
    }

    @Unroll
    def "grails version #version"(String version, String major, String minor, String patch, GrailsReleaseType releaseType, Integer candidate) {
        when:
        GrailsVersion grailsVersion = new GrailsVersion(version)

        then:
        noExceptionThrown()
        grailsVersion.version == version
        grailsVersion.major == major as int
        grailsVersion.minor == minor as int
        grailsVersion.patch == patch as int
        grailsVersion.releaseType == releaseType
        grailsVersion.candidate == candidate as Integer

        where:
        version          | major | minor | patch | releaseType                 | candidate
        '7.0.0'          | '7'   | '0'   | '0'   | GrailsReleaseType.RELEASE   | null
        '7.0.1'          | '7'   | '0'   | '1'   | GrailsReleaseType.RELEASE   | null
        '7.2.0'          | '7'   | '2'   | '0'   | GrailsReleaseType.RELEASE   | null
        '7.0.0-SNAPSHOT' | '7'   | '0'   | '0'   | GrailsReleaseType.SNAPSHOT  | null
        '7.0.0-RC1'      | '7'   | '0'   | '0'   | GrailsReleaseType.RC        | 1
        '7.0.0-M2'       | '7'   | '0'   | '0'   | GrailsReleaseType.MILESTONE | 2
    }

    def "comparison checks"() {
        expect:
        new GrailsVersion('7.0.0') > new GrailsVersion('7.0.1')
        new GrailsVersion('7.0.1') < new GrailsVersion('7.0.0')
        new GrailsVersion('7.0.0') > new GrailsVersion('7.1.0')
        new GrailsVersion('8.0.0') < new GrailsVersion('7.0.0')
        new GrailsVersion('7.0.0') < new GrailsVersion('7.0.0-SNAPSHOT')
        new GrailsVersion('7.0.0') < new GrailsVersion('7.0.0-RC1')
        new GrailsVersion('7.0.0') < new GrailsVersion('7.0.0-M1')
        new GrailsVersion('7.0.0-RC1') < new GrailsVersion('7.0.0-M1')
        new GrailsVersion('7.0.0-RC2') < new GrailsVersion('7.0.0-RC1')
        new GrailsVersion('7.0.0-RC1') < new GrailsVersion('7.0.0-SNAPSHOT')
        new GrailsVersion('7.0.0-M2') < new GrailsVersion('7.0.0-M1')
        new GrailsVersion('7.0.0-M1') < new GrailsVersion('7.0.0-SNAPSHOT')
    }

    def "sorted"() {
        expect:
        [
                new GrailsVersion('8.0.0'),
                new GrailsVersion('8.0.0-RC1'),
                new GrailsVersion('8.0.0-M1'),
                new GrailsVersion('8.0.0-SNAPSHOT'),
                new GrailsVersion('7.1.1'),
                new GrailsVersion('7.1.1-RC1'),
                new GrailsVersion('7.1.1-M1'),
                new GrailsVersion('7.1.1-SNAPSHOT'),
                new GrailsVersion('7.1.0'),
                new GrailsVersion('7.1.0-RC1'),
                new GrailsVersion('7.1.0-M1'),
                new GrailsVersion('7.1.0-SNAPSHOT'),
                new GrailsVersion('7.0.1'),
                new GrailsVersion('7.0.1-RC1'),
                new GrailsVersion('7.0.1-M1'),
                new GrailsVersion('7.0.1-SNAPSHOT'),
                new GrailsVersion('7.0.0'),
                new GrailsVersion('7.0.0-RC1'),
                new GrailsVersion('7.0.0-M1'),
                new GrailsVersion('7.0.0-SNAPSHOT'),
        ]
                ==
                [
                        new GrailsVersion('7.0.0'),
                        new GrailsVersion('7.0.0-RC1'),
                        new GrailsVersion('7.0.0-M1'),
                        new GrailsVersion('7.0.0-SNAPSHOT'),
                        new GrailsVersion('7.1.1'),
                        new GrailsVersion('7.1.1-RC1'),
                        new GrailsVersion('7.1.1-M1'),
                        new GrailsVersion('7.1.1-SNAPSHOT'),
                        new GrailsVersion('8.0.0'),
                        new GrailsVersion('8.0.0-RC1'),
                        new GrailsVersion('7.1.0'),
                        new GrailsVersion('7.1.0-RC1'),
                        new GrailsVersion('7.1.0-M1'),
                        new GrailsVersion('7.1.0-SNAPSHOT'),
                        new GrailsVersion('8.0.0-M1'),
                        new GrailsVersion('8.0.0-SNAPSHOT'),
                        new GrailsVersion('7.0.1'),
                        new GrailsVersion('7.0.1-RC1'),
                        new GrailsVersion('7.0.1-M1'),
                        new GrailsVersion('7.0.1-SNAPSHOT'),
                ].sort()
    }
}
