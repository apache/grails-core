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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The versions of Grails, or of Forge, a feature pack says it is for.
 *
 * <p>A range is one version, {@code 9.0.0}, which is that version and no other, or one or more
 * intervals: {@code [9.0.0,10.0.0)} is 9.0.0 and later and before 10.0.0, {@code [9.1.0,)} is 9.1.0 and
 * later, {@code (,10.0.0)} is before 10.0.0, and {@code [9.0.0,9.1.0),[9.2.0,10.0.0)} is either. A
 * square bracket has the version at that end in the range, a round one has it out.</p>
 *
 * <p>The versions of a range are releases, up to four numbers with dots between them; a number left
 * out is zero, so {@code 9} is {@code 9.0.0}. A version that is compared with a range is taken for the
 * release it leads to: {@code 9.0.0-SNAPSHOT}, {@code 9.0.0-M2} and {@code 9.0.0-RC1} are in the range
 * when 9.0.0 is. A pack that is for a release is for what leads to it, so that it can be tried
 * before the release is there. A version that is not a release with nothing or a qualifier after it,
 * such as {@code 9.0.1a} or one of five numbers, is in no range.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackVersionRange {

    private static final String RELEASE = /\d{1,9}(?:\.\d{1,9}){0,3}/
    private static final Pattern ONE = Pattern.compile(RELEASE)
    private static final Pattern INTERVAL = Pattern.compile('([\\[(]) *(' + RELEASE + ')? *, *(' + RELEASE + ')? *([\\])])')
    private static final Pattern ONLY = Pattern.compile('\\[ *(' + RELEASE + ') *\\]')
    // after the release nothing, or what is no part of a number: 9.0.1a is not 9.0.0 with something after it
    private static final Pattern LEADS_TO = Pattern.compile('(' + RELEASE + ')(?:[-+].*|\\.[^0-9].*)?')
    private static final Pattern BETWEEN = Pattern.compile(' *, *')
    private static final int MAX_INTERVALS = 20

    private final String text
    private final List<Interval> intervals

    private PackVersionRange(String text, List<Interval> intervals) {
        this.text = text
        this.intervals = intervals
    }

    /**
     * @param text the range as a descriptor has it
     * @return the range
     * @throws IllegalArgumentException when the text is not a range, with what is wrong with it
     */
    static PackVersionRange parse(String text) {
        String range = text.trim()
        if (ONE.matcher(range).matches()) {
            List<Integer> only = numbers(range)
            return new PackVersionRange(range, [new Interval(only, true, only, true)])
        }
        List<Interval> intervals = []
        int at = 0
        while (at < range.length()) {
            if (intervals.size() == MAX_INTERVALS) {
                throw new IllegalArgumentException("has more than ${MAX_INTERVALS} intervals")
            }
            Matcher interval = INTERVAL.matcher(range).region(at, range.length())
            Matcher only = ONLY.matcher(range).region(at, range.length())
            if (interval.lookingAt()) {
                List<Integer> from = interval.group(2) == null ? null : numbers(interval.group(2))
                List<Integer> to = interval.group(3) == null ? null : numbers(interval.group(3))
                boolean fromIn = interval.group(1) == '['
                boolean toIn = interval.group(4) == ']'
                if (from == null && to == null) {
                    throw new IllegalArgumentException('has an interval with no version at either end')
                }
                if ((from == null && fromIn) || (to == null && toIn)) {
                    throw new IllegalArgumentException('has an end without a version that is closed with a square bracket; use a round one')
                }
                if (from != null && to != null && (compare(from, to) > 0 || (compare(from, to) == 0 && !(fromIn && toIn)))) {
                    throw new IllegalArgumentException("has the interval ${interval.group()}, which no version is in")
                }
                intervals << new Interval(from, fromIn, to, toIn)
                at = interval.end()
            } else if (only.lookingAt()) {
                List<Integer> version = numbers(only.group(1))
                intervals << new Interval(version, true, version, true)
                at = only.end()
            } else {
                throw new IllegalArgumentException('is not one release, such as 9.0.0, or intervals of releases, such as [9.0.0,10.0.0)')
            }
            Matcher comma = BETWEEN.matcher(range).region(at, range.length())
            if (at < range.length()) {
                if (!comma.lookingAt() || comma.end() == range.length()) {
                    throw new IllegalArgumentException('has intervals that are not separated by one comma each')
                }
                at = comma.end()
            }
        }
        if (intervals.isEmpty()) {
            throw new IllegalArgumentException('is empty')
        }
        return new PackVersionRange(range, intervals)
    }

    /**
     * @param version a version, which can be one that leads to a release, such as a snapshot
     * @return whether the release the version leads to is in the range; a version that does not start
     * with a release is in no range
     */
    boolean contains(String version) {
        Matcher release = LEADS_TO.matcher(version == null ? '' : version.trim())
        if (!release.matches()) {
            return false
        }
        List<Integer> numbers = numbers(release.group(1))
        return intervals.any { Interval each -> each.contains(numbers) }
    }

    @Override
    String toString() {
        return text
    }

    private static List<Integer> numbers(String release) {
        List<Integer> numbers = release.split(/\./).collect { String each -> Integer.valueOf(each) }
        while (numbers.size() < 4) {
            numbers << 0
        }
        return numbers
    }

    private static int compare(List<Integer> one, List<Integer> other) {
        for (int index = 0; index < 4; index++) {
            int difference = Integer.compare(one[index], other[index])
            if (difference != 0) {
                return difference
            }
        }
        return 0
    }

    private static final class Interval {

        private final List<Integer> from
        private final boolean fromIn
        private final List<Integer> to
        private final boolean toIn

        Interval(List<Integer> from, boolean fromIn, List<Integer> to, boolean toIn) {
            this.from = from
            this.fromIn = fromIn
            this.to = to
            this.toIn = toIn
        }

        boolean contains(List<Integer> version) {
            if (from != null) {
                int low = compare(version, from)
                if (low < 0 || (low == 0 && !fromIn)) {
                    return false
                }
            }
            if (to != null) {
                int high = compare(version, to)
                if (high > 0 || (high == 0 && !toIn)) {
                    return false
                }
            }
            return true
        }
    }
}
