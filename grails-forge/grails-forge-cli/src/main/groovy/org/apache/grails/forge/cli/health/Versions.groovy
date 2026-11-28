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
package org.apache.grails.forge.cli.health

import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic

/**
 * Versions as Grails, Gradle and SDKMAN! write them: a release of up to three numbers, and what
 * follows it after a hyphen (a milestone, a release candidate, a snapshot, the vendor of a JDK).
 *
 * @since 9.0
 */
@CompileStatic
final class Versions {

    private static final Pattern RELEASE = Pattern.compile(/^(\d{1,4})(?:\.(\d{1,4}))?(?:\.(\d{1,4}))?([-.+].*)?$/)

    private Versions() {
    }

    /**
     * @return the major version, or {@code null} for what is no version
     */
    static Integer major(String version) {
        Matcher matched = version == null ? null : RELEASE.matcher(version)
        return matched != null && matched.matches() ? Integer.valueOf(matched.group(1)) : null
    }

    /**
     * Compares two versions by their releases; of two versions of one release, the one with something
     * after it (a milestone, a release candidate, a snapshot) is the earlier.
     *
     * @return less than 0 when the first is the earlier, more than 0 when it is the later, 0 when they
     * are of one release and neither or both have something after it; {@code null} when one of them
     * is no version
     */
    static Integer compare(String first, String second) {
        Matcher one = first == null ? null : RELEASE.matcher(first)
        Matcher other = second == null ? null : RELEASE.matcher(second)
        if (one == null || other == null || !one.matches() || !other.matches()) {
            return null
        }
        for (int group = 1; group <= 3; group++) {
            int compared = Integer.compare(Integer.parseInt(one.group(group) ?: '0'), Integer.parseInt(other.group(group) ?: '0'))
            if (compared != 0) {
                return compared
            }
        }
        boolean oneQualified = one.group(4) != null && one.group(4).startsWith('-')
        boolean otherQualified = other.group(4) != null && other.group(4).startsWith('-')
        return oneQualified == otherQualified ? 0 : oneQualified ? -1 : 1
    }
}
