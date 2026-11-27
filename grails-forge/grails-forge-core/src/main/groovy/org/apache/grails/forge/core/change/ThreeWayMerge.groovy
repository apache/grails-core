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

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets

/**
 * The merge of a text file that was changed in two ways since a common ancestor: {@code base} is the
 * file as it was generated, {@code current} the file of the project, which whoever works on the
 * project may have edited, and {@code proposed} the file as it is generated now. The result is the
 * current file with the changes the generator made between the base and the proposed file.
 *
 * <p>The files are compared line by line, a line that ends with CR LF being the line that ends with
 * LF. Where lines repeat, two files can be lined up in more than one way, and nothing says which line
 * a change is next to. The merge therefore goes by the lines that can only be lined up one way, its
 * <em>anchors</em>: a line of the base that neither side changed and that every shortest list of
 * changes between the base and each side keeps, and keeps as the same line of that side. An empty
 * line or a closing brace is an anchor like any other line when nothing else can be made of it, and
 * is none where a line like it was added or removed beside it. The anchors cut the files into
 * stretches. A stretch only one side changed is taken as that side has it. A stretch both changed is
 * a conflict, unless the two have the very same lines in it, which are then the result: that is the
 * change of the generator made by hand. Two changes are merged only when an anchor is between them,
 * never by the accident of how lines that are alike were lined up.</p>
 *
 * <p>The lines of the current file are kept byte for byte, with the line endings they have. The lines
 * the generator adds end as the lines of the current file do when all of those end with CR LF, and as
 * they were generated otherwise.</p>
 *
 * <p>The work is bounded: a file of more than {@link #MAX_LINES} lines, and a file that differs from
 * its base in more than {@link #MAX_CHANGED} lines, is not merged, and is a conflict.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ThreeWayMerge {

    /** The most lines a file that is merged can have */
    public static final int MAX_LINES = 200_000

    /** The most lines one side can have added and removed in a file that is merged */
    public static final int MAX_CHANGED = 2_000

    // the most lines that are compared to line two files up, whatever they are
    private static final long MAX_STEPS = 50_000_000L

    // the most pairs of lines for which it is worked out which lines every way of lining two files up keeps
    private static final long MAX_PAIRS = 4_000_000L

    private final byte[] merged
    private final List<Conflict> conflicts
    private final boolean tooLarge
    private final int changedAlike

    private ThreeWayMerge(byte[] merged, List<Conflict> conflicts, boolean tooLarge, int changedAlike = 0) {
        this.merged = merged
        this.conflicts = conflicts
        this.tooLarge = tooLarge
        this.changedAlike = changedAlike
    }

    /**
     * @return in how many stretches both sides made the same change, which was taken once. A merge
     * that has such a stretch is one that a merge which lines the two sides up apart, as that of Git
     * does, can have a conflict for
     */
    int getChangedAlike() {
        return changedAlike
    }

    /**
     * @return the merged file, or {@code null} when there are conflicts or the files are not merged
     */
    byte[] getMerged() {
        return merged
    }

    /**
     * @return how many places the two changed in different ways; 1 for files that are not merged
     * because of their size
     */
    int getConflicts() {
        return tooLarge ? 1 : conflicts.size()
    }

    /**
     * @return whether the files were not merged because they have more lines, or more changes, than
     * are merged
     */
    boolean isTooLarge() {
        return tooLarge
    }

    /**
     * @return the places the two changed in different ways, in the order of the file
     */
    List<Conflict> getConflictingChanges() {
        return conflicts
    }

    /**
     * A place both changed, each in its way; or a line both have in another place than the base has it.
     */
    @CompileStatic
    static final class Conflict {

        /** The number of the first line of the place in the current file, from 1; where the current file has no lines there, the line after the place */
        final int line

        /** The lines the base has there, without what ends them; none where lines were only added */
        final List<String> base

        /** The lines the current file has there, without what ends them; none when it has none there */
        final List<String> current

        /** The lines the proposed file has there, without what ends them; none when it has none there */
        final List<String> proposed

        /**
         * Whether this is no place both changed, but a line ({@link #current}, and the same in
         * {@link #proposed}) that both sides moved or added, each to another place, so that the files
         * merged would have it more often, or less often, than any of the three has it
         */
        final boolean elsewhere

        Conflict(int line, List<String> base, List<String> current, List<String> proposed, boolean elsewhere = false) {
            this.line = line
            this.base = base
            this.current = current
            this.proposed = proposed
            this.elsewhere = elsewhere
        }
    }

    /**
     * @param first a text file
     * @param second a text file
     * @return whether the two are the same but for how their lines end
     */
    static boolean sameText(byte[] first, byte[] second) {
        if (Arrays.equals(first, second)) {
            return true
        }
        int one = 0
        int other = 0
        while (true) {
            // a carriage return before a line feed is passed over; one that stands alone is content
            if (one + 1 < first.length && first[one] == (byte) 13 && first[one + 1] == (byte) 10) {
                one++
            }
            if (other + 1 < second.length && second[other] == (byte) 13 && second[other + 1] == (byte) 10) {
                other++
            }
            if (one == first.length || other == second.length) {
                return one == first.length && other == second.length
            }
            if (first[one++] != second[other++]) {
                return false
            }
        }
    }

    /**
     * @return whether the difference between two files is within what is worked out: neither has
     * more than {@link #MAX_LINES} lines, and they differ in at most {@link #MAX_CHANGED}
     */
    static boolean isComparable(byte[] first, byte[] second) {
        if (lineCount(first) > MAX_LINES || lineCount(second) > MAX_LINES) {
            return false
        }
        Map<String, Integer> numbers = new HashMap<>()
        return script(keys(lines(first), numbers), keys(lines(second), numbers)) != null
    }

    /**
     * The shortest list of changes that makes one list of lines of another, within the bounds of the
     * merge.
     *
     * @return each change as the lines it replaces of the first list, from and to, and the lines of
     * the second in their place, from and to; or {@code null} for lists that differ in more than
     * {@link #MAX_CHANGED} lines in more than one place
     */
    static List<int[]> changes(List<String> first, List<String> second) {
        Map<String, Integer> numbers = new HashMap<>()
        Script script = script(numbered(first, numbers), numbered(second, numbers))
        return script == null ? null : script.edits.collect { Edit edit -> [edit.start, edit.end, edit.changedStart, edit.changedEnd] as int[] }
    }

    private static int[] numbered(List<String> lines, Map<String, Integer> numbers) {
        int[] keys = new int[lines.size()]
        for (int index = 0; index < keys.length; index++) {
            Integer number = numbers.get(lines[index])
            if (number == null) {
                number = numbers.size()
                numbers.put(lines[index], number)
            }
            keys[index] = number
        }
        return keys
    }

    /**
     * A file as it is generated, with the line endings of the file of the project it takes the place
     * of, as a merge gives the lines the generator adds: every line ends with CR LF when every line of
     * the current file does, and as it was generated otherwise.
     *
     * @param current the text file of the project
     * @param generated the text file as it is generated
     * @return the generated file with those line endings
     */
    static byte[] withLineEndingsOf(byte[] current, byte[] generated) {
        if (!withCarriageReturns(lines(current))) {
            return generated
        }
        List<String> generatedLines = lines(generated)
        StringBuilder result = new StringBuilder()
        append(result, generatedLines, 0, generatedLines.size(), true)
        return result.toString().getBytes(StandardCharsets.ISO_8859_1)
    }

    // whether every line of a file ends with CR LF, the last line of a file that does not end with a line feed apart
    private static boolean withCarriageReturns(List<String> lines) {
        return lines.any { String line -> line.endsWith('\r\n') } && !lines.any { String line -> line.endsWith('\n') && !line.endsWith('\r\n') }
    }

    static ThreeWayMerge of(byte[] base, byte[] current, byte[] proposed) {
        if (lineCount(base) > MAX_LINES || lineCount(current) > MAX_LINES || lineCount(proposed) > MAX_LINES) {
            return new ThreeWayMerge(null, [], true)
        }
        List<String> baseLines = lines(base)
        List<String> currentLines = lines(current)
        List<String> proposedLines = lines(proposed)
        // a line is a number: the lines of the three files that are the same line have the same one
        Map<String, Integer> numbers = new HashMap<>()
        int[] baseKeys = keys(baseLines, numbers)
        int[] currentKeys = keys(currentLines, numbers)
        int[] proposedKeys = keys(proposedLines, numbers)
        if (Arrays.equals(currentKeys, proposedKeys)) {
            // the project has the file as it is generated now, but perhaps for how its lines end
            return new ThreeWayMerge(current, [], false, Arrays.equals(baseKeys, currentKeys) ? 0 : 1)
        }
        Script toProposed = script(baseKeys, proposedKeys)
        Script toCurrent = toProposed == null ? null : script(baseKeys, currentKeys)
        if (toCurrent == null) {
            return new ThreeWayMerge(null, [], true)
        }
        List<Edit> ofProposed = toProposed.edits
        List<Edit> ofCurrent = toCurrent.edits
        // A change reaches from the anchor before it to the anchor after it. Where the nearest anchors
        // are is worked out once for every line, not once for every change
        int[] after = new int[baseKeys.length + 1]
        int[] behind = new int[baseKeys.length + 1]
        after[0] = 0
        for (int index = 0; index < baseKeys.length; index++) {
            // the first line of the stretch a change that begins at index + 1 is in
            after[index + 1] = toCurrent.loose[index] || toProposed.loose[index] ? after[index] : index + 1
        }
        behind[baseKeys.length] = baseKeys.length
        for (int index = baseKeys.length - 1; index >= 0; index--) {
            // the line the stretch of a change that ends at index ends before
            behind[index] = toCurrent.loose[index] || toProposed.loose[index] ? behind[index + 1] : index
        }
        for (Edit edit : ofProposed + ofCurrent) {
            edit.from = after[edit.start]
            edit.to = behind[edit.end]
        }
        boolean carriageReturns = withCarriageReturns(currentLines)

        StringBuilder result = new StringBuilder()
        List<Conflict> conflicts = []
        int changedAlike = 0
        // how often each line is in the result, and by how much the stretches both changed alike change that
        int[] inResult = new int[numbers.size()]
        int[] byBoth = new int[numbers.size()]
        int at = 0
        int nextProposed = 0
        int nextCurrent = 0
        int proposedOffset = 0
        int currentOffset = 0
        while (nextProposed < ofProposed.size() || nextCurrent < ofCurrent.size()) {
            int start = Math.min(nextProposed < ofProposed.size() ? ofProposed[nextProposed].from : Integer.MAX_VALUE,
                    nextCurrent < ofCurrent.size() ? ofCurrent[nextCurrent].from : Integer.MAX_VALUE)
            // what neither changed, as the project has it
            append(result, currentLines, at + currentOffset, start + currentOffset, false)
            count(inResult, currentKeys, at + currentOffset, start + currentOffset, 1)
            int end = start
            int proposedGrowth = 0
            int currentGrowth = 0
            boolean byProposed = false
            boolean byCurrent = false
            // every change that has no anchor between it and this one is of the same stretch
            boolean grew = true
            while (grew) {
                grew = false
                if (nextProposed < ofProposed.size() && ofProposed[nextProposed].from <= end) {
                    Edit edit = ofProposed[nextProposed++]
                    end = Math.max(end, edit.to)
                    proposedGrowth += edit.growth
                    byProposed = true
                    grew = true
                }
                if (nextCurrent < ofCurrent.size() && ofCurrent[nextCurrent].from <= end) {
                    Edit edit = ofCurrent[nextCurrent++]
                    end = Math.max(end, edit.to)
                    currentGrowth += edit.growth
                    byCurrent = true
                    grew = true
                }
            }
            int proposedFrom = start + proposedOffset
            int proposedTo = end + proposedOffset + proposedGrowth
            int currentFrom = start + currentOffset
            int currentTo = end + currentOffset + currentGrowth
            if (!byProposed) {
                append(result, currentLines, currentFrom, currentTo, false)
                count(inResult, currentKeys, currentFrom, currentTo, 1)
            } else if (!byCurrent) {
                append(result, proposedLines, proposedFrom, proposedTo, carriageReturns)
                count(inResult, proposedKeys, proposedFrom, proposedTo, 1)
            } else if (same(proposedKeys, proposedFrom, proposedTo, currentKeys, currentFrom, currentTo)) {
                // both made the same change of this stretch: the lines of the project are those of the generator
                append(result, currentLines, currentFrom, currentTo, false)
                count(inResult, currentKeys, currentFrom, currentTo, 1)
                count(byBoth, currentKeys, currentFrom, currentTo, 1)
                count(byBoth, baseKeys, start, end, -1)
                changedAlike++
            } else {
                conflicts << conflict(baseLines, baseKeys, start, end, currentLines, currentKeys, currentFrom, currentTo, proposedLines, proposedKeys, proposedFrom, proposedTo)
            }
            at = end
            proposedOffset += proposedGrowth
            currentOffset += currentGrowth
        }
        append(result, currentLines, at + currentOffset, currentLines.size(), false)
        count(inResult, currentKeys, at + currentOffset, currentLines.size(), 1)
        if (conflicts.isEmpty()) {
            conflicts.addAll(unaccounted(numbers.size(), baseKeys, currentKeys, proposedKeys, inResult, byBoth, baseLines, currentLines, proposedLines))
        }
        return new ThreeWayMerge(conflicts.isEmpty() ? result.toString().getBytes(StandardCharsets.ISO_8859_1) : null, conflicts, false, changedAlike)
    }

    /**
     * What every result is held to before it is one, whatever lined the files up: every line is in
     * it as often as the three files account for.
     *
     * <ul>
     * <li>The result has a line as often as the base has it, more or less by what each side added or
     * removed of it, the changes both made alike in one stretch counted once.</li>
     * <li>A change both made alike in one stretch is the same change only when neither side does the
     * opposite elsewhere: lines both removed in one place and each added in another were moved by
     * both, and would be there twice; lines both added in one place and each removed in another
     * would be there once less than any file has them.</li>
     * <li>A line that is new to the file is not in the result more often than the side that has it
     * most often has it: both added it, each in another place.</li>
     * </ul>
     *
     * @return a conflict for every line that is not accounted for, in the order of the lines of the
     * current file they are first on
     */
    private static List<Conflict> unaccounted(int numbers, int[] base, int[] current, int[] proposed, int[] inResult, int[] byBoth,
                                              List<String> baseLines, List<String> currentLines, List<String> proposedLines) {
        int[] inBase = new int[numbers]
        int[] inCurrent = new int[numbers]
        int[] inProposed = new int[numbers]
        count(inBase, base, 0, base.length, 1)
        count(inCurrent, current, 0, current.length, 1)
        count(inProposed, proposed, 0, proposed.length, 1)
        List<Conflict> conflicts = []
        for (int line = 0; line < numbers; line++) {
            int ofCurrent = inCurrent[line] - inBase[line]
            int ofProposed = inProposed[line] - inBase[line]
            boolean accounted = inResult[line] == inBase[line] + ofCurrent + ofProposed - byBoth[line]
            boolean moved = byBoth[line] != 0 && (long) (ofCurrent - byBoth[line]) * byBoth[line] < 0 && (long) (ofProposed - byBoth[line]) * byBoth[line] < 0
            boolean twice = inBase[line] == 0 && inResult[line] > Math.max(inCurrent[line], inProposed[line])
            if (!accounted || moved || twice) {
                int inCurrentFile = indexOf(current, line)
                int inProposedFile = indexOf(proposed, line)
                List<String> shown = shown([inCurrentFile >= 0 ? currentLines[inCurrentFile] : inProposedFile >= 0 ? proposedLines[inProposedFile] : baseLines[indexOf(base, line)]])
                conflicts << new Conflict(inCurrentFile + 1, inBase[line] == 0 ? [] as List<String> : shown, inCurrentFile >= 0 ? shown : [] as List<String>,
                        inProposedFile >= 0 ? shown : [] as List<String>, true)
            }
        }
        return conflicts.sort(false) { Conflict conflict -> conflict.line }
    }

    private static int indexOf(int[] keys, int key) {
        for (int index = 0; index < keys.length; index++) {
            if (keys[index] == key) {
                return index
            }
        }
        return -1
    }

    private static void count(int[] counts, int[] keys, int from, int to, int by) {
        for (int index = from; index < to; index++) {
            counts[keys[index]] += by
        }
    }

    // the place without the lines all three have alike at its beginning and its end, which are there
    // because a stretch reaches from anchor to anchor
    private static Conflict conflict(List<String> baseLines, int[] baseKeys, int baseFrom, int baseTo, List<String> currentLines, int[] currentKeys, int currentFrom, int currentTo,
                                     List<String> proposedLines, int[] proposedKeys, int proposedFrom, int proposedTo) {
        while (baseFrom < baseTo && currentFrom < currentTo && proposedFrom < proposedTo && currentKeys[currentFrom] == proposedKeys[proposedFrom] &&
                baseKeys[baseFrom] == currentKeys[currentFrom]) {
            baseFrom++
            currentFrom++
            proposedFrom++
        }
        while (baseFrom < baseTo && currentFrom < currentTo && proposedFrom < proposedTo && currentKeys[currentTo - 1] == proposedKeys[proposedTo - 1] &&
                baseKeys[baseTo - 1] == currentKeys[currentTo - 1]) {
            baseTo--
            currentTo--
            proposedTo--
        }
        return new Conflict(currentFrom + 1, shown(baseLines.subList(baseFrom, baseTo)), shown(currentLines.subList(currentFrom, currentTo)),
                shown(proposedLines.subList(proposedFrom, proposedTo)))
    }

    private static List<String> shown(List<String> lines) {
        return lines.collect { String line ->
            String without = line.endsWith('\r\n') ? line.substring(0, line.length() - 2) : line.endsWith('\n') ? line.substring(0, line.length() - 1) : line
            new String(without.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
        }
    }

    private static boolean same(int[] first, int firstFrom, int firstTo, int[] second, int secondFrom, int secondTo) {
        if (firstTo - firstFrom != secondTo - secondFrom) {
            return false
        }
        for (int index = 0; index < firstTo - firstFrom; index++) {
            if (first[firstFrom + index] != second[secondFrom + index]) {
                return false
            }
        }
        return true
    }

    private static void append(StringBuilder result, List<String> lines, int from, int to, boolean withCarriageReturns) {
        for (String line : lines.subList(from, to)) {
            if (withCarriageReturns && line.endsWith('\n') && !line.endsWith('\r\n')) {
                result.append(line, 0, line.length() - 1).append('\r\n')
            } else {
                result.append(line)
            }
        }
    }

    private static int lineCount(byte[] bytes) {
        int count = 0
        for (byte each : bytes) {
            if (each == (byte) 10) {
                count++
            }
        }
        return count
    }

    // The lines of a file, each with what ends it. A byte is a character, so that a file that is not
    // the text it is taken for comes out of a merge with every byte it went in with
    private static List<String> lines(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1)
        List<String> lines = []
        int from = 0
        for (int feed = text.indexOf('\n'); feed >= 0; feed = text.indexOf('\n', from)) {
            lines << text.substring(from, feed + 1)
            from = feed + 1
        }
        if (from < text.length()) {
            lines << text.substring(from)
        }
        return lines
    }

    private static int[] keys(List<String> lines, Map<String, Integer> numbers) {
        int[] keys = new int[lines.size()]
        for (int index = 0; index < keys.length; index++) {
            String line = lines[index]
            String key = line.endsWith('\r\n') ? line.substring(0, line.length() - 2) + '\n' : line
            Integer number = numbers.get(key)
            if (number == null) {
                number = numbers.size()
                numbers.put(key, number)
            }
            keys[index] = number
        }
        return keys
    }

    /**
     * What was changed between the base and a file, as the shortest list of lines to remove and to
     * add (Myers), with the lines of the base that not every such list keeps.
     *
     * @return the changes in the order of the base, or {@code null} when there are more than are merged
     */
    private static Script script(int[] base, int[] changed) {
        int prefix = 0
        while (prefix < base.length && prefix < changed.length && base[prefix] == changed[prefix]) {
            prefix++
        }
        int suffix = 0
        while (suffix < base.length - prefix && suffix < changed.length - prefix && base[base.length - 1 - suffix] == changed[changed.length - 1 - suffix]) {
            suffix++
        }
        int n = base.length - prefix - suffix
        int m = changed.length - prefix - suffix
        if (n == 0 || m == 0) {
            // lines were only added in one place, or only removed
            List<Edit> one = n + m == 0 ? [] as List<Edit> : [new Edit(prefix, prefix + n, prefix, prefix + m)]
            return new Script(one, loose(base, changed, prefix, suffix))
        }
        int most = Math.min(n + m, MAX_CHANGED)
        // the furthest line of the base reached on each diagonal with d changes, for every d
        List<int[]> reached = []
        int[] furthest = new int[2 * most + 3]
        int offset = most + 1
        long steps = 0
        int found = -1
        for (int d = 0; d <= most && found < 0; d++) {
            for (int k = -d; k <= d; k += 2) {
                int x = (k == -d || (k != d && furthest[offset + k - 1] < furthest[offset + k + 1])) ? furthest[offset + k + 1] : furthest[offset + k - 1] + 1
                int y = x - k
                while (x < n && y < m && base[prefix + x] == changed[prefix + y]) {
                    x++
                    y++
                    steps++
                }
                furthest[offset + k] = x
                if (x >= n && y >= m) {
                    found = d
                    break
                }
            }
            steps += d
            if (steps > MAX_STEPS) {
                return null
            }
            reached << Arrays.copyOfRange(furthest, offset - d, offset + d + 1)
        }
        if (found < 0) {
            return null
        }
        // back from the end: which lines were removed, and which were added
        boolean[] removed = new boolean[n]
        boolean[] added = new boolean[m]
        int x = n
        int y = m
        for (int d = found; d > 0; d--) {
            int[] before = reached[d - 1]
            int k = x - y
            boolean down = k == -d || (k != d && before[k - 1 + (d - 1)] < before[k + 1 + (d - 1)])
            int previousK = down ? k + 1 : k - 1
            int previousX = before[previousK + (d - 1)]
            int previousY = previousX - previousK
            if (down) {
                added[previousY] = true
            } else {
                removed[previousX] = true
            }
            x = previousX
            y = previousY
        }
        List<Edit> edits = []
        int i = 0
        int j = 0
        while (i < n || j < m) {
            if ((i < n && removed[i]) || (j < m && added[j])) {
                int fromBase = i
                int fromChanged = j
                while ((i < n && removed[i]) || (j < m && added[j])) {
                    if (i < n && removed[i]) {
                        i++
                    } else {
                        j++
                    }
                }
                edits << new Edit(prefix + fromBase, prefix + i, prefix + fromChanged, prefix + j)
            } else {
                i++
                j++
            }
        }
        return new Script(edits, loose(base, changed, prefix, suffix))
    }

    /**
     * The lines of the base that are no anchors for one side: a line that is removed, a line that
     * some shortest list of changes removes, and a line that the shortest lists of changes keep as
     * different lines of the other file. A change beside such a line could as well be said to be on
     * its other side.
     *
     * <p>Worked out from the longest common subsequences of what is before and after each line, for
     * the part of the two files that differs and, before and after it, the lines that are like a line
     * of that part: a line of that part can be lined up with one of those in its place. The lines
     * further out, which are the same in both files and like nothing of that part, are kept. For more
     * pairs of lines than {@link #MAX_PAIRS}, a line counts as loose unless it is there once in that
     * part of each file.</p>
     *
     * @param prefix how many lines the two files have alike at their beginning
     * @param suffix how many they have alike at their end, after those
     * @return for each line of the base, whether it is loose
     */
    private static boolean[] loose(int[] base, int[] changed, int prefix, int suffix) {
        boolean[] loose = new boolean[base.length]
        if (prefix + suffix == base.length && base.length == changed.length) {
            return loose
        }
        Set<Integer> differing = new HashSet<>()
        for (int index = prefix; index < base.length - suffix; index++) {
            differing.add(base[index])
        }
        for (int index = prefix; index < changed.length - suffix; index++) {
            differing.add(changed[index])
        }
        int from = prefix
        while (from > 0 && differing.contains(base[from - 1])) {
            from--
        }
        int rest = suffix
        while (rest > 0 && differing.contains(base[base.length - rest])) {
            rest--
        }
        int n = base.length - from - rest
        int m = changed.length - from - rest
        if ((long) n * m > MAX_PAIRS) {
            Map<Integer, Integer> inBase = new HashMap<>()
            Map<Integer, Integer> inChanged = new HashMap<>()
            for (int i = 0; i < n; i++) {
                inBase.merge(base[from + i], 1) { Integer one, Integer other -> one + other }
            }
            for (int j = 0; j < m; j++) {
                inChanged.merge(changed[from + j], 1) { Integer one, Integer other -> one + other }
            }
            for (int i = 0; i < n; i++) {
                loose[from + i] = inBase.get(base[from + i]) != 1 || inChanged.get(base[from + i]) != 1
            }
            return loose
        }
        int width = m + 1
        // before[i * width + j]: the most lines the first i of the base and the first j of the other have alike, in order
        int[] before = new int[(n + 1) * width]
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                before[i * width + j] = base[from + i - 1] == changed[from + j - 1] ? before[(i - 1) * width + j - 1] + 1 :
                        Math.max(before[(i - 1) * width + j], before[i * width + j - 1])
            }
        }
        // after[i * width + j]: the same of the lines from i of the base and from j of the other
        int[] after = new int[(n + 2) * width]
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                after[i * width + j] = base[from + i] == changed[from + j] ? after[(i + 1) * width + j + 1] + 1 :
                        Math.max(after[(i + 1) * width + j], after[i * width + j + 1])
            }
        }
        int most = before[n * width + m]
        for (int i = 0; i < n; i++) {
            // with which lines of the other file a longest common subsequence has this line, and
            // whether one has it with none
            int partners = 0
            boolean without = false
            for (int j = 0; j <= m && !without && partners < 2; j++) {
                without = before[i * width + j] + after[(i + 1) * width + j] == most
                if (j < m && base[from + i] == changed[from + j] && before[i * width + j] + 1 + after[(i + 1) * width + j + 1] == most) {
                    partners++
                }
            }
            loose[from + i] = without || partners != 1
        }
        return loose
    }

    @CompileStatic
    private static final class Script {

        final List<Edit> edits
        // the lines of the base that are no anchors for this side, see loose
        final boolean[] loose

        Script(List<Edit> edits, boolean[] loose) {
            this.edits = edits
            this.loose = loose
        }
    }

    @CompileStatic
    private static final class Edit {

        // the lines of the base that were replaced, and the lines of the changed file in their place
        final int start
        final int end
        final int changedStart
        final int changedEnd
        final int growth
        // the lines of the base the change counts as touching
        int from
        int to

        Edit(int start, int end, int changedStart, int changedEnd) {
            this.start = start
            this.end = end
            this.changedStart = changedStart
            this.changedEnd = changedEnd
            this.growth = (changedEnd - changedStart) - (end - start)
            this.from = start
            this.to = end
        }

    }
}
