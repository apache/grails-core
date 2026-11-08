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
package org.grails.cli.profile.repository

import grails.util.Environment
import spock.lang.Specification
import spock.lang.Unroll

class MavenProfileRepositorySnapshotSpec extends Specification {

    @Unroll
    void 'version #version isSnapshotVersion == #expected'() {
        expect: 'only snapshot (or unknown) versions resolve against snapshot repositories'
        MavenProfileRepository.isSnapshotVersion(version) == expected

        where:
        version           || expected
        '8.0.0-SNAPSHOT'  || true
        null              || true
        ''                || true
        '8.0.0-M4'        || false
        '8.0.0'           || false
        '8.0.0-RC1'       || false
    }

    void 'the snapshot gate is derived from the running Grails version'() {
        expect:
        MavenProfileRepository.snapshotsEnabled() ==
                MavenProfileRepository.isSnapshotVersion(Environment.grailsVersion)
    }

    void 'both built-in repositories honour the snapshot gate'() {
        expect: 'a milestone/release CLI does not pay for snapshot metadata checks against the built-in repos'
        MavenProfileRepository.grailsRepo().snapshotsEnabled == MavenProfileRepository.snapshotsEnabled()
        MavenProfileRepository.apacheRepo().snapshotsEnabled == MavenProfileRepository.snapshotsEnabled()
    }
}
