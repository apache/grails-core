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
 * Makes of the source of a template of a feature pack the page that is compiled: one in which the
 * only things that are read are the instructions to the generator, and everything else is text.
 *
 * <p>A GSP page reads more in its text than a template has use for. What the generator reads in a
 * template is:</p>
 * <ul>
 * <li>an expression, <code>${ }</code>;</li>
 * <li>a scriptlet, a directive or a comment that starts with {@code <%}: {@code <% %>},
 * {@code <%= %>}, {@code <%@ %>}, {@code <%-- --%>};</li>
 * <li>a scriptlet or a comment that starts with <code>%{</code>: <code>%{ }%</code>,
 * <code>%{-- --}%</code>;</li>
 * <li>the tags of {@link #INSTRUCTIONS} in the namespace {@code g}, which are the tags the GSP
 * compiler compiles into a page.</li>
 * </ul>
 *
 * <p>Everything else a page would read is made text here, by having the page write its first
 * character with a scriptlet, so that the page writes it as the template has it:</p>
 * <ul>
 * <li>every other tag {@code <prefix:name>}, start or end, of any namespace: a page compiled
 * without tag libraries would evaluate its attributes and write it anew. A tag named like an
 * instruction in another namespace is one of these;</li>
 * <li>the short directive <code>@{ }</code>, which the views scaffolding generates start with, and the
 * declaration <code>!{ }!</code>, which a page refuses;</li>
 * <li>the form <code>^name%{ }%</code>, for which a page takes the HTML tag around it out, where it
 * is in the text.</li>
 * </ul>
 *
 * <p>Backslashes directly in front of what the generator reads, and in front of any tag, are an
 * escape, two by two: each two of them write one, and one that is left over has what follows written
 * and not read, and is not written itself. So {@code \<%} writes {@code <%}, {@code \\<%} writes a
 * backslash and then has the scriptlet run, and {@code \<g:link>} writes the tag as {@code <g:link>}
 * does, which makes it safe to escape every tag of a view. A backslash anywhere else is a backslash.
 * What is written so is all of what the backslash is in front of: <code>\<%{</code> writes
 * <code>&lt;%{</code>, and its percent sign and brace do not start a scriptlet of their own.</p>
 *
 * <p>Two attributes of an instruction are refused, because a template with them does not do what it
 * says: {@code env}, which would ask for the environment the generator runs in and not for that of
 * the application, and a {@code test} that is not one expression, which is text and so always true.</p>
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

    // the instructions that run what they hold when their test is true
    private static final Set<String> TESTED = Set.of('if', 'elseif', 'unless', 'while')
    private static final String TEST = 'test'
    private static final String ENVIRONMENT = 'env'
    private static final int UNCLOSED = -1
    private static final char BACKSLASH = (char) '\\'

    private final String source
    private final int length
    private final StringBuilder page
    private int copied
    // where the text begins that the backslashes in front of a construct can be of
    private int textStart

    private PackTemplateSource(String source) {
        this.source = source
        this.length = source.length()
        this.page = new StringBuilder(length + 64)
    }

    /**
     * @param source the source of a template
     * @return the page to compile for it
     * @throws Refused when the template has an instruction with what it cannot have
     */
    static String toPage(String source) {
        return new PackTemplateSource(source).walk()
    }

    private String walk() {
        int position = 0
        while (position < length && position != UNCLOSED) {
            int next = next(position)
            if (next != position + 1 && next != UNCLOSED) {
                textStart = next
            }
            position = next
        }
        page.append(source, copied, length)
        return page.toString()
    }

    /**
     * @return where the text goes on after what is at the position, or {@link #UNCLOSED} when what
     * starts there does not end, which is left for the compiler to say
     */
    @SuppressWarnings(['CyclomaticComplexity', 'AbcMetric'])
    private int next(int position) {
        char c = source.charAt(position)
        char c1 = charAt(position + 1)
        if (c == (char) '<' && length - position > 3) {
            if (c1 == (char) '%') {
                if (isWritten(position, (char) '<')) {
                    if (charAt(position + 2) == (char) '{') {
                        // the percent sign that is written with it would start a scriptlet of its own with the brace
                        write(position + 1, 1, c1)
                        return position + 2
                    }
                    return position + 1
                }
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
                if (isWritten(position, (char) '<')) {
                    return position + 1
                }
                if (isInstruction(position, colon)) {
                    return endOfInstruction(position, colon)
                }
                // text from here on: what is in the tag is read as anything else in the text is
                write(position, 1, (char) '<')
            }
            return position + 1
        }
        if (c == (char) '$' && c1 == (char) '{') {
            if (isWritten(position, (char) '$')) {
                return position + 1
            }
            return endOfExpression(position + 2)
        }
        if (c == (char) '%' && c1 == (char) '{') {
            if (isWritten(position, (char) '%')) {
                return position + 1
            }
            if (charAt(position + 2) == (char) '-' && charAt(position + 3) == (char) '-') {
                int end = endAfter('--}%', position + 4)
                if (end != UNCLOSED) {
                    return end
                }
            }
            return endAfter('}%', position + 2)
        }
        if ((c == (char) '!' || c == (char) '@') && c1 == (char) '{') {
            // a declaration and a short directive of a page: text of a template
            write(position, 1, c)
            return position + 1
        }
        if (c == (char) '^' && isStrippingForm(position)) {
            write(position, 1, c)
            return position + 1
        }
        return position + 1
    }

    /**
     * Takes the backslashes in front of what the generator reads at the position for the escape they
     * are: each two of them are written as one, and with one left over, what is at the position is
     * written and not read.
     *
     * @param first the character at the position
     * @return whether what is at the position is written, and the text goes on after its first character
     */
    private boolean isWritten(int position, char first) {
        int backslashes = position
        while (backslashes > textStart && source.charAt(backslashes - 1) == BACKSLASH) {
            backslashes--
        }
        int count = position - backslashes
        if (count == 0) {
            return false
        }
        page.append(source, copied, backslashes)
        copied = position
        boolean written = count % 2 == 1
        if (count > 1) {
            // by a scriptlet: a page takes a backslash in front of an expression for an escape of it, also
            // one that comes to stand there when the comment between them is left out
            page.append("<%='").append('\\\\' * count.intdiv(2)).append("'%>")
        }
        if (written) {
            write(position, 1, first)
        }
        return written
    }

    /**
     * Has the page write a character itself where the source has it, so that it is not read with what
     * follows it.
     */
    private void write(int position, int count, char written) {
        page.append(source, copied, position).append("<%='").append(written).append("'%>")
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
        return source.substring(namespaceStart, colon) == NAMESPACE && INSTRUCTIONS.contains(source.substring(colon + 1, endOfName(colon + 1)))
    }

    private int endOfName(int start) {
        int end = start
        while (end < length) {
            char c = source.charAt(end)
            if (Character.isWhitespace(c) || c == (char) '>' || (c == (char) '/' && charAt(end + 1) == (char) '>') ||
                    (c == (char) '$' && charAt(end + 1) == (char) '{')) {
                break
            }
            end++
        }
        return end
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
                checkAttributes(position, colon, end)
                return end + 1
            } else {
                end++
            }
        }
        return UNCLOSED
    }

    /**
     * Refuses the attributes of an instruction that do not do in a template what they say.
     */
    private void checkAttributes(int position, int colon, int end) {
        int nameEnd = endOfName(colon + 1)
        String name = source.substring(colon + 1, nameEnd)
        int at = nameEnd
        while (at < end) {
            while (at < end && !isWord(source.charAt(at))) {
                at++
            }
            int attributeStart = at
            while (at < end && isWord(source.charAt(at))) {
                at++
            }
            String attribute = source.substring(attributeStart, at)
            while (at < end && Character.isWhitespace(source.charAt(at))) {
                at++
            }
            if (at >= end || source.charAt(at) != (char) '=') {
                continue
            }
            at++
            while (at < end && Character.isWhitespace(source.charAt(at))) {
                at++
            }
            char quote = charAt(at)
            if (at >= end || (quote != (char) '"' && quote != (char) '\'')) {
                continue
            }
            int valueStart = ++at
            while (at < end && source.charAt(at) != quote) {
                at = source.charAt(at) == (char) '$' && charAt(at + 1) == (char) '{' ? endOfExpression(at + 2) : at + 1
                if (at == UNCLOSED) {
                    return
                }
            }
            if (attribute == ENVIRONMENT) {
                throw new Refused(lineOf(position), "the instruction g:${name} has the attribute env, which a template cannot have: it would ask for the " +
                        'environment the generator runs in, and that of the application is not known when it is generated')
            }
            if (attribute == TEST && TESTED.contains(name) &&
                    !(source.startsWith('${', valueStart) && endOfExpression(valueStart + 2) == at)) {
                throw new Refused(lineOf(position), "the test of the instruction g:${name} has to be one expression, test=\"\${...}\": " +
                        'anything else is text, which is always true')
            }
            at++
        }
    }

    private int lineOf(int position) {
        int line = 1
        for (int i = 0; i < position; i++) {
            if (source.charAt(i) == (char) '\n') {
                line++
            }
        }
        return line
    }

    /**
     * @return whether a page would take the HTML tag around the position out of its text: it does for
     * a circumflex, letters and a scriptlet in braces, <code>^p%{ }%</code>
     */
    private boolean isStrippingForm(int position) {
        int letters = position + 1
        while (letters < length && Character.isLetter(source.charAt(letters)) && source.charAt(letters) < (char) 128) {
            letters++
        }
        return letters > position + 1 && source.startsWith('%{', letters)
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
                    // a bracket that closes nothing is left for the compiler to refuse
                    if (states.size() > 1) {
                        states.pop()
                    }
                } else if (c == (char) '\'' || c == (char) '"') {
                    if (next != c && previous != c) {
                        states.push(c == (char) '"' ? (char) 'D' : (char) 'S')
                    } else if (previous == c && beforePrevious == c) {
                        states.push(c == (char) '"' ? (char) 'U' : (char) 'T')
                    }
                }
            } else if (c == (char) '"' || c == (char) '\'') {
                if (next != c && (previous != c || beforePrevious == BACKSLASH) &&
                        (previous != BACKSLASH || beforePrevious == BACKSLASH) &&
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

    /**
     * A template the generator does not take: it has an instruction with what one cannot have.
     */
    static final class Refused extends RuntimeException {

        final int line

        Refused(int line, String message) {
            this(line, message, null)
        }

        Refused(int line, String message, Throwable cause) {
            super(message, cause)
            this.line = line
        }
    }
}
