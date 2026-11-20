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

import com.github.difflib.DiffUtils
import com.github.difflib.patch.AbstractDelta
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
 * <p>The lines of the current file are kept byte for byte, with the line endings they have. The lines
 * the generator adds end as the lines of the current file do when all of those end with CR LF, and as
 * they were generated otherwise.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ThreeWayMerge {

    private final byte[] merged
    private final int conflicts

    private ThreeWayMerge(byte[] merged, int conflicts) {
        this.merged = merged
        this.conflicts = conflicts
    }

    /**
     * @return the merged file, or {@code null} when there are conflicts
     */
    byte[] getMerged() {
        return merged
    }

    /**
     * @return how many places the two changed in different ways
     */
    int getConflicts() {
        return conflicts
    }

    /**
     * @param first a text file
     * @param second a text file
     * @return whether the two are the same but for how their lines end
     */
    static boolean sameText(byte[] first, byte[] second) {
        return Arrays.equals(first, second) || keys(lines(first)) == keys(lines(second))
    }

    static ThreeWayMerge of(byte[] base, byte[] current, byte[] proposed) {
        List<String> currentLines = lines(current)
        List<String> proposedLines = lines(proposed)
        List<String> baseKeys = keys(lines(base))
        List<String> currentKeys = keys(currentLines)
        List<String> proposedKeys = keys(proposedLines)
        List<Edit> ofProposed = edits(baseKeys, proposedKeys)
        List<Edit> ofCurrent = edits(baseKeys, currentKeys)
        boolean carriageReturns = currentLines.any { String line -> line.endsWith('\r\n') } &&
                !currentLines.any { String line -> line.endsWith('\n') && !line.endsWith('\r\n') }

        StringBuilder result = new StringBuilder()
        int conflicts = 0
        int at = 0
        int nextProposed = 0
        int nextCurrent = 0
        int proposedOffset = 0
        int currentOffset = 0
        while (nextProposed < ofProposed.size() || nextCurrent < ofCurrent.size()) {
            int start = Math.min(nextProposed < ofProposed.size() ? ofProposed[nextProposed].start : Integer.MAX_VALUE,
                    nextCurrent < ofCurrent.size() ? ofCurrent[nextCurrent].start : Integer.MAX_VALUE)
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
                if (nextProposed < ofProposed.size() && ofProposed[nextProposed].start <= end) {
                    Edit edit = ofProposed[nextProposed++]
                    end = Math.max(end, edit.end)
                    proposedGrowth += edit.growth
                    byProposed = true
                    grew = true
                }
                if (nextCurrent < ofCurrent.size() && ofCurrent[nextCurrent].start <= end) {
                    Edit edit = ofCurrent[nextCurrent++]
                    end = Math.max(end, edit.end)
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
            } else if (proposedKeys.subList(proposedFrom, proposedTo) == currentKeys.subList(currentFrom, currentTo)) {
                append(result, currentLines, currentFrom, currentTo, false)
            } else {
                conflicts++
            }
            at = end
            proposedOffset += proposedGrowth
            currentOffset += currentGrowth
        }
        append(result, currentLines, at + currentOffset, currentLines.size(), false)
        return new ThreeWayMerge(conflicts == 0 ? result.toString().getBytes(StandardCharsets.ISO_8859_1) : null, conflicts)
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

    private static List<Edit> edits(List<String> base, List<String> changed) {
        List<Edit> edits = []
        for (AbstractDelta<String> delta : DiffUtils.diff(base, changed).getDeltas()) {
            edits << new Edit(delta.getSource().getPosition(), delta.getSource().getPosition() + delta.getSource().size(),
                    delta.getTarget().size() - delta.getSource().size())
        }
        return edits.sort(false) { Edit edit -> edit.start }
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

    private static List<String> keys(List<String> lines) {
        return lines.collect { String line -> line.endsWith('\r\n') ? line.substring(0, line.length() - 2) + '\n' : line }
    }

    @CompileStatic
    private static final class Edit {

        final int start
        final int end
        final int growth

        Edit(int start, int end, int growth) {
            this.start = start
            this.end = end
            this.growth = growth
        }
    }
}
