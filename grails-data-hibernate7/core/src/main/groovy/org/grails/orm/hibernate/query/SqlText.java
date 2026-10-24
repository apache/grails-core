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

package org.grails.orm.hibernate.query;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexical reading of SQL or HQL text, so that every scanner of this package skips the same things the same way:
 * string literals and quoted identifiers, where a doubled quote stands for the quote itself; block comments; and, as
 * the {@link SqlSyntax} of the text has them, line comments, {@code [bracket identifiers]}, where {@code ]]} stands for
 * a bracket, {@code $tag$...$tag$} and {@code q'[...]'} strings, backslash escapes and nested block comments.
 * Parentheses, and the square brackets of a syntax without bracket identifiers, nest. The text is read once, when the instance is created: each character is classified as plain text, quoted
 * text or comment, and each plain character carries its nesting depth, so every question asked afterwards walks
 * those two arrays and never rescans the text. The text is scanned, never parsed: this answers where something is,
 * not what it means.
 *
 * @since 8.0.1
 */
final class SqlText {

    private static final byte PLAIN = 0;
    private static final byte QUOTED = 1;
    private static final byte BLOCK_COMMENT = 2;
    private static final byte LINE_COMMENT = 3;

    private static final Pattern DOLLAR_QUOTE_TAG = Pattern.compile("\\$(?:[\\p{L}_][\\p{L}\\p{N}_]*)?\\$");

    private final String text;
    private final SqlSyntax syntax;
    /** The kind of each character: plain text, inside a literal or quoted identifier, or inside a comment. */
    private final byte[] kind;
    /**
     * The nesting depth of each plain character: the depth a {@code (} opens and a {@code )} closes, so the two
     * parentheses of a pair carry the same depth, and the depth enclosing any other character. Square brackets
     * nest the same way where they do not quote an identifier.
     */
    private final int[] depth;

    private SqlText(String text, SqlSyntax syntax) {
        this.text = text;
        this.syntax = syntax;
        int length = text.length();
        this.kind = new byte[length];
        this.depth = new int[length];
        int d = 0;
        int i = 0;
        while (i < length) {
            int after = unitEnd(i);
            if (after > i) {
                byte unitKind = unitKind(i);
                for (int j = i; j < after; j++) {
                    kind[j] = unitKind;
                }
                i = after;
                continue;
            }
            char c = text.charAt(i);
            if (c == '(' || c == '[') {
                d++;
                depth[i] = d;
            } else if (c == ')' || c == ']') {
                depth[i] = d;
                if (d > 0) {
                    d--;
                }
            } else {
                depth[i] = d;
            }
            i++;
        }
    }

    /** Reads the text in the given syntax. */
    static SqlText of(String text, SqlSyntax syntax) {
        return new SqlText(text, syntax);
    }

    /** Reads the text as HQL. */
    static SqlText hql(String text) {
        return new SqlText(text, SqlSyntax.HQL);
    }

