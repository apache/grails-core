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
package grails.doc.dropdown

import spock.lang.Specification
import spock.lang.Unroll

class SoftwareVersionSpec extends Specification {

    @Unroll
    void "it can parse version: #semver"(String semver, SoftwareVersion expected) {
        when:
        expected.versionText = semver
        SoftwareVersion softwareVersion = SoftwareVersion.build(semver)

        then:
        softwareVersion == expected

        where:
        semver                  || expected
        "1.0.0"                 || new SoftwareVersion(major: 1, minor: 0, patch: 0)

        "1.0.0.M1"              || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("M1"))
        "1.0.0.RC1"             || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("RC1"))
        "1.0.0.BUILD-SNAPSHOT"  || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("BUILD-SNAPSHOT"))

        "1.0.0-M1"              || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("M1"))
        "1.0.0-RC1"             || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("RC1"))
        "1.0.0-BUILD-SNAPSHOT"  || new SoftwareVersion(major: 1, minor: 0, patch: 0, snapshot: new Snapshot("BUILD-SNAPSHOT"))

    }
}
