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

/**
 * Files to merge, made from a seed: a base of a few lines out of a small alphabet, so that lines
 * repeat and many are empty, and two files that are the base with a few lines added, removed,
 * replaced, moved and doubled. The same seed gives the same files on every machine, which is what
 * the reference of {@link ThreeWayMergePropertySpec} relies on: change nothing here without making
 * the reference anew.
 */
@CompileStatic
final class ThreeWayMergeCases {

    private final Random random
    private int unique

    private ThreeWayMergeCases(long seed) {
        this.random = new Random(seed)
    }

    /**
     * @return for each case the base, the file of the project and the file of the generator
     */
    static List<List<String>> of(long seed, int count) {
        ThreeWayMergeCases cases = new ThreeWayMergeCases(seed)
        List<List<String>> all = []
        for (int index = 0; index < count; index++) {
            all << cases.next()
        }
        return all
    }

    private List<String> next() {
        int size = random.nextInt(5) == 0 ? random.nextInt(3) : random.nextInt(25)
        int alphabet = 1 + random.nextInt(Math.max(1, size * 2))
        if (random.nextInt(3) == 0) {
            alphabet = 1 + random.nextInt(3)
        }
        List<String> base = []
        for (int index = 0; index < size; index++) {
            base << line(alphabet)
        }
        List<String> current = edited(base, 'O', alphabet, 4)
        List<String> proposed
        int mode = random.nextInt(12)
        if (mode == 0) {
            proposed = new ArrayList<>(current)
        } else if (mode == 1) {
            proposed = new ArrayList<>(base)
        } else if (mode == 2) {
            proposed = edited(current, 'T', alphabet, 1)
        } else {
            proposed = edited(base, 'T', alphabet, 4)
        }
        boolean endOfBase = random.nextInt(6) != 0
        boolean endOfCurrent = random.nextInt(6) != 0
        boolean endOfProposed = random.nextInt(6) != 0
        if (random.nextInt(3) != 0) {
            endOfBase = endOfCurrent = endOfProposed = true
        }
        return [text(base, endOfBase), text(current, endOfCurrent), text(proposed, endOfProposed)]
    }

    private String line(int alphabet) {
        int which = random.nextInt(alphabet + 1)
        return which == alphabet ? '' : 'L' + which
    }

    private List<String> edited(List<String> base, String side, int alphabet, int most) {
        List<String> lines = new ArrayList<>(base)
        int edits = random.nextInt(most + 1)
        for (int edit = 0; edit < edits; edit++) {
            int kind = random.nextInt(7)
            int at = lines.isEmpty() ? 0 : random.nextInt(lines.size() + 1)
            if (random.nextInt(6) == 0) {
                at = 0
            }
            if (random.nextInt(6) == 0) {
                at = lines.size()
            }
            if (kind < 2) {
                int count = 1 + random.nextInt(3)
                for (int index = 0; index < count; index++) {
                    lines.add(at, side + (unique++))
                }
            } else if (kind == 2) {
                lines.add(at, line(alphabet))
            } else if (kind == 3 && !lines.isEmpty()) {
                int from = random.nextInt(lines.size())
                int count = 1 + random.nextInt(Math.min(3, lines.size() - from))
                for (int index = 0; index < count; index++) {
                    lines.remove(from)
                }
            } else if (kind == 4 && !lines.isEmpty()) {
                lines.set(random.nextInt(lines.size()), random.nextBoolean() ? side + (unique++) : line(alphabet))
            } else if (kind == 5 && lines.size() > 1) {
                String moved = lines.remove(random.nextInt(lines.size()))
                lines.add(random.nextInt(lines.size() + 1), moved)
            } else if (kind == 6 && !lines.isEmpty()) {
                int from = random.nextInt(lines.size())
                lines.add(from, lines.get(from))
            }
        }
        return lines
    }

    private static String text(List<String> lines, boolean ended) {
        StringBuilder text = new StringBuilder()
        for (int index = 0; index < lines.size(); index++) {
            text.append(lines.get(index))
            if (index < lines.size() - 1 || ended) {
                text.append('\n')
            }
        }
        return text.toString()
    }
}
