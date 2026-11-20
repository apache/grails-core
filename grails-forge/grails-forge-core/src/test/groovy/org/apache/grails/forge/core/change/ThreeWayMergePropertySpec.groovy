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
 * What holds of every merge, on a few thousand files made from a seed ({@link ThreeWayMergeCases}),
 * most of them with lines that repeat and with empty lines, where it is least clear which line a
 * change is next to.
 *
 * <p>The reference is what {@code git merge-file -p} (Git 2.x, its default settings) made of the same
 * cases, kept in {@code three-way-merge-reference.txt}: for each case, in order, {@code X} for a
 * conflict or the first 16 digits of the SHA-256 of what it merged. The merge here is to be no bolder
 * than that one: never a result where it has a conflict, and never another result. It may have a
 * conflict where that one merges. The file was written once, with Git; the spec does not need Git.</p>
 */
class ThreeWayMergePropertySpec extends Specification {

    static final long SEED = 20261120L
    static final int CASES = 4000

    @Shared
    List<List<byte[]>> cases = ThreeWayMergeCases.of(SEED, CASES).collect { List<String> each -> each.collect { String text -> text.getBytes(StandardCharsets.ISO_8859_1) } }

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

    void "there is a result or there are conflicts, never both and never neither, and every conflict has the lines of a side"() {
        when:
        List<Integer> wrong = (0..<CASES).findAll { int index ->
            ThreeWayMerge merge = ThreeWayMerge.of(cases[index][0], cases[index][1], cases[index][2])
            (merge.merged == null) != (merge.conflicts > 0) || merge.tooLarge || merge.conflictingChanges.size() != merge.conflicts ||
                    merge.conflictingChanges.any { it.current.isEmpty() && it.proposed.isEmpty() }
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

    void "the merge is never bolder than the reference"() {
        expect:
        reference.size() == CASES

        when:
        List<Integer> mergedWhereTheReferenceHasAConflict = []
        List<Integer> mergedToSomethingElse = []
        int merged = 0
        int conflictWhereTheReferenceMerges = 0
        (0..<CASES).each { int index ->
            ThreeWayMerge merge = ThreeWayMerge.of(cases[index][0], cases[index][1], cases[index][2])
            if (merge.conflicts == 0) {
                merged++
                if (reference[index] == 'X') {
                    mergedWhereTheReferenceHasAConflict << index
                } else if (MessageDigest.getInstance('SHA-256').digest(merge.merged).encodeHex().toString().substring(0, 16) != reference[index]) {
                    mergedToSomethingElse << index
                }
            } else if (reference[index] != 'X') {
                conflictWhereTheReferenceMerges++
            }
        }

        then:
        mergedWhereTheReferenceHasAConflict == []
        mergedToSomethingElse == []

        and: 'most cases are merged, so the comparison is of something: the numbers are those of the day the reference was written'
        merged == MERGED
        conflictWhereTheReferenceMerges == MORE_CAREFUL
    }

    static final int MERGED = 2545
    static final int MORE_CAREFUL = 249

    private static boolean clean(ThreeWayMerge merge, byte[] expected) {
        return merge.conflicts == 0 && Arrays.equals(merge.merged, expected)
    }

    private static byte[] crlf(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1).replace('\n', '\r\n').getBytes(StandardCharsets.ISO_8859_1)
    }
}
