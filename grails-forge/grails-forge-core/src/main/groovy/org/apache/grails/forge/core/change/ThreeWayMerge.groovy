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
 * LF. A change of the generator that touches no line the project changed, and no line next to one, is
 * taken; where the two changed the same lines, or lines next to each other, in different ways, there
 * is a conflict and no result. The same change made by both is no conflict.</p>
 *
 * <p>Where lines repeat, two files can be lined up in more than one way, and nothing says which line
 * a change is next to. A change therefore counts as touching every line beside it that is like a line
 * it adds or removes, and every line beside it that not every shortest list of changes keeps; and a
 * line that is removed in one place and added a few lines away counts as one change with the lines
 * between. Two changes are then not merged by the accident of how the lines were lined up.</p>
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

    private ThreeWayMerge(byte[] merged, List<Conflict> conflicts, boolean tooLarge) {
        this.merged = merged
        this.conflicts = conflicts
        this.tooLarge = tooLarge
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
     * A place both changed, each in its way.
     */
    @CompileStatic
    static final class Conflict {

        /** The number of the first line of the place in the current file, from 1; for lines that were only removed there, the line after them */
        final int line

        /** The lines the current file has there, without what ends them; none when it removed lines */
        final List<String> current

        /** The lines the proposed file has there, without what ends them; none when it removed lines */
        final List<String> proposed

        Conflict(int line, List<String> current, List<String> proposed) {
            this.line = line
            this.current = current
            this.proposed = proposed
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
        return edits(keys(lines(first), numbers), keys(lines(second), numbers)) != null
    }

    static ThreeWayMerge of(byte[] base, byte[] current, byte[] proposed) {
        if (lineCount(base) > MAX_LINES || lineCount(current) > MAX_LINES || lineCount(proposed) > MAX_LINES) {
            return new ThreeWayMerge(null, [], true)
        }
        List<String> currentLines = lines(current)
        List<String> proposedLines = lines(proposed)
        // a line is a number: the lines of the three files that are the same line have the same one
        Map<String, Integer> numbers = new HashMap<>()
        int[] baseKeys = keys(lines(base), numbers)
        int[] currentKeys = keys(currentLines, numbers)
        int[] proposedKeys = keys(proposedLines, numbers)
        List<Edit> ofProposed = edits(baseKeys, proposedKeys)
        List<Edit> ofCurrent = ofProposed == null ? null : edits(baseKeys, currentKeys)
        if (ofCurrent == null) {
            return new ThreeWayMerge(null, [], true)
        }
        boolean carriageReturns = currentLines.any { String line -> line.endsWith('\r\n') } &&
                !currentLines.any { String line -> line.endsWith('\n') && !line.endsWith('\r\n') }

        StringBuilder result = new StringBuilder()
        List<Conflict> conflicts = []
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
            int end = start
            int proposedGrowth = 0
            int currentGrowth = 0
            boolean byProposed = false
            boolean byCurrent = false
            // a change that begins where another ends is taken with it: lines next to each other that
            // the two changed are not merged without a word
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
            } else if (!byCurrent) {
                append(result, proposedLines, proposedFrom, proposedTo, carriageReturns)
            } else if (same(proposedKeys, proposedFrom, proposedTo, currentKeys, currentFrom, currentTo)) {
                append(result, currentLines, currentFrom, currentTo, false)
            } else {
                conflicts << conflict(currentLines, currentKeys, currentFrom, currentTo, proposedLines, proposedKeys, proposedFrom, proposedTo)
            }
            at = end
            proposedOffset += proposedGrowth
            currentOffset += currentGrowth
        }
        append(result, currentLines, at + currentOffset, currentLines.size(), false)
        return new ThreeWayMerge(conflicts.isEmpty() ? result.toString().getBytes(StandardCharsets.ISO_8859_1) : null, conflicts, false)
    }

    // the place without the lines the two have alike at its beginning and its end, which are there
    // because a change counts as touching the lines next to it
    private static Conflict conflict(List<String> currentLines, int[] currentKeys, int currentFrom, int currentTo,
                                     List<String> proposedLines, int[] proposedKeys, int proposedFrom, int proposedTo) {
        while (currentFrom < currentTo && proposedFrom < proposedTo && currentKeys[currentFrom] == proposedKeys[proposedFrom]) {
            currentFrom++
            proposedFrom++
        }
        while (currentFrom < currentTo && proposedFrom < proposedTo && currentKeys[currentTo - 1] == proposedKeys[proposedTo - 1]) {
            currentTo--
            proposedTo--
        }
        return new Conflict(currentFrom + 1, shown(currentLines.subList(currentFrom, currentTo)), shown(proposedLines.subList(proposedFrom, proposedTo)))
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
     * add (Myers), each change with the lines it could as well be next to.
     *
     * @return the changes in the order of the base, or {@code null} when there are more than are merged
     */
    private static List<Edit> edits(int[] base, int[] changed) {
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
            widen(one, base, changed, new boolean[base.length])
            return one
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
            return n + m == 0 ? [] as List<Edit> : null
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
        boolean[] all = new boolean[base.length]
        boolean[] ofMiddle = loose(base, changed, prefix, n, m)
        System.arraycopy(ofMiddle, 0, all, prefix, n)
        widen(edits, base, changed, all)
        return edits
    }

    /**
     * The lines of the base that not every shortest list of changes keeps: the files can be lined up
     * as well with such a line removed and another line like it kept in its place. A change beside
     * such a line could as well be said to be on its other side.
     *
     * <p>Worked out for the part of the files between what they have alike at their beginnings and
     * ends, from the longest common subsequences of what is before and after each line. For more
     * pairs of lines than {@link #MAX_PAIRS}, a line counts as loose unless it is there once in that
     * part of each file.</p>
     *
     * @return for each of the {@code n} lines of the base from {@code prefix}, whether it is loose
     */
    private static boolean[] loose(int[] base, int[] changed, int prefix, int n, int m) {
        boolean[] loose = new boolean[n]
        if ((long) n * m > MAX_PAIRS) {
            Map<Integer, Integer> inBase = new HashMap<>()
            Map<Integer, Integer> inChanged = new HashMap<>()
            for (int i = 0; i < n; i++) {
                inBase.merge(base[prefix + i], 1) { Integer one, Integer other -> one + other }
            }
            for (int j = 0; j < m; j++) {
                inChanged.merge(changed[prefix + j], 1) { Integer one, Integer other -> one + other }
            }
            for (int i = 0; i < n; i++) {
                loose[i] = inBase.get(base[prefix + i]) != 1 || inChanged.get(base[prefix + i]) != 1
            }
            return loose
        }
        int width = m + 1
        // before[i * width + j]: the most lines the first i of the base and the first j of the other have alike, in order
        int[] before = new int[(n + 1) * width]
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                before[i * width + j] = base[prefix + i - 1] == changed[prefix + j - 1] ? before[(i - 1) * width + j - 1] + 1 :
                        Math.max(before[(i - 1) * width + j], before[i * width + j - 1])
            }
        }
        // after[i * width + j]: the same of the lines from i of the base and from j of the other
        int[] after = new int[(n + 2) * width]
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                after[i * width + j] = base[prefix + i] == changed[prefix + j] ? after[(i + 1) * width + j + 1] + 1 :
                        Math.max(after[(i + 1) * width + j], after[i * width + j + 1])
            }
        }
        int most = before[n * width + m]
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= m && !loose[i]; j++) {
                loose[i] = before[i * width + j] + after[(i + 1) * width + j] == most
            }
        }
        return loose
    }

    // A line that was added or removed next to a line like it could as well be said to be that line,
    // and the change to be a line further up or down. A change counts as touching every line next to
    // it that is like a line it adds or removes, and the lines next to those, up to the changes
    // before and after it
    private static void widen(List<Edit> edits, int[] base, int[] changed, boolean[] loose) {
        for (int index = 0; index < edits.size(); index++) {
            Edit edit = edits[index]
            int lowest = index == 0 ? 0 : edits[index - 1].end
            int highest = index == edits.size() - 1 ? base.length : edits[index + 1].start
            Set<Integer> lines = new HashSet<>()
            for (int line = edit.start; line < edit.end; line++) {
                lines.add(base[line])
            }
            for (int line = edit.changedStart; line < edit.changedEnd; line++) {
                lines.add(changed[line])
            }
            int from = edit.start
            while (from > lowest && (loose[from - 1] || lines.contains(base[from - 1]))) {
                from--
            }
            int to = edit.end
            while (to < highest && (loose[to] || lines.contains(base[to]))) {
                to++
            }
            edit.from = from
            edit.to = to
        }
        // A line that is taken out in one place and put in a few lines away was moved, and that can
        // as well be said of the lines it was moved past: two such changes are one, with the lines
        // between them
        for (int index = 0; index + 1 < edits.size(); index++) {
            Edit edit = edits[index]
            Edit next = edits[index + 1]
            if (next.from - edit.to <= edit.size() + next.size() && alike(edit, next, base, changed)) {
                edit.to = next.start
                next.from = edit.end
            }
        }
    }

    // whether two changes add or remove a line that is the same line
    private static boolean alike(Edit one, Edit other, int[] base, int[] changed) {
        Set<Integer> lines = new HashSet<>()
        for (int index = one.start; index < one.end; index++) {
            lines.add(base[index])
        }
        for (int index = one.changedStart; index < one.changedEnd; index++) {
            lines.add(changed[index])
        }
        for (int index = other.start; index < other.end; index++) {
            if (lines.contains(base[index])) {
                return true
            }
        }
        for (int index = other.changedStart; index < other.changedEnd; index++) {
            if (lines.contains(changed[index])) {
                return true
            }
        }
        return false
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

        // how many lines the change removes and adds
        int size() {
            return (end - start) + (changedEnd - changedStart)
        }
    }
}
