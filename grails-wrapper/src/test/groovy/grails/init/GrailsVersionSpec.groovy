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
import spock.lang.Unroll

class GrailsVersionSpec extends Specification {

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
        new GrailsVersion('7.0.0') < new GrailsVersion('7.0.1')
        new GrailsVersion('7.0.1') > new GrailsVersion('7.0.0')
        new GrailsVersion('7.0.0') < new GrailsVersion('7.1.0')
        new GrailsVersion('8.0.0') > new GrailsVersion('7.0.0')
        new GrailsVersion('7.0.0') > new GrailsVersion('7.0.0-SNAPSHOT')
        new GrailsVersion('7.0.0') > new GrailsVersion('7.0.0-RC1')
        new GrailsVersion('7.0.0') > new GrailsVersion('7.0.0-M1')
        new GrailsVersion('7.0.0-RC1') > new GrailsVersion('7.0.0-M1')
        new GrailsVersion('7.0.0-RC2') > new GrailsVersion('7.0.0-RC1')
        new GrailsVersion('7.0.0-RC1') > new GrailsVersion('7.0.0-SNAPSHOT')
        new GrailsVersion('7.0.0-M2') > new GrailsVersion('7.0.0-M1')
        new GrailsVersion('7.0.0-M1') > new GrailsVersion('7.0.0-SNAPSHOT')
    }

    def "sorted"() {
        expect:
        [new GrailsVersion('7.0.0'), new GrailsVersion('7.0.0-RC1'), new GrailsVersion('7.0.0-M1'), new GrailsVersion('7.0.0-SNAPSHOT')].sort() == [
            new GrailsVersion('7.0.0-SNAPSHOT'),
            new GrailsVersion('7.0.0-M1'),
            new GrailsVersion('7.0.0-RC1'),
            new GrailsVersion('7.0.0')
        ]
    }
}
