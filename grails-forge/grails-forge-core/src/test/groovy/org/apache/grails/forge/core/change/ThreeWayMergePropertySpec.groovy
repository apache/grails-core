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
package org.apache.grails.forge.core.change

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import spock.lang.Shared
import spock.lang.Specification

/**
 * What holds of every merge, on files made from seeds ({@link ThreeWayMergeCases}): a few thousand
 * short ones of several seeds, most of them with lines that repeat and with empty lines, where it is
 * least clear which line a change is next to, and a few long ones that are lined up whole, for which
 * the merge takes the lines it goes by in the cheaper way.
 *
 * <p>The reference is what {@code git merge-file -p} (Git 2.x, its default settings) made of the same
 * cases, kept in {@code three-way-merge-reference.txt}: for each case, in order, {@code X} for a
 * conflict, {@code S} for a conflict in which the two sides have the same lines in every place and
 * that is those lines, or the first 16 digits of the SHA-256 of what it merged. The merge here is to
 * be no bolder than that one: a result only where that one has a result, and the same one, but for
 * {@code S}: where both sides have the same lines between two lines that can only be lined up one
 * way, those lines are the result here, also where Git, which lines the two sides up apart, has a
 * conflict of two equal halves. The merge may have a conflict where that one merges. The file was
 * written once, with Git; the spec does not need Git.</p>
 */
class ThreeWayMergePropertySpec extends Specification {

    @Shared
    List<List<byte[]>> cases = ThreeWayMergeCases.ofReference().collect { List<String> each -> each.collect { String text -> text.getBytes(StandardCharsets.ISO_8859_1) } }

    static final int CASES = ThreeWayMergeCases.SEEDS.size() * ThreeWayMergeCases.CASES + ThreeWayMergeCases.LONG_CASES

    @Shared
    List<String> reference = getClass().getResourceAsStream('/three-way-merge-reference.txt').readLines('UTF-8').findAll { !it.startsWith('#') && !it.isEmpty() }

    void "a file only one side changed is the file of that side, and the same change on both sides is that change"() {
        when:
        List<Integer> wrong = (0..<CASES).findAll { int index ->
            byte[] base = cases[index][0]
            byte[] current = cases[index][1]
            byte[] proposed = cases[index][2]
            !clean(ThreeWayMerge.of(base, current, base), current) || !clean(ThreeWayMerge.of(base, base, proposed), proposed) ||
                    !clean(ThreeWayMerge.of(base, current, current), current) || !clean(ThreeWayMerge.of(base, base, base), base)
        }

        then:
        wrong == []
    }

    void "the two sides can change places: there is a conflict for the one order when there is for the other, and otherwise one result"() {
        when:
        List<Integer> wrong = (0..<CASES).findAll { int index ->
            ThreeWayMerge one = ThreeWayMerge.of(cases[index][0], cases[index][1], cases[index][2])
            ThreeWayMerge other = ThreeWayMerge.of(cases[index][0], cases[index][2], cases[index][1])
            one.conflicts != other.conflicts || (one.merged != null && !Arrays.equals(one.merged, other.merged))
        }

        then:
        wrong == []
    }

    void "there is a result or there are conflicts, never both and never neither, and every conflict has its place"() {
        when:
        List<Integer> wrong = (0..<CASES).findAll { int index ->
            ThreeWayMerge merge = ThreeWayMerge.of(cases[index][0], cases[index][1], cases[index][2])
            (merge.merged == null) != (merge.conflicts > 0) || merge.tooLarge || merge.conflictingChanges.size() != merge.conflicts ||
                    merge.conflictingChanges.any { it.current.isEmpty() && it.proposed.isEmpty() && it.line < 1 }
        }

        then:
        wrong == []
    }

    void "a file whose lines end with CR LF is merged as the file with LF is, and keeps them"() {
        when:
        List<Integer> wrong = (0..<CASES).findAll { int index ->
            ThreeWayMerge plain = ThreeWayMerge.of(cases[index][0], cases[index][1], cases[index][2])
            ThreeWayMerge returned = ThreeWayMerge.of(cases[index][0], crlf(cases[index][1]), cases[index][2])
            plain.conflicts != returned.conflicts || (plain.merged != null && cases[index][1].length > 0 && new String(cases[index][1], StandardCharsets.ISO_8859_1).contains('\n') &&
                    !Arrays.equals(crlf(plain.merged), returned.merged))
        }

        then:
        wrong == []
    }

