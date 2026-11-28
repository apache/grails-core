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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.grails.taglib.GrailsTagException;

/**
 * NOTE: Based on work done by on the GSP standalone project (https://gsp.dev.java.net/)
 *
 * Lexer for GroovyPagesServlet.
 *
 * @author Troy Heninger
 * @author Graeme Rocher
 *
 * Date: Jan 10, 2004
 */
class GroovyPageScanner implements Tokens {
    private static final int DEFAULT_MAX_HTML_LENGTH = 64000;
    private String text;
    private int end1;
    private int begin1;
    private int end2;
    private int begin2;
    private int state = HTML;
    private int len;
    private String lastNamespace;
    // where each line of the page begins, in the text as it was before any comment was removed from it
    private List<Integer> lineNumberPositions;
    private int lastLineNumberIndex = -1;
    // where comments were removed, in the text as it is now and in the order of the text, and how many
    // characters had been removed up to and including each
    private int[] removedAt = new int[16];
    private int[] removedUpTo = new int[16];
    private int removals;
    private String pageName = "Unknown";
    private int maxHtmlLength = DEFAULT_MAX_HTML_LENGTH;
    private Pattern isTagNameSpacePattern = Pattern.compile("^\\p{Alpha}\\w*$");

    GroovyPageScanner(String text) {
        Strip strip = new Strip(text);
        strip.strip(0);
        this.text = strip.toString();
        len = this.text.length();
        resolveLineNumberPositions();
    }

    public GroovyPageScanner(String gspSource, String uri) {
        this(gspSource);
        this.pageName = uri;
    }

    // add line starting positions to array
    private void resolveLineNumberPositions() {
        lineNumberPositions = new ArrayList<>();
        // first line starts at 0
        lineNumberPositions.add(0);
        // a line break that ends the page starts no line: what is found at the end is on the last line
        for (int i = 0; i < len - 1; i++) {
            if (text.charAt(i) == '\n') {
                // next line starts after LF
                lineNumberPositions.add(i + 1);
            }
        }
    }

    private int found(int newState, int skip) {
        begin2 = begin1;
        end2 = --end1;
        begin1 = end1 += skip;
        int lastState = state;
        state = newState;
        return lastState;
    }

    private int foundStartOrEndTag(int newState, int skip, String namespace) {
        begin2 = begin1;
        end2 = --end1;
        begin1 = end1 += skip;
        int lastState = state;
        state = newState;
        lastNamespace = namespace;
        return lastState;
    }

    String getToken() {
        return text.substring(begin2, end2);
    }

    int getLineNumberForToken() {
        return getLineNumberAt(begin2);
    }

    /**
     * The line of the page a position of the text as it is now is on. The positions are asked for in
     * the order of the text, by {@link #getLineNumberForToken()} for where a token begins and for where
     * an expression begins that does not end.
     */
    private int getLineNumberAt(int positionNow) {
        int position = positionNow + removedBefore(positionNow);
        for (int i = lastLineNumberIndex + 1; i < lineNumberPositions.size(); i++) {
            if (lineNumberPositions.get(i) > position) {
                lastLineNumberIndex = i - 1;
                return i;
            }
        }
        // no line begins after the token: it is on the last one
        return lineNumberPositions.size();
    }

