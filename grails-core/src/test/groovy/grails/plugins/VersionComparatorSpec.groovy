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
package grails.plugins

import spock.lang.Specification
import spock.lang.Unroll

class VersionComparatorSpec extends Specification {

    @Unroll
    def "should compare #version1 and #version2 and return #expectedResult"() {
        given:
        def comparator = new VersionComparator();

        when:
        int actualResult = comparator.compare(version1, version2)

        then:
        actualResult == expectedResult

        where:
        version1               | version2               || expectedResult
        "3.1.0"                | "4.0.1"                || -1
        "3.1.10"               | "4.0.1"                || -1
        "3.0.0.BUILD-SNAPSHOT" | "4.0"                  || -1
        "3.1.110"              | "4.0.1"                || -1
        "3.0.0.BUILD-SNAPSHOT" | "3.0.0.BUILD-SNAPSHOT" || 0
        "3.0.0"                | "3.0.0"                || 0
        "4.0.1"                | "3.1.110"              || 1
        "4.0.1"                | "3.0.0.BUILD-SNAPSHOT" || 1

        // A pre-release (milestone/rc/snapshot) is older than the final release of the same number
        "7.0.0-M1"             | "7.0.0"                || -1
        "7.0.0"                | "7.0.0-M1"             || 1
        "7.0.0-RC1"            | "7.0.0"                || -1
        "7.0.0-SNAPSHOT"       | "7.0.0"                || -1
        "7.0.0"                | "7.0.0-SNAPSHOT"       || 1

        // The numeric version is compared before the qualifier, so the patch number is never lost
        "7.0.5-M1"             | "7.0.0"                || 1
        "7.0.0"                | "7.0.5-M1"             || -1
        "7.0.1-M1"             | "7.0.0"                || 1
        "7.0.0-RC1"            | "6.9.9"                || 1
        "6.9.9"                | "7.0.0-RC1"            || -1

        // Milestones and release candidates are ordered by their number, numerically not lexically
        "7.0.0-M1"             | "7.0.0-M2"             || -1
        "7.0.0-M2"             | "7.0.0-M1"             || 1
        "7.0.0-RC1"            | "7.0.0-RC2"            || -1
        "7.0.0-RC10"           | "7.0.0-RC2"            || 1

        // Qualifier tiers: milestone < release candidate < snapshot < final
        "7.0.0-M2"             | "7.0.0-RC1"            || -1
        "7.0.0-RC1"            | "7.0.0-SNAPSHOT"       || -1
        "7.0.0-M9"             | "7.0.0-SNAPSHOT"       || -1

        // The dotted and hyphenated qualifier forms are equivalent, and matching is case insensitive
        "7.0.0.M1"             | "7.0.0-M1"             || 0
        "7.0.0.RC1"            | "7.0.0-RC1"            || 0
        "7.0.0.BUILD-SNAPSHOT" | "7.0.0-SNAPSHOT"       || 0
        "7.0.0-rc3"            | "7.0.0-RC3"            || 0

        // An unrecognised qualifier is treated as a final release, even when it has a trailing number
        "7.0.0-FOO2"           | "7.0.0"                || 0
        "7.0.0"                | "7.0.0-FOO2"           || 0
        "7.0.0-FOO"            | "7.0.0"                || 0
    }

    def "sorts mixed milestone, release candidate, snapshot and final versions from oldest to newest"() {
        given:
        def comparator = new VersionComparator()
        def versions = ["7.0.0", "7.0.0-M1", "7.0.0-RC2", "7.0.0-SNAPSHOT", "7.0.0-M2", "7.0.0-RC1", "6.9.9"]

        when:
        def sorted = versions.sort(false, comparator)

        then:
        sorted == ["6.9.9", "7.0.0-M1", "7.0.0-M2", "7.0.0-RC1", "7.0.0-RC2", "7.0.0-SNAPSHOT", "7.0.0"]
    }
}
