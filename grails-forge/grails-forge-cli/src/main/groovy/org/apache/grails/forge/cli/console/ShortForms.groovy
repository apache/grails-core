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
package org.apache.grails.forge.cli.console

import java.util.function.Function
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.grails.build.parsing.ScriptNameResolver
import picocli.CommandLine

/**
 * The short forms of the names of commands, and the commands they are short for.
 *
 * <p>A short form is read in three ways, and the first way that fits any command is the one that
 * counts:</p>
 * <ol>
 * <li>by the rule of the Grails shell, {@link ScriptNameResolver}: the form is cut into parts before
 * each upper-case letter and at each hyphen, there are as many parts as the name has words, and each
 * part is the start of its word whatever its case, so that {@code cA}, {@code creA} and
 * {@code create-a} are {@code create-app}. A form of lower-case letters only is one part, which is
 * the start of the name of a command of one word: {@code h} is {@code help};</li>
 * <li>a form of lower-case letters only, as the first letters of the words of a name:
 * {@code ra} is {@code run-app}, {@code cdc} is {@code create-domain-class};</li>
 * <li>a form of lower-case letters only, cut into as many parts as the name has words, each part the
 * start of its word, in their order: {@code runa} and {@code rapp} are {@code run-app},
 * {@code ccon} is {@code create-controller}.</li>
 * </ol>
 *
 * @since 9.0
 */
@CompileStatic
final class ShortForms {

    /** What can be a short form at all: anything else, a path or an option among them, is short for nothing. */
    public static final Pattern FORM = Pattern.compile('[A-Za-z][A-Za-z-]{0,63}')

    // a form of lower-case letters only: where its words begin is not marked, and every cut is read
    private static final Pattern LOWER_CASE = Pattern.compile('[a-z]{1,64}')

    // how many forms of a name are tried for the shortest that is short for it alone, at most
    private static final int MAX_TRIED = 4096

    private ShortForms() {
    }

    /**
     * The commands a form is short for, by the first of the three readings any of them fits.
     *
     * @param typed what was typed, which {@link #FORM} matches
     * @param commands the commands it may be short for
     * @return those it is short for, in their order; none, one or several
     */
    static List<CommandLine> fitting(String typed, List<CommandLine> commands) {
        List<CommandLine> fits = commands.findAll { CommandLine command ->
            names(command).any { String name -> ScriptNameResolver.resolvesTo(typed, name) }
        }
        if (!fits.isEmpty() || !LOWER_CASE.matcher(typed).matches()) {
            return fits
        }
        fits = commands.findAll { CommandLine command -> names(command).any { String name -> isInitials(typed, name) } }
        if (!fits.isEmpty()) {
            return fits
        }
        return commands.findAll { CommandLine command -> names(command).any { String name -> isCut(typed, name) } }
    }

    /**
     * The shortest form of lower-case letters that is short for a command and for no other: the first
     * letters of each word of its name, and as few more as that takes. Of two forms of one length, the
     * one with fewer letters of the first words comes first.
     *
     * @param command the command
     * @param resolve what a form is: the command it is short for alone, or {@code null}
     * @return the form, or {@code null} when none of the forms that are tried is short for the command alone
     */
    static String shortest(CommandLine command, Function<String, CommandLine> resolve) {
        String[] words = command.getCommandSpec().name().split('-', -1)
        int[] limits = new int[words.length]
        int longest = 0
        for (int i = 0; i < words.length; i++) {
            limits[i] = lowerCaseStart(words[i])
            if (limits[i] == 0) {
                // a word that does not start with a lower-case letter has no part in a form of lower-case letters
                return null
            }
            longest += limits[i]
        }
        int[] tried = [0] as int[]
        for (int total = words.length; total <= longest && tried[0] < MAX_TRIED; total++) {
            String found = shortest(command, resolve, words, limits, 0, total, new StringBuilder(), tried)
            if (found != null) {
                return found
            }
        }
        return null
    }

    // the forms of the given length of the words from index on, after the form so far, with the fewest letters of the first words first
    private static String shortest(CommandLine command, Function<String, CommandLine> resolve, String[] words, int[] limits, int index, int remaining,
                                   StringBuilder form, int[] tried) {
        if (index == words.length) {
            if (remaining != 0 || ++tried[0] > MAX_TRIED) {
                return null
            }
            String candidate = form.toString()
            CommandLine resolved = resolve.apply(candidate)
            return resolved != null && resolved.is(command) ? candidate : null
        }
        int after = 0
        for (int i = index + 1; i < words.length; i++) {
            after += limits[i]
        }
        int fewest = Math.max(1, remaining - after)
        int most = Math.min(limits[index], remaining - (words.length - index - 1))
        for (int part = fewest; part <= most; part++) {
            form.append(words[index], 0, part)
            String found = shortest(command, resolve, words, limits, index + 1, remaining - part, form, tried)
            form.setLength(form.length() - part)
            if (found != null || tried[0] > MAX_TRIED) {
                return found
            }
        }
        return null
    }

    // the name of a command and the other names it has
    private static List<String> names(CommandLine command) {
        CommandLine.Model.CommandSpec spec = command.getCommandSpec()
        List<String> names = [spec.name()]
        names.addAll(spec.aliases().toList())
        return names
    }

    // whether a form is the first letters of the words of a name, one letter for each word
    private static boolean isInitials(String typed, String name) {
        String[] words = name.split('-', -1)
        if (words.length != typed.length()) {
            return false
        }
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty() || words[i].charAt(0) != typed.charAt(i)) {
                return false
            }
        }
        return true
    }

    // whether a form can be cut into as many parts as a name has words, each part the start of its word
    private static boolean isCut(String typed, String name) {
        String[] words = name.split('-', -1)
        int length = typed.length()
        if (length < words.length) {
            return false
        }
        // where the parts of the words so far can end in what was typed
        boolean[] ends = new boolean[length + 1]
        ends[0] = true
        for (String word : words) {
            boolean[] next = new boolean[length + 1]
            for (int from = 0; from < length; from++) {
                if (!ends[from]) {
                    continue
                }
                for (int part = 1; part <= word.length() && from + part <= length && typed.charAt(from + part - 1) == word.charAt(part - 1); part++) {
                    next[from + part] = true
                }
            }
            ends = next
        }
        return ends[length]
    }

    // how many letters a word starts with that a form of lower-case letters can have
    private static int lowerCaseStart(String word) {
        int letters = 0
        while (letters < word.length() && word.charAt(letters) >= ('a' as char) && word.charAt(letters) <= ('z' as char)) {
            letters++
        }
        return letters
    }
}
