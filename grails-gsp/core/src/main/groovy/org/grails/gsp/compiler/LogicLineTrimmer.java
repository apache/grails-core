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
package org.grails.gsp.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes the lines of a page that hold only template logic, for a page declaring
 * {@code trimLogicLines="true"}: a line holding nothing but page directives, scriptlets and comments,
 * besides spaces and tabs, writes neither its indentation nor its line break. A line holding text, an
 * expression, a declaration or a tag is left as it is.
 *
 * <p>The page is walked the way {@link GroovyPageScanner} reads it, so only a line of the page's
 * text can be removed: text inside an expression, a tag, a scriptlet or a comment is never touched,
 * whatever it looks like.</p>
 *
 * <p>The line break of a removed line is moved inside the line's last construct, just after its
 * opening delimiter, rather than dropped, so that every line of the page keeps its number in the
 * compiled page and in the errors reported against it.</p>
 */
final class LogicLineTrimmer {

    private static final Pattern TAG_NAMESPACE_PATTERN = Pattern.compile("^\\p{Alpha}\\w*$");

    private static final int UNCLOSED = -1;

    private final String source;
    private final int length;

    private LogicLineTrimmer(String source) {
        this.source = source;
        this.length = source.length();
    }

    static String trim(String gspSource) {
        return new LogicLineTrimmer(gspSource).trim();
    }

    private String trim() {
        StringBuilder result = new StringBuilder(length);
        int copied = 0;
        int position = 0;
        while (position < length) {
            if (position == 0 || source.charAt(position - 1) == '\n') {
                int lineEnd = trimLogicLine(position, result, copied);
                if (lineEnd != position) {
                    copied = position = lineEnd;
                    continue;
                }
            }
            Construct construct = constructAt(position);
            if (construct == null) {
                position++;
            } else if (construct.end() == UNCLOSED) {
                // left for the parser to report
                break;
            } else {
                position = construct.end();
            }
        }
        result.append(source, copied, length);
        return result.toString();
    }

    /**
     * Writes the line starting at {@code lineStart} without its blanks if it holds only template
     * logic, the line break going just after the opening delimiter of its last construct.
     *
     * @return where the line ends, after its line break, or {@code lineStart} when it is not a logic line
     */
    private int trimLogicLine(int lineStart, StringBuilder result, int copied) {
        List<Construct> constructs = new ArrayList<>();
        int position = skipBlanks(lineStart);
        String lineBreak = null;
        while (lineBreak == null) {
            Construct construct = constructAt(position);
            if (construct == null || construct.end() == UNCLOSED || !construct.logic()) {
                return lineStart;
            }
            constructs.add(construct);
            position = skipBlanks(construct.end());
            if (position == length) {
                lineBreak = "";
            } else if (source.startsWith("\r\n", position)) {
                lineBreak = "\r\n";
            } else if (source.charAt(position) == '\n') {
                lineBreak = "\n";
            }
        }
        result.append(source, copied, lineStart);
        Construct last = constructs.get(constructs.size() - 1);
        for (Construct construct : constructs) {
            if (construct == last) {
                int opening = construct.start() + construct.openingLength();
                result.append(source, construct.start(), opening).append(lineBreak).append(source, opening, construct.end());
            } else {
                result.append(source, construct.start(), construct.end());
            }
        }
        return position + lineBreak.length();
    }

    private int skipBlanks(int position) {
        while (position < length && (source.charAt(position) == ' ' || source.charAt(position) == '\t')) {
            position++;
        }
        return position;
    }

    /**
     * Reads the construct that {@link GroovyPageScanner} would start at {@code position} of the page's
     * text, or returns {@code null} when the text goes on there.
     */
    private Construct constructAt(int position) {
        char c = source.charAt(position);
        char c1 = charAt(position + 1);
        char c2 = charAt(position + 2);
        if (c == '<' && length - position > 3) {
            if (c1 == '%') {
                if (c2 == '=' || c2 == '!') {
                    return new Construct(position, endAfter("%>", position + 3), 3, false);
                }
                if (c2 == '@') {
                    return new Construct(position, endAfter("%>", position + 3), 3, true);
                }
                if (c2 == '-' && charAt(position + 3) == '-') {
                    int end = endAfter("--%>", position + 4);
                    if (end != UNCLOSED) {
                        return new Construct(position, end, 4, true);
                    }
                }
                return new Construct(position, endAfter("%>", position + 2), 2, true);
            }
            return tagAt(position);
        }
        if (c == '$' && c1 == '{' && (position == 0 || source.charAt(position - 1) != '\\')) {
            return new Construct(position, endOfExpression(position + 2), 2, false);
        }
        if (c == '%' && c1 == '{') {
            if (c2 == '-' && charAt(position + 3) == '-') {
                int end = endAfter("--}%", position + 4);
                if (end != UNCLOSED) {
                    return new Construct(position, end, 4, true);
                }
            }
            return new Construct(position, endAfter("}%", position + 2), 2, true);
        }
        if (c == '!' && c1 == '{') {
            int end = position + 2;
            while (end < length - 1 && !(source.charAt(end) == '}' && (source.charAt(end + 1) == '!' || source.charAt(end + 1) == '%'))) {
                end++;
            }
            return new Construct(position, end < length - 1 ? end + 2 : UNCLOSED, 2, false);
        }
        if (c == '@' && c1 == '{') {
            return new Construct(position, endAfter("}", position + 2), 2, false);
        }
        return null;
    }

    /**
     * Reads a start or end tag of a tag library, whose attributes may hold expressions.
     */
    private Construct tagAt(int position) {
        boolean startTag = source.charAt(position + 1) != '/';
        int namespaceStart = startTag ? position + 1 : position + 2;
        int colon = source.indexOf(':', namespaceStart);
        if (colon == -1 || !TAG_NAMESPACE_PATTERN.matcher(source.substring(namespaceStart, colon)).matches()) {
            return null;
        }
        if (!startTag) {
            return new Construct(position, endAfter(">", colon + 1), 0, false);
        }
        int end = colon + 1;
        while (end < length) {
            char c = source.charAt(end);
            if (c == '$' && charAt(end + 1) == '{') {
                end = endOfExpression(end + 2);
                if (end == UNCLOSED) {
                    break;
                }
            } else if (c == '>') {
                return new Construct(position, end + 1, 0, false);
            } else {
                end++;
            }
        }
        return new Construct(position, UNCLOSED, 0, false);
    }

    private int endOfExpression(int expressionStart) {
        int closing = new GroovyPageExpressionParser(source, expressionStart, '}', (char) 0, true).parse();
        return closing == -1 ? UNCLOSED : closing + 1;
    }

    private int endAfter(String delimiter, int from) {
        int found = source.indexOf(delimiter, from);
        return found == -1 ? UNCLOSED : found + delimiter.length();
    }

    private char charAt(int position) {
        return position < length ? source.charAt(position) : 0;
    }

    /**
     * A construct of the page from {@code start} to {@code end}, {@link #UNCLOSED} when it never
     * ends; a {@code logic} one writes nothing, so a line may be removed for it.
     */
    private record Construct(int start, int end, int openingLength, boolean logic) {
    }
}
