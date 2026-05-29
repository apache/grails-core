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

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

/**
 * A comparator that orders versions from oldest to newest, following the standard
 * {@link Comparator} contract (a negative result means the first version is older).
 *
 * <p>Versions are compared by their numeric components first (major, minor, patch, ...),
 * padding the shorter side with zeros so that {@code 7.0} and {@code 7.0.0} are equal.
 * When the numeric components are equal the version qualifier is used as a tie-breaker
 * following the same ordering as {@code org.grails.datastore.mapping.core.grailsversion.GrailsVersion}:</p>
 *
 * <pre>
 * 7.0.0-M1 &lt; 7.0.0-M2 &lt; 7.0.0-RC1 &lt; 7.0.0-RC2 &lt; 7.0.0-SNAPSHOT &lt; 7.0.0
 * </pre>
 *
 * <p>In other words a milestone is older than a release candidate, a release candidate is
 * older than a snapshot, and any qualified (pre-release) version is older than the final
 * release of the same number. Both the dotted ({@code 7.0.0.M1}) and hyphenated
 * ({@code 7.0.0-M1}) qualifier forms are treated as equivalent. Unrecognised qualifiers are
 * treated as a final release to preserve backwards compatible behaviour.</p>
 */
@CompileStatic
class VersionComparator implements Comparator<String> {

    private static final String SNAPSHOT = 'SNAPSHOT'
    private static final String RELEASE_CANDIDATE = 'RC'
    private static final String MILESTONE = 'M'

    static private final List<String> SNAPSHOT_SUFFIXES = ['-SNAPSHOT', '.BUILD-SNAPSHOT'].asImmutable()

    private static final Pattern DIGITS = ~/\d+/
    private static final Pattern NUMERIC_PREFIX = ~/(\d+)-(.+)/
    private static final Pattern TRAILING_DIGITS = ~/(\d+)$/

    private static final int TIER_FINAL = 4
    private static final int TIER_SNAPSHOT = 3
    private static final int TIER_RELEASE_CANDIDATE = 2
    private static final int TIER_MILESTONE = 1

    int compare(String o1, String o2) {
        String left = o1?.trim()
        String right = o2?.trim()

        if (left == '*') {
            return 1
        }
        if (right == '*') {
            return -1
        }

        ParsedVersion v1 = parse(left)
        ParsedVersion v2 = parse(right)

        int result = compareNumbers(v1.numbers, v2.numbers)
        if (result == 0) {
            result = compareQualifiers(v1.qualifier, v2.qualifier)
        }
        return result
    }

    boolean equals(obj) { false }

    /**
     * Removes any suffixes that indicate that the version is a kind of snapshot
     */
    @CompileStatic
    protected String deSnapshot(String version) {
        String suffix = SNAPSHOT_SUFFIXES.find { String it -> version?.endsWith(it) }
        if (suffix) {
            return version[0..-(suffix.size() + 1)]
        } else {
            return version
        }
    }

    @CompileStatic
    protected boolean isSnapshot(String version) {
        SNAPSHOT_SUFFIXES.any { String it -> version?.endsWith(it) }
    }

    /**
     * Splits a version into its leading numeric components and an optional trailing qualifier.
     * The first token that is not purely numeric ends the numeric section. A token of the form
     * {@code <digits>-<qualifier>} (for example {@code 0-RC1} from {@code 7.0.0-RC1}) contributes
     * its leading digits to the numeric section and the remainder becomes the qualifier.
     */
    private static ParsedVersion parse(String version) {
        List<Integer> numbers = []
        String qualifier = null
        if (version) {
            for (String token : version.split(/\./)) {
                if (DIGITS.matcher(token).matches()) {
                    numbers.add(Integer.parseInt(token))
                    continue
                }
                Matcher matcher = NUMERIC_PREFIX.matcher(token)
                if (matcher.matches()) {
                    numbers.add(Integer.parseInt(matcher.group(1)))
                    qualifier = normalizeQualifier(matcher.group(2))
                } else {
                    qualifier = normalizeQualifier(token)
                }
                break
            }
        }
        return new ParsedVersion(numbers, qualifier)
    }

    private static int compareNumbers(List<Integer> a, List<Integer> b) {
        int max = Math.max(a.size(), b.size())
        for (int i = 0; i < max; i++) {
            int left = i < a.size() ? a.get(i) : 0
            int right = i < b.size() ? b.get(i) : 0
            int result = Integer.compare(left, right)
            if (result != 0) {
                return result
            }
        }
        return 0
    }

    private static int compareQualifiers(String q1, String q2) {
        int tier1 = qualifierTier(q1)
        int tier2 = qualifierTier(q2)
        if (tier1 != tier2) {
            return Integer.compare(tier1, tier2)
        }
        if (tier1 == TIER_MILESTONE || tier1 == TIER_RELEASE_CANDIDATE) {
            return Integer.compare(qualifierNumber(q1), qualifierNumber(q2))
        }
        return 0
    }

    private static String normalizeQualifier(String qualifier) {
        if (qualifier == null) {
            return null
        }
        String upper = qualifier.toUpperCase()
        return upper.contains(SNAPSHOT) ? SNAPSHOT : upper
    }

    private static int qualifierTier(String qualifier) {
        if (qualifier == null) {
            return TIER_FINAL
        }
        if (qualifier.contains(SNAPSHOT)) {
            return TIER_SNAPSHOT
        }
        if (qualifier.startsWith(RELEASE_CANDIDATE)) {
            return TIER_RELEASE_CANDIDATE
        }
        if (qualifier.startsWith(MILESTONE)) {
            return TIER_MILESTONE
        }
        return TIER_FINAL
    }

    private static int qualifierNumber(String qualifier) {
        if (qualifier == null) {
            return 0
        }
        Matcher matcher = TRAILING_DIGITS.matcher(qualifier)
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0
    }

    @CompileStatic
    private static class ParsedVersion {

        final List<Integer> numbers
        final String qualifier

        ParsedVersion(List<Integer> numbers, String qualifier) {
            this.numbers = numbers
            this.qualifier = qualifier
        }
    }
}