    void "every line is in a result as often as the three files account for"() {
        when: 'counted here, from the four files alone'
        List<String> wrong = []
        (0..<CASES).each { int index ->
            ThreeWayMerge merge = ThreeWayMerge.of(cases[index][0], cases[index][1], cases[index][2])
            if (merge.merged == null) {
                return
            }
            Map<String, Integer> inBase = counted(cases[index][0])
            Map<String, Integer> inCurrent = counted(cases[index][1])
            Map<String, Integer> inProposed = counted(cases[index][2])
            Map<String, Integer> inResult = counted(merge.merged)
            for (String line : inBase.keySet() + inCurrent.keySet() + inProposed.keySet() + inResult.keySet()) {
                int b = inBase.get(line) ?: 0
                int o = inCurrent.get(line) ?: 0
                int t = inProposed.get(line) ?: 0
                int m = inResult.get(line) ?: 0
                // as often as the base has it, with what each side added and removed of it; where both
                // made the same change in one place, that change once, which is between the two
                boolean accounted = merge.changedAlike == 0 ? m == o + t - b : m >= [o, t, o + t - b].min() && m <= [o, t, o + t - b].max()
                // a line all three have equally often is there as often; a line that is new is there
                // no more often than the side that has it most often has it
                if (!accounted || (b == o && o == t && m != b) || (b == 0 && m > Math.max(o, t))) {
                    wrong << "case ${index}: '${line}' base ${b}, project ${o}, generator ${t}, result ${m}".toString()
                }
            }
        }

        then:
        wrong == []
    }

    private static Map<String, Integer> counted(byte[] file) {
        if (file.length == 0) {
            return [:]
        }
        String text = new String(file, StandardCharsets.ISO_8859_1)
        return (text.endsWith('\n') ? text.substring(0, text.length() - 1) : text).split('\n', -1).toList().countBy { String line -> line }
    }

    void "the merge is never bolder than the reference"() {
        expect:
        reference.size() == CASES

        when:
        List<Integer> mergedWhereTheReferenceHasAConflict = []
        List<Integer> mergedToSomethingElse = []
        int merged = 0
        int sameOnBothSides = 0
        int conflictWhereTheReferenceMerges = 0
        (0..<CASES).each { int index ->
            ThreeWayMerge merge = ThreeWayMerge.of(cases[index][0], cases[index][1], cases[index][2])
            if (merge.conflicts == 0) {
                merged++
                if (reference[index] == 'X') {
                    mergedWhereTheReferenceHasAConflict << index
                } else if (reference[index].startsWith('S')) {
                    sameOnBothSides++
                    // but for the line feeds at its end, which the reference adds to a side that has none
                    int end = merge.merged.length
                    while (end > 0 && merge.merged[end - 1] == (byte) 10) {
                        end--
                    }
                    if (MessageDigest.getInstance('SHA-256').digest(Arrays.copyOf(merge.merged, end)).encodeHex().toString().substring(0, 16) != reference[index].substring(1)) {
                        mergedToSomethingElse << index
                    }
                } else if (MessageDigest.getInstance('SHA-256').digest(merge.merged).encodeHex().toString().substring(0, 16) != reference[index]) {
                    mergedToSomethingElse << index
                }
            } else if (reference[index] != 'X' && !reference[index].startsWith('S')) {
                conflictWhereTheReferenceMerges++
            }
        }

        then:
        mergedWhereTheReferenceHasAConflict == []
        mergedToSomethingElse == []

        and: 'most cases are merged, so the comparison is of something: the numbers are those of the day the reference was written'
        merged == MERGED
        conflictWhereTheReferenceMerges == MORE_CAREFUL
        sameOnBothSides == SAME_ON_BOTH_SIDES

        and: 'the long files among them, which are merged or not as the short ones are'
        (CASES - ThreeWayMergeCases.LONG_CASES..<CASES).count { ThreeWayMerge.of(cases[it][0], cases[it][1], cases[it][2]).conflicts == 0 } == LONG_MERGED
    }

    static final int MERGED = 4777
    static final int MORE_CAREFUL = 384
    static final int SAME_ON_BOTH_SIDES = 0
    static final int LONG_MERGED = 12

    private static boolean clean(ThreeWayMerge merge, byte[] expected) {
        return merge.conflicts == 0 && Arrays.equals(merge.merged, expected)
    }

    private static byte[] crlf(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1).replace('\n', '\r\n').getBytes(StandardCharsets.ISO_8859_1)
    }
}
