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

import spock.lang.PendingFeatureIf
import spock.lang.Specification
import spock.lang.Unroll
import uk.org.webcompere.systemstubs.SystemStubs

class GrailsVersionSpec extends Specification {

    @PendingFeatureIf(
            reason = 'groovy.lang.MissingMethodException: No signature of method: MockImpl for class: grails.init.GrailsVersionSpec is applicable for argument types: (String, Class, Class) values: [mockVersion, class grails.init.GrailsWrapper, class grails.init.GrailsWrapper]',
            value = { GroovySystem.version.startsWith('5') }
    )
    def "allowed release types - specified valid"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper)

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_ALLOWED_TYPES', 'SNAPSHOT, RC').execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        noExceptionThrown()

        and:
        allowedTypes == [GrailsReleaseType.SNAPSHOT, GrailsReleaseType.RC] as LinkedHashSet

        and:
        0 * mockVersion._
    }

    @PendingFeatureIf(
            reason = 'groovy.lang.MissingMethodException: No signature of method: MockImpl for class: grails.init.GrailsVersionSpec is applicable for argument types: (String, Class, Class) values: [mockVersion, class grails.init.GrailsWrapper, class grails.init.GrailsWrapper]',
            value = { GroovySystem.version.startsWith('5') }
    )
    def "allowed release types - specified invalid"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper)

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_ALLOWED_TYPES', 'SNAPSHOT, FOO').execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        def ie = thrown(IllegalStateException)
        ie.message == 'Invalid Value in GRAILS_WRAPPER_ALLOWED_TYPES: FOO'

        and:
        0 * mockVersion._
    }

    @PendingFeatureIf(
            reason = 'groovy.lang.MissingMethodException: No signature of method: MockImpl for class: grails.init.GrailsVersionSpec is applicable for argument types: (String, Class, Class) values: [mockVersion, class grails.init.GrailsWrapper, class grails.init.GrailsWrapper]',
            value = { GroovySystem.version.startsWith('5') }
    )
    def "allowed release types - with preferred version defined and allowed types override"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper)

        and:
        GrailsVersion preferredVersion = new GrailsVersion('7.0.0-RC1')

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_ALLOWED_TYPES', 'SNAPSHOT, RC').execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(preferredVersion, mockVersion)
        }

        then:
        noExceptionThrown()

        and: 'due to override, both are returned'
        allowedTypes == [GrailsReleaseType.SNAPSHOT, GrailsReleaseType.RC] as LinkedHashSet

        and:
        0 * mockVersion._
    }

    @PendingFeatureIf(
            reason = 'groovy.lang.MissingMethodException: No signature of method: MockImpl for class: grails.init.GrailsVersionSpec is applicable for argument types: (String, Class, Class) values: [mockVersion, class grails.init.GrailsWrapper, class grails.init.GrailsWrapper]',
            value = { GroovySystem.version.startsWith('5') }
    )
    def "allowed release types - with preferred version defined and no types override"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper)

        and:
        GrailsVersion preferredVersion = new GrailsVersion('7.0.0-M1')

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_ALLOWED_TYPES', null).execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(preferredVersion, mockVersion)
        }

        then:
        noExceptionThrown()

        and:
        allowedTypes == [GrailsReleaseType.RELEASE, GrailsReleaseType.RC, GrailsReleaseType.MILESTONE] as LinkedHashSet

        and:
        0 * mockVersion._
    }

    @PendingFeatureIf(
            reason = 'groovy.lang.MissingMethodException: No signature of method: MockImpl for class: grails.init.GrailsVersionSpec is applicable for argument types: (String, Class, Class) values: [mockVersion, class grails.init.GrailsWrapper, class grails.init.GrailsWrapper]',
            value = { GroovySystem.version.startsWith('5') }
    )
    def "allowed release types - no preferred version - development"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper) {
            1 * getVersion() >> '7.0.0-SNAPSHOT'
        }

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_ALLOWED_TYPES', null).execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        noExceptionThrown()

        and: 'only later versions since its not snapshot'
        allowedTypes == [GrailsReleaseType.RELEASE, GrailsReleaseType.RC, GrailsReleaseType.MILESTONE, GrailsReleaseType.SNAPSHOT] as LinkedHashSet
    }

    @PendingFeatureIf(
            reason = 'groovy.lang.MissingMethodException: No signature of method: MockImpl for class: grails.init.GrailsVersionSpec is applicable for argument types: (String, Class, Class) values: [mockVersion, class grails.init.GrailsWrapper, class grails.init.GrailsWrapper]',
            value = { GroovySystem.version.startsWith('5') }
    )
    def "allowed release types - no preferred version - non-development for non-release"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper) {
            1 * getVersion() >> '7.0.0-RC2'
        }

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_ALLOWED_TYPES', null).execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        noExceptionThrown()

        and: 'only later versions since its not snapshot'
        allowedTypes == [GrailsReleaseType.RELEASE, GrailsReleaseType.RC] as LinkedHashSet
    }

    @PendingFeatureIf(
            reason = 'groovy.lang.MissingMethodException: No signature of method: MockImpl for class: grails.init.GrailsVersionSpec is applicable for argument types: (String, Class, Class) values: [mockVersion, class grails.init.GrailsWrapper, class grails.init.GrailsWrapper]',
            value = { GroovySystem.version.startsWith('5') }
    )
    def "allowed release types - no preferred version - non-development for release"() {
        given:
        GrailsWrapper mockVersion = Mock(GrailsWrapper) {
            1 * getVersion() >> '7.0.0'
        }

        when:
        Set<GrailsReleaseType> allowedTypes = null
        SystemStubs.withEnvironmentVariable('GRAILS_WRAPPER_ALLOWED_TYPES', null).execute {
            allowedTypes = GrailsVersion.getAllowedReleaseTypes(null, mockVersion)
        }

        then:
        noExceptionThrown()

        and: 'only later versions since its not snapshot'
        allowedTypes == [GrailsReleaseType.RELEASE] as LinkedHashSet
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
