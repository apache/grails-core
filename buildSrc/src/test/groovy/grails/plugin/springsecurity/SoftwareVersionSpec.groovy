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
package grails.plugin.springsecurity

import spock.lang.Specification

class SoftwareVersionSpec extends Specification {

    // TODO: I'm not 100% sure how the SoftwareVersion class is supposed to work so this test is incomplete
    void "versions are parsed correctly"() {

        when: 'creating a SoftwareVersion'
            def version = SoftwareVersion.build(versionString)

        then: 'the version is parsed correctly'
            version.major == major
            version.minor == minor
            version.patch == patch
            version.isSnapshot() == isSnapshot
            version.stableVersion == stableVersion
            version.snapshotVersion == snapshotVersion

        where:
            versionString | major | minor | patch || isSnapshot | stableVersion | snapshotVersion
            '1.2.3'       | 1     | 2     | 3     || false      | '1.2.3'       | '1.2.4'
    }
}
