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
package org.grails.forge.cli.util

import groovy.transform.CompileStatic

/**
 * A pattern of a {@code .gitignore} or a {@code .gitattributes} as the commands of a project read one,
 * matched against the path of a file below the directory of that file. A pattern without a slash
 * matches the name of a file in any directory; one with a slash, also a leading one, matches the path
 * from the directory of the file. {@code *} is any characters and {@code ?} one character, neither of
 * them a slash; a step that is {@code **} alone is any number of directories, none among them, and
 * at the end of the pattern everything below. Stars that follow each other are one star, as Git reads
 * them, and every other character is itself.
 *
 * <p>The pattern is not made a regular expression: the files of a project are read as they are, and a
 * line of a few stars and letters would keep a regular expression that goes back over its stars busy
 * for hours. A path is matched step by step, and a step only ever goes back to its last star, so the
 * time is bounded by the product of the lengths of the pattern and the path.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class GitPattern {

    private static final String ANY_DIRECTORIES = '**'
    private static final char STAR = '*' as char
    private static final char ANY = '?' as char

    private final boolean anywhere
    // the steps of the pattern between its slashes, with the stars that follow each other made one;
    // null for a step that is ** alone, and never two of those in a row, which are one
    private final List<String> steps
    // the steps that are a name each: a path with fewer names is not matched
    private final int nameSteps

    private GitPattern(boolean anywhere, List<String> steps) {
        this.anywhere = anywhere
        this.steps = steps
        this.nameSteps = steps.count { String step -> step != null } as int
    }

    /**
     * @param pattern a pattern as a line of the file has it, without what the file reads around it: an
     * exclamation mark in front of it, the slash at the end that makes it one of a directory, and the
     * attributes after it
     * @return the pattern
     */
    static GitPattern of(String pattern) {
        boolean anywhere = !pattern.contains('/')
        String glob = pattern.startsWith('/') ? pattern.substring(1) : pattern
        List<String> steps = []
        for (String step : glob.split('/', -1)) {
            if (step != ANY_DIRECTORIES) {
                steps << step.replaceAll(/\*+/, '*')
            } else if (steps.isEmpty() || steps.last() != null) {
                steps << null
            }
        }
        return new GitPattern(anywhere, steps.asImmutable())
    }

    /**
     * @param path the path of a file or a directory below the directory of the file the pattern is in,
     * with {@code /} between its steps
     * @return whether the pattern matches the whole path
     */
    boolean matches(String path) {
        List<String> names = path.split('/', -1).toList()
        if (nameSteps > names.size()) {
            return false
        }
        Boolean[][] known = new Boolean[steps.size() + 1][names.size() + 1]
        if (!anywhere) {
            return matchesFrom(0, 0, names, known)
        }
        // a name anywhere: the pattern starts at any step of the path
        for (int start = 0; start < names.size(); start++) {
            if (matchesFrom(0, start, names, known)) {
                return true
            }
        }
        return false
    }

    // whether the steps of the pattern from the one given match the names of the path from the one given, and
    // nothing is left of either; what is known of a pair is not worked out again
    private boolean matchesFrom(int step, int name, List<String> names, Boolean[][] known) {
        Boolean answer = known[step][name]
        if (answer != null) {
            return answer
        }
        boolean matched
        if (step == steps.size()) {
            matched = name == names.size()
        } else if (steps[step] == null) {
            if (step == steps.size() - 1) {
                // at the end: everything below, which is something
                matched = name < names.size()
            } else {
                // any number of directories, none among them
                matched = false
                for (int next = name; next < names.size() && !matched; next++) {
                    matched = matchesFrom(step + 1, next, names, known)
                }
            }
        } else {
            matched = name < names.size() && matchesName(steps[step], names[name]) && matchesFrom(step + 1, name + 1, names, known)
        }
        known[step][name] = matched
        return matched
    }

    // whether a step that is no ** matches one name: a character that does not match goes back to the last star,
    // which then takes one character more, and never further; no two stars follow each other
    private static boolean matchesName(String glob, String name) {
        int g = 0
        int n = 0
        int star = -1
        int fromStar = 0
        while (n < name.length()) {
            if (g < glob.length() && glob.charAt(g) == STAR) {
                star = g
                fromStar = n
                g++
            } else if (g < glob.length() && (glob.charAt(g) == ANY || glob.charAt(g) == name.charAt(n))) {
                g++
                n++
            } else if (star >= 0) {
                fromStar++
                g = star + 1
                n = fromStar
            } else {
                return false
            }
        }
        while (g < glob.length() && glob.charAt(g) == STAR) {
            g++
        }
        return g == glob.length()
    }
}