    /**
     * Returns the index of {@code keyword} at or after {@code fromIndex}, outside string literals, quoted identifiers,
     * comments and parentheses, where it starts a word: {@code select} in {@code preselect} or in {@code b.select} is
     * part of a name. A keyword that starts with a space starts a word. The keyword is matched as written, so a
     * case-insensitive search passes the text in lower case.
     *
     * @return the index, or -1 when the keyword is not at the top level of the text
     */
    int indexOfTopLevel(String keyword, int fromIndex) {
        for (int i = Math.max(fromIndex, 0); i < text.length(); i++) {
            if (kind[i] == PLAIN && depth[i] == 0 && text.startsWith(keyword, i) && startsWord(i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Splits the text at the separators outside string literals, quoted identifiers, comments and parentheses, and
     * trims each part. A text without the separator is one part.
     */
    List<String> splitTopLevel(char separator) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isTopLevel(i, separator)) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    /** Returns how many times {@code separator} occurs at the top level of the whole text. */
    int countTopLevel(char separator) {
        return countTopLevel(separator, 0, text.length());
    }

    /** Returns how many times {@code separator} occurs at the top level between {@code from} and {@code to}. */
    int countTopLevel(char separator, int from, int to) {
        int count = 0;
        for (int i = Math.max(from, 0); i < Math.min(to, text.length()); i++) {
            if (isTopLevel(i, separator)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the indexes of {@code c} outside string literals, quoted identifiers and comments, at any depth. Where
     * the syntax reads {@code ??} as a literal question mark, a pair of them is not a placeholder.
     */
    List<Integer> plainIndexes(char c) {
        List<Integer> indexes = new ArrayList<>();
        boolean pairEscaped = c == '?' && syntax.has(SqlSyntax.Feature.DOUBLE_QUESTION_MARK_ESCAPE);
        for (int i = 0; i < text.length(); i++) {
            if (kind[i] == PLAIN && text.charAt(i) == c) {
                if (pairEscaped && i + 1 < text.length() && kind[i + 1] == PLAIN && text.charAt(i + 1) == c) {
                    i++;
                    continue;
                }
                indexes.add(i);
            }
        }
        return indexes;
    }

    /** Returns whether the text ends in a line comment, which would comment out anything appended on the same line. */
    boolean endsInLineComment() {
        return text.length() > 0 && kind[text.length() - 1] == LINE_COMMENT;
    }

    /**
     * Returns the text without a trailing {@code as alias} that names the given alias, and without the comments
     * and whitespace after it; or the text as it is when it does not end in that alias. The alias may be quoted the
     * way the syntax quotes an identifier, and then hold any character, and it is compared without its quotes, in
     * any case.
     */
    String withoutTrailingAlias(String alias) {
        int end = endOfContent();
        int aliasStart = startOfTrailingIdentifier(end);
        if (aliasStart < 0 || !alias.equalsIgnoreCase(unquoted(aliasStart, end, true))) {
            return text;
        }
        int asEnd = startOfSeparator(aliasStart);
        if ((asEnd == aliasStart && kind[aliasStart] != QUOTED) || asEnd < 2 || kind[asEnd - 2] != PLAIN ||
                kind[asEnd - 1] != PLAIN || !text.regionMatches(true, asEnd - 2, "as", 0, 2)) {
            return text;
        }
        int asStart = asEnd - 2;
        int expressionEnd = startOfSeparator(asStart);
        // the as is a word of its own: after whitespace or a comment, or right after a closing parenthesis or quote
        if (expressionEnd == 0 || (expressionEnd == asStart && !closesOperand(asStart - 1))) {
            return text;
        }
        return text.substring(0, expressionEnd);
    }

    /** Returns whether the character at {@code index} closes an operand: a parenthesis or bracket, or the end of a quoted text. */
    private boolean closesOperand(int index) {
        return kind[index] == QUOTED || (kind[index] == PLAIN && (text.charAt(index) == ')' || text.charAt(index) == ']'));
    }

    /**
     * Returns the text without the quotes that make all of it one identifier, in which a doubled closing quote
     * stands for the quote; or the text as it is when it is not one quoted identifier, a string literal included,
     * whatever the syntax: only the alias after an {@code as} may be written as a string.
     */
    String unquoted() {
        return unquoted(0, text.length(), false);
    }

    /**
     * Returns the text between {@code start} and {@code end} without the quotes that make all of it one identifier;
     * a string in single quotes is one only for an alias, in the syntaxes that allow it.
     */
    private String unquoted(int start, int end, boolean alias) {
        if (end - start < 2 || kind[start] != QUOTED || unitEnd(start) != end) {
            return text.substring(start, end);
        }
        char opening = text.charAt(start);
        char closing = opening == '[' ? ']' : opening;
        boolean identifier = opening == '"' || opening == '`' ||
                (opening == '[' && syntax.has(SqlSyntax.Feature.BRACKET_IDENTIFIERS)) ||
                (alias && opening == '\'' && syntax.has(SqlSyntax.Feature.STRING_ALIASES));
        if (!identifier || text.charAt(end - 1) != closing) {
            return text.substring(start, end);
        }
        String doubled = String.valueOf(closing) + closing;
        return text.substring(start + 1, end - 1).replace(doubled, String.valueOf(closing));
    }

    /** Returns the index after the last character that is neither whitespace nor part of a comment. */
    private int endOfContent() {
        int end = text.length();
        while (end > 0 && (kind[end - 1] >= BLOCK_COMMENT || (kind[end - 1] == PLAIN && Character.isWhitespace(text.charAt(end - 1))))) {
            end--;
        }
        return end;
    }

    /**
     * Returns the index at which the identifier ending at {@code end} starts: a quoted identifier, or a plain word
     * of identifier characters; or -1 when no identifier ends there.
     */
    private int startOfTrailingIdentifier(int end) {
        if (end == 0) {
            return -1;
        }
        int start = end;
        if (kind[end - 1] == QUOTED) {
            while (start > 0 && kind[start - 1] == QUOTED) {
                start--;
            }
            return start;
        }
        while (start > 0 && kind[start - 1] == PLAIN && isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        return start == end ? -1 : start;
    }

    /**
     * Returns the index at which the whitespace and comments ending at {@code end} start, or {@code end} without any.
     */
    private int startOfSeparator(int end) {
        int start = end;
        while (start > 0 && (kind[start - 1] >= BLOCK_COMMENT ||
                (kind[start - 1] == PLAIN && Character.isWhitespace(text.charAt(start - 1))))) {
            start--;
        }
        return start;
    }

    /**
     * Returns whether the character can be part of a name: a letter or digit, a surrogate half of one outside the
     * basic plane, a combining mark, a joiner, {@code _} or {@code $}, and {@code #} where it does not start a comment.
     */
    private boolean isIdentifierPart(char c) {
        if (Character.isLetterOrDigit(c) || Character.isSurrogate(c) || c == '_' || c == '$' || c == '\u200C' || c == '\u200D') {
            return true;
        }
        int type = Character.getType(c);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK ||
                type == Character.ENCLOSING_MARK || (c == '#' && !syntax.has(SqlSyntax.Feature.HASH_COMMENTS));
    }

    private boolean isTopLevel(int index, char c) {
        return kind[index] == PLAIN && depth[index] == 0 && text.charAt(index) == c;
    }

    private boolean startsWord(int index) {
        if (index == 0 || text.charAt(index) == ' ') {
            return true;
        }
        char previous = text.charAt(index - 1);
        return !Character.isJavaIdentifierPart(previous) && previous != '.';
    }

    /**
     * Returns the index after the string literal, quoted identifier or comment that starts at {@code index}, or
     * {@code index} itself when none starts there and the character is plain text.
     */
    private int unitEnd(int index) {
        char c = text.charAt(index);
        if (c == '\'') {
            if (syntax.has(SqlSyntax.Feature.ALTERNATIVE_QUOTES) && startsAlternativeQuote(index)) {
                return skipAlternativeQuoted(index);
            }
            boolean backslash = syntax.has(SqlSyntax.Feature.BACKSLASH_ESCAPES) ||
                    (syntax.has(SqlSyntax.Feature.ESCAPE_STRINGS) && startsEscapeString(index));
            return skipQuoted(index, c, backslash);
        }
        if (c == '"') {
            return skipQuoted(index, c, syntax.has(SqlSyntax.Feature.BACKSLASH_ESCAPES));
        }
        if (c == '`') {
            return skipQuoted(index, c, false);
        }
        if (c == '-' && startsLineComment(index)) {
            return endOfLine(index);
        }
        if (c == '#' && syntax.has(SqlSyntax.Feature.HASH_COMMENTS)) {
            return endOfLine(index);
        }
        if (c == '/' && syntax.has(SqlSyntax.Feature.DOUBLE_SLASH_COMMENTS) && text.startsWith("//", index)) {
            return endOfLine(index);
        }
        if (c == '/' && text.startsWith("/*", index)) {
            return skipBlockComment(index);
        }
        if (c == '[' && syntax.has(SqlSyntax.Feature.BRACKET_IDENTIFIERS)) {
            return skipBracketIdentifier(index);
        }
        if (c == '$' && (syntax.has(SqlSyntax.Feature.DOLLAR_QUOTES) || syntax.has(SqlSyntax.Feature.UNTAGGED_DOLLAR_QUOTES))) {
            return skipDollarQuoted(index);
        }
        return index;
    }

    /**
     * Returns whether the {@code --} at {@code index} starts a comment: where the syntax has line comments, and, where
     * it needs one, with whitespace or a control character after it.
     */
    private boolean startsLineComment(int index) {
        if (!syntax.has(SqlSyntax.Feature.LINE_COMMENTS) || !text.startsWith("--", index)) {
            return false;
        }
        if (!syntax.has(SqlSyntax.Feature.DASH_COMMENT_NEEDS_SPACE)) {
            return true;
        }
        return index + 2 >= text.length() || Character.isWhitespace(text.charAt(index + 2)) ||
                Character.isISOControl(text.charAt(index + 2));
    }

    /** Returns whether the quote at {@code index} follows an {@code E} that is not part of a longer word. */
    private boolean startsEscapeString(int index) {
        return index > 0 && (text.charAt(index - 1) == 'E' || text.charAt(index - 1) == 'e') && !isWordBefore(index - 1);
    }

    /** Returns whether the quote at {@code index} follows {@code q}, or {@code nq}, standing alone, with a delimiter after it. */
    private boolean startsAlternativeQuote(int index) {
        if (index == 0 || index + 1 >= text.length() || Character.isWhitespace(text.charAt(index + 1))) {
            return false;
        }
        char q = text.charAt(index - 1);
        if (q != 'q' && q != 'Q') {
            return false;
        }
        int before = index - 2;
        if (before >= 0 && (text.charAt(before) == 'n' || text.charAt(before) == 'N')) {
            before--;
        }
        return before < 0 || kind[before] != PLAIN || !isIdentifierPart(text.charAt(before));
    }

    /** Returns whether the character before {@code index} is part of a word, so the character at it is not a prefix. */
    private boolean isWordBefore(int index) {
        return index > 0 && kind[index - 1] == PLAIN && isIdentifierPart(text.charAt(index - 1));
    }

    private byte unitKind(int index) {
        if (text.charAt(index) == '#' || text.startsWith("--", index) || text.startsWith("//", index)) {
            return LINE_COMMENT;
        }
        if (text.startsWith("/*", index)) {
            return BLOCK_COMMENT;
        }
        return QUOTED;
    }

    /**
     * Returns the index after the quoted text starting at {@code start}, where a doubled quote stands for the quote
     * and, where the syntax escapes with a backslash, a backslash stands for the character after it.
     */
    private int skipQuoted(int start, char quote, boolean backslash) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (backslash && c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return text.length();
    }

    /**
     * Returns the index after the block comment starting at {@code start}; where the syntax nests block comments, after
     * the {@code *}{@code /} that closes the outermost one.
     */
    private int skipBlockComment(int start) {
        boolean nested = syntax.has(SqlSyntax.Feature.NESTED_BLOCK_COMMENTS);
        int level = 1;
        int i = start + 2;
        while (i < text.length()) {
            if (text.startsWith("*/", i)) {
                level--;
                i += 2;
                if (level == 0) {
                    return i;
                }
            } else if (nested && text.startsWith("/*", i)) {
                level++;
                i += 2;
            } else {
                i++;
            }
        }
        return text.length();
    }

    /**
     * Returns the index after the Oracle alternative quoted string whose quote is at {@code quote}, such as
     * {@code q'[it's]'}: the delimiter after the quote, with a closing bracket for an opening one, then a quote.
     */
    private int skipAlternativeQuoted(int quote) {
        char opening = text.charAt(quote + 1);
        char closing = switch (opening) {
            case '[' -> ']';
            case '{' -> '}';
            case '(' -> ')';
            case '<' -> '>';
            default -> opening;
        };
        int end = text.indexOf(String.valueOf(closing) + '\'', quote + 2);
        return end == -1 ? text.length() : end + 2;
    }

    /**
     * Returns the index after the bracket identifier starting at {@code start}, which the first {@code ]} not
     * written {@code ]]} closes: {@code [box's width]}, {@code [total (cm)]}, {@code [a]]b]} and {@code [a[b]} are
     * each one identifier, as Transact-SQL reads them.
     */
    private int skipBracketIdentifier(int start) {
        int i = start + 1;
        while (i < text.length()) {
            if (text.charAt(i) == ']') {
                if (text.startsWith("]]", i)) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return i;
    }

    /**
     * Returns the index after the dollar-quoted string starting at {@code start}, such as {@code $$a,b$$} or
     * {@code $tag$a,b$tag$}, or {@code start} itself when no string starts there, as in the identifier {@code a$b}
     * or the parameter {@code $1}.
     */
    private int skipDollarQuoted(int start) {
        char previous = start > 0 ? text.charAt(start - 1) : ' ';
        Matcher tag = DOLLAR_QUOTE_TAG.matcher(text).region(start, text.length());
        boolean afterWord = start > 0 && kind[start - 1] == PLAIN && isIdentifierPart(previous);
        if (afterWord || !tag.lookingAt()) {
            return start;
        }
        if (!syntax.has(SqlSyntax.Feature.DOLLAR_QUOTES) && !"$$".equals(tag.group())) {
            return start;
        }
        int end = text.indexOf(tag.group(), tag.end());
        return end == -1 ? text.length() : end + tag.group().length();
    }

    private int endOfLine(int start) {
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return text.length();
    }
}