    /**
     * The characters of comments that were removed in front of a position of the text as it is now. What
     * directly follows a removed comment is after it, on the line the comment ended on.
     */
    private int removedBefore(int position) {
        int low = 0;
        int high = removals - 1;
        int removed = 0;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (removedAt[middle] <= position) {
                removed = removedUpTo[middle];
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return removed;
    }

    String getNamespace() {
        return lastNamespace;
    }

    int nextToken() {
        for (;;) {
            int left = len - end1;
            if (left == 0) {
                end1++; // in order to include the last letter
                return found(EOF, 0);
            }
            char c = text.charAt(end1++);
            char c1 = left > 1 ? text.charAt(end1) : 0;
            char c2 = left > 2 ? text.charAt(end1 + 1) : 0;
            int tokenLength = end1 - begin1;

            switch (state) {
                case HTML:
                    if (isPotentialScriptletOrTag(c, left)) {
                        if (c1 == '%') {
                            if (c2 == '=') {
                                return found(JEXPR, 3);
                            }
                            if (c2 == '@') {
                                return found(JDIRECT, 3);
                            }
                            if (c2 == '!') {
                                return found(JDECLAR, 3);
                            }
                            if (isStartComment(c1, c2, left)) {
                                if (skipJComment())
                                    continue;
                            }
                            return found(JSCRIPT, 2);
                        }

                        boolean bStartTag = !isClosingTag(c1);

                        String tagNameSpace = getTagNamespace(bStartTag ? end1 : end1 + 1);
                        if (isTagDefinition(tagNameSpace)) {
                            if (bStartTag) {
                                return foundStartOrEndTag(GSTART_TAG, tagNameSpace.length() + 2, tagNameSpace);
                            }

                            return foundStartOrEndTag(GEND_TAG, tagNameSpace.length() + 3, tagNameSpace);
                        }
                    }
                    else if (isStartOfGExpression(c, c1) && !isEscapedGExpression()) {
                        return found(GEXPR, 2);
                    }

                    if (c == '%' && c1 == '{') {
                        if (c2 == '-' && left > 3 && text.charAt(end1 + 2) == '-') {
                            if (skipGComment())
                                continue;
                        }
                        return found(GSCRIPT, 2);
                    }

                    if (c == '!' && c1 == '{') {
                        return found(GDECLAR, 2);
                    }

                    if (c == '@' && c1 == '{') {
                        return found(GDIRECT, 2);
                    }

                    if (tokenLength > maxHtmlLength && !isInEscapedGExpressionStart(c, c1)) {
                        return found(HTML, 0);
                    }

                    break;
                case JEXPR:
                case JSCRIPT:
                case JDIRECT:
                case JDECLAR:
                    if (c == '%' && c1 == '>') {
                        return found(HTML, 2);
                    }
                    break;
                case GSTART_TAG:
                    if (isStartOfGExpression(c, c1)) {
                        return found(GTAG_EXPR, 2);
                    }
                    if (c == '>') {
                        return found(HTML, 1);
                    }
                    else if (isClosingTag(c) && c1 == '>') {
                        return found(GEND_EMPTY_TAG, 1);
                    }
                    break;
                case GEND_TAG:
                case GEND_EMPTY_TAG:
                    if (c == '>') {
                        return found(HTML, 1);
                    }
                    break;
                case GTAG_EXPR:
                case GEXPR:
                    return parseExpression();
                case GDIRECT:
                    if (c == '}') {
                        return found(HTML, 1);
                    }
                    break;
                case GSCRIPT:
                    if (c == '}' && c1 == '%') {
                        return found(HTML, 2);
                    }
                    break;
                case GDECLAR:
                    if (c == '}' && (c1 == '!' || c1 == '%')) {
                        return found(HTML, 2);
                    }
                    break;
            }
        }
    }

    protected int parseExpression() {
        char terminationChar = '}';
        char nextTerminationChar = 0;
        boolean startInExpression = true;
        int expressionStart = end1 - 1;
        GroovyPageExpressionParser expressionParser = new GroovyPageExpressionParser(text, expressionStart, terminationChar, nextTerminationChar, startInExpression);
        int endpos = expressionParser.parse();
        if (endpos != -1) {
            end1 = endpos + 1;
            int expressionEndState = HTML;
            if (state == GTAG_EXPR) {
                expressionEndState = GSTART_TAG;
            }
            return found(expressionEndState, nextTerminationChar == 0 ? 1 : 2);
        }

        // the expression began two characters before what the parser was given, with its ${
        throw new GrailsTagException("Unclosed GSP expression", pageName, getLineNumberAt(Math.max(expressionStart - 2, begin2)));
    }

    private boolean isClosingTag(char c1) {
        return c1 == '/';
    }

    private boolean isTagDefinition(String tagNameSpace) {
        return tagNameSpace != null && isTagNameSpacePattern.matcher(tagNameSpace).matches();
    }

    private String getTagNamespace(int fromIndex) {
        int foundColonIdx = text.indexOf(":", fromIndex);
        String tagNameSpace = null;
        if (foundColonIdx > -1) {
            tagNameSpace = text.substring(fromIndex, foundColonIdx);
        }
        return tagNameSpace;
    }

    private boolean isPotentialScriptletOrTag(char c, int left) {
        return c == '<' && left > 3;
    }

    private boolean isStartComment(char c1, char c2, int left) {
        return c1 == '%' && c2 == '-' && left > 3 && text.charAt(end1 + 2) == '-';
    }

    private boolean isStartOfGExpression(char c, char c1) {
        return c == '$' && c1 == '{';
    }

    /**
     * A backslash immediately before <code>${</code> escapes the expression in template text: the
     * whole sequence stays in the surrounding HTML token, and {@link GroovyPageParser} drops the
     * backslash when it writes the token out, so the page renders a literal <code>${</code>.
     * Expressions in tag attributes are scanned in {@link Tokens#GSTART_TAG} state and cannot be
     * escaped this way.
     */
    private boolean isEscapedGExpression() {
        return end1 > 1 && text.charAt(end1 - 2) == '\\';
    }

    /**
     * Long text is handed on in pieces, and the backslash of an escaped expression is only taken out of
     * a piece that has the whole of <code>\${</code>: the text is not cut inside one.
     */
    private boolean isInEscapedGExpressionStart(char c, char c1) {
        if (c == '$' && c1 == '{') {
            return isEscapedGExpression();
        }
        return c == '{' && end1 > 2 && text.charAt(end1 - 2) == '$' && text.charAt(end1 - 3) == '\\';
    }

    private boolean skipComment(char c3, char c4) {
        int ix = end1 + 3;
        for (int ixz = len - 4; ; ix++) {
            if (ix > ixz) return false;
            if (text.charAt(ix) == '-' && text.charAt(ix + 1) == '-' && text.charAt(ix + 2) == c3 &&
                    text.charAt(ix + 3) == c4) {
                break;
            }
        }
        int removedFrom = --end1;
        int removed = ix + 4 - removedFrom;
        text = text.substring(0, removedFrom) + text.substring(ix + 4);
        len = text.length();
        // the lines of the comment still count: what follows it is as far on in the page as it was
        if (removals == removedAt.length) {
            removedAt = Arrays.copyOf(removedAt, removals * 2);
            removedUpTo = Arrays.copyOf(removedUpTo, removals * 2);
        }
        removedAt[removals] = removedFrom;
        removedUpTo[removals] = removed + (removals == 0 ? 0 : removedUpTo[removals - 1]);
        removals++;
        return true;
    }

    private boolean skipGComment() {
        return skipComment('}', '%');
    }

    private boolean skipJComment() {
        return skipComment('%', '>');
    }

    void reset() {
        end1 = begin1 = end2 = begin2 = 0;
        state = HTML;
        lastNamespace = null;
        lastLineNumberIndex = -1;
    }

    public int getMaxHtmlLength() {
        return maxHtmlLength;
    }

    public void setMaxHtmlLength(int maxHtmlLength) {
        this.maxHtmlLength = maxHtmlLength;
    }
}
