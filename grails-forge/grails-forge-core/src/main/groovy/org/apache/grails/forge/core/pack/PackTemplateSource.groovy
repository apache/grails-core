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

/**
 * Makes of the source of a template of a feature pack the page that is compiled: one in which the only
 * tags are the instructions to the generator.
 *
 * <p>The generator has no tag libraries. A GSP page compiled without them would still take every
 * {@code <prefix:name>} for a tag, evaluate its attributes and write it anew, which is not the tag the
 * template has. So before a template is compiled, the {@code <} of every tag that is no instruction is
 * replaced by an expression that writes a {@code <}: the page then has the tag as text, and writes it
 * as the template has it, with the expressions in it evaluated as they are anywhere in the text.</p>
 *
 * <p>The instructions are the tags of {@link #INSTRUCTIONS} in the namespace {@code g}, which are the
 * tags the GSP compiler compiles into a page. A tag of one of those names in another namespace is no
 * instruction.</p>
 *
 * <p>A backslash in front of what is an instruction writes it instead: in front of {@code <%}, which
 * starts a scriptlet, a directive or a comment, in front of <code>%{</code>, which starts the other
 * form of them, and in front of an instruction tag. The backslash is not written. A backslash in front
 * of <code>${</code> does the same, and is left to the page, which knows it.</p>
 *
 * <p>The source is walked the way the scanner of GSP pages reads it, so what is inside a scriptlet,
 * an expression, a comment, a directive or an instruction tag is never touched. No line break is
 * added or removed: a line of the page is the line of the template.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackTemplateSource {

    /** The tags of the namespace {@code g} that are instructions to the generator */
    public static final List<String> INSTRUCTIONS = List.of('if', 'elseif', 'else', 'unless', 'each', 'while', 'collect', 'findAll', 'grep', 'def')

    /** The namespace of the instructions */
    public static final String NAMESPACE = 'g'

    private static final int UNCLOSED = -1

    private final String source
    private final int length
    private final StringBuilder page
    private int copied

    private PackTemplateSource(String source) {
        this.source = source
        this.length = source.length()
        this.page = new StringBuilder(length + 64)
    }

    /**
     * @param source the source of a template
     * @return the page to compile for it
     */
    static String toPage(String source) {
        return new PackTemplateSource(source).walk()
    }

    private String walk() {
        int position = 0
        while (position < length && position != UNCLOSED) {
            position = next(position)
        }
        page.append(source, copied, length)
        return page.toString()
    }

    /**
     * @return where the text goes on after what is at the position, or {@link #UNCLOSED} when what
     * starts there does not end, which is left for the compiler to say
     */
    private int next(int position) {
        char c = source.charAt(position)
        char c1 = charAt(position + 1)
        if (c == (char) '\\') {
            return afterBackslash(position)
        }
        if (c == (char) '<' && length - position > 3) {
            if (c1 == (char) '%') {
                if (charAt(position + 2) == (char) '-' && charAt(position + 3) == (char) '-') {
                    int end = endAfter('--%>', position + 4)
                    if (end != UNCLOSED) {
                        return end
                    }
                }
                return endAfter('%>', position + 2)
            }
            int colon = colonOfTag(position)
            if (colon != UNCLOSED) {
                if (isInstruction(position, colon)) {
                    return endOfInstruction(position, colon)
                }
                // text from here on: what is in the tag is read as anything else in the text is
                replace(position, 1, (char) '<')
            }
            return position + 1
        }
        if (c == (char) '$' && c1 == (char) '{') {
            return endOfExpression(position + 2)
        }
        if (c == (char) '%' && c1 == (char) '{') {
            if (charAt(position + 2) == (char) '-' && charAt(position + 3) == (char) '-') {
                int end = endAfter('--}%', position + 4)
                if (end != UNCLOSED) {
                    return end
                }
            }
            return endAfter('}%', position + 2)
        }
        if (c == (char) '!' && c1 == (char) '{') {
            int end = position + 2
            while (end < length - 1 && !(source.charAt(end) == (char) '}' && (source.charAt(end + 1) == (char) '!' || source.charAt(end + 1) == (char) '%'))) {
                end++
            }
            return end < length - 1 ? end + 2 : UNCLOSED
        }
        if (c == (char) '@' && c1 == (char) '{') {
            return endAfter('}', position + 2)
        }
        return position + 1
    }

    private int afterBackslash(int position) {
        int escaped = position + 1
        if (source.startsWith('${', escaped)) {
            // the page writes it without the backslash
            return escaped + 2
        }
        if (source.startsWith('<%', escaped)) {
            replace(position, 2, (char) '<')
            return escaped + 1
        }
        if (source.startsWith('%{', escaped)) {
            replace(position, 2, (char) '%')
            return escaped + 1
        }
        if (charAt(escaped) == (char) '<' && length - escaped > 3) {
            int colon = colonOfTag(escaped)
            if (colon != UNCLOSED && isInstruction(escaped, colon)) {
                replace(position, 2, (char) '<')
                return escaped + 1
            }
        }
        return escaped
    }

    /**
     * Has the page write a character where the source has what would be read. The backslashes in front
     * of it are written with it: a page takes a backslash in front of an expression for an escape of it.
     */
    private void replace(int position, int count, char written) {
        int backslashes = position
        while (backslashes > copied && source.charAt(backslashes - 1) == (char) '\\') {
            backslashes--
        }
        page.append(source, copied, backslashes).append('${\'').append('\\\\' * (position - backslashes)).append(written).append('\'}')
        copied = position + count
    }

    /**
     * @return where the colon of the tag is that starts or ends at the position, which holds a
     * {@code <}, or {@link #UNCLOSED} when no tag does
     */
    private int colonOfTag(int position) {
        int namespaceStart = charAt(position + 1) == (char) '/' ? position + 2 : position + 1
        // a namespace is a word that starts with a letter, and the colon is right after it
        int colon = namespaceStart
        while (colon < length && isWord(source.charAt(colon))) {
            colon++
        }
        if (colon == namespaceStart || colon == length || source.charAt(colon) != (char) ':' || Character.isDigit(source.charAt(namespaceStart)) ||
                source.charAt(namespaceStart) == (char) '_') {
            return UNCLOSED
        }
        return colon
    }

    private boolean isInstruction(int position, int colon) {
        int namespaceStart = charAt(position + 1) == (char) '/' ? position + 2 : position + 1
        if (source.substring(namespaceStart, colon) != NAMESPACE) {
            return false
        }
        int end = colon + 1
        while (end < length) {
            char c = source.charAt(end)
            if (Character.isWhitespace(c) || c == (char) '>' || (c == (char) '/' && charAt(end + 1) == (char) '>') ||
                    (c == (char) '$' && charAt(end + 1) == (char) '{')) {
                break
            }
            end++
        }
        return INSTRUCTIONS.contains(source.substring(colon + 1, end))
    }

    private int endOfInstruction(int position, int colon) {
        if (charAt(position + 1) == (char) '/') {
            return endAfter('>', colon + 1)
        }
        int end = colon + 1
        while (end < length) {
            char c = source.charAt(end)
            if (c == (char) '$' && charAt(end + 1) == (char) '{') {
                end = endOfExpression(end + 2)
                if (end == UNCLOSED) {
                    return UNCLOSED
                }
            } else if (c == (char) '>') {
                return end + 1
            } else {
                end++
            }
        }
        return UNCLOSED
    }

    private int endAfter(String delimiter, int from) {
        int found = source.indexOf(delimiter, from)
        return found == -1 ? UNCLOSED : found + delimiter.length()
    }

    private static boolean isWord(char c) {
        return (c >= (char) 'a' && c <= (char) 'z') || (c >= (char) 'A' && c <= (char) 'Z') || (c >= (char) '0' && c <= (char) '9') || c == (char) '_'
    }

    private char charAt(int position) {
        return position < length ? source.charAt(position) : (char) 0
    }

    /**
     * Where an expression ends that starts at the position, after its <code>${</code>: at the brace
     * that closes it, which is not one inside a string, a list or a closure of it. It is found the way
     * the scanner of GSP pages finds it.
     */
    @SuppressWarnings(['CyclomaticComplexity', 'AbcMetric'])
    private int endOfExpression(int start) {
        // E an expression, S and D a string in single and double quotes, T and U one in three of them
        Deque<Character> states = new ArrayDeque<>()
        states.push((char) 'E')
        char previous = 0
        char beforePrevious = 0
        for (int position = start; position < length; position++) {
            char state = states.peek()
            char c = source.charAt(position)
            char next = charAt(position + 1)
            if (states.size() == 1 && c == (char) '}') {
                return position + 1
            }
            if (state == (char) 'E') {
                if (c == (char) '{') {
                    if (previous == (char) '$') {
                        return UNCLOSED
                    }
                    states.push((char) 'E')
                } else if (c == (char) '[') {
                    states.push((char) 'E')
                } else if (c == (char) '}' || c == (char) ']') {
                    if (states.size() == 1) {
                        // a list that was never opened
                        return UNCLOSED
                    }
                    states.pop()
                } else if (c == (char) '\'' || c == (char) '"') {
                    if (next != c && previous != c) {
                        states.push(c == (char) '"' ? (char) 'D' : (char) 'S')
                    } else if (previous == c && beforePrevious == c) {
                        states.push(c == (char) '"' ? (char) 'U' : (char) 'T')
                    }
                }
            } else if (c == (char) '"' || c == (char) '\'') {
                if (next != c && (previous != c || beforePrevious == (char) '\\') &&
                        (previous != (char) '\\' || beforePrevious == (char) '\\') &&
                        ((state == (char) 'D' && c == (char) '"') || (state == (char) 'S' && c == (char) '\''))) {
                    states.pop()
                } else if (previous == c && beforePrevious == c &&
                        ((state == (char) 'U' && c == (char) '"') || (state == (char) 'T' && c == (char) '\''))) {
                    states.pop()
                }
            }
            beforePrevious = previous
            previous = c
        }
        return UNCLOSED
    }
}
