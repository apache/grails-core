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

package org.grails.datastore.mapping.core.grailsversion

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

/**
 * A class to represent the snapshot version of Grails for comparison
 *
 * @author James Kleeh
 */
@CompileStatic
@EqualsAndHashCode(includes = ['text'])
class Snapshot implements Comparable<Snapshot> {

    private static final String SNAPSHOT = "SNAPSHOT"
    private static final String RC = "RC"
    private static final String MILESTONE = "M"

    final String text

    @Override
    String toString() {
        text
    }

    int getMilestoneVersion() {
        text.replace(MILESTONE, "").toInteger()
    }

    int getReleaseCandidateVersion() {
        text.replace(RC, "").toInteger()
    }

    boolean isBuildSnapshot() {
        text.endsWith(SNAPSHOT)
    }

    boolean isReleaseCandidate() {
        text.startsWith(RC)
    }

    boolean isMilestone() {
        text.startsWith(MILESTONE)
    }

    Snapshot(String text) {
        this.text = text
        if (!text.matches(/^(M|RC|Final)\d*$/) && !isBuildSnapshot()) {
            throw new IllegalArgumentException("GrailsVersion snapshot is not in the expected format")
        }
    }

    @Override
    int compareTo(Snapshot o) {

        if (this.buildSnapshot && !o.buildSnapshot) {
            return 1
        } else if (!this.buildSnapshot && o.buildSnapshot) {
            return -1
        } else if (this.buildSnapshot && o.buildSnapshot) {
            return 0
        }

        if (this.releaseCandidate && !o.releaseCandidate) {
            return 1
        } else if (!this.releaseCandidate && o.releaseCandidate) {
            return -1
        } else if (this.releaseCandidate && o.releaseCandidate) {
            return this.releaseCandidateVersion <=> o.releaseCandidateVersion
        }

        if (this.milestone && !o.milestone) {
            return 1
        } else if (!this.milestone && o.milestone) {
            return -1
        } else if (this.milestone && o.milestone) {
            return this.milestoneVersion <=> o.milestoneVersion
        }

        return 0
    }
}
