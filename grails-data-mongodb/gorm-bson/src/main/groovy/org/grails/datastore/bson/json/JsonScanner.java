/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.bson.json;


import org.bson.BsonRegularExpression;
import org.bson.json.JsonParseException;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

/**
 * Parses the string representation of a JSON object into a set of {@link JsonToken}-derived objects.
 *
 * @since 3.0
 */
class JsonScanner {

    private static final char[] NFINITY = new char[]{'n', 'f', 'i', 'n', 'i', 't', 'y'};

    final PushbackReader reader;
    int position;


    /**
     * Constructs a a new {@code JSONScanner} that produces values scanned from specified {@code JSONBuffer}.
     *
     * @param reader A reader to be scanned.
     */
    public JsonScanner(final Reader reader) {
        this.reader = new PushbackReader(reader);
    }


    /**
     * Finds and returns the next complete token from this scanner. If scanner reached the end of the source, it will return a token with
     * {@code JSONTokenType.END_OF_FILE} type.
     *
     * @return The next token.
     * @throws JsonParseException if source is invalid.
     */
    public JsonToken nextToken() throws IOException {

        int c = readCharacter();
        while (c != -1 && Character.isWhitespace(c)) {
            c = readCharacter();
        }
        if (c == -1) {
            return new JsonToken(JsonTokenType.END_OF_FILE, "<eof>");
        }

        switch (c) {
            case JsonToken.OPEN_BRACE:
                return new JsonToken(JsonTokenType.BEGIN_OBJECT, JsonToken.OPEN_BRACE);
            case JsonToken.CLOSE_BRACE:
                return new JsonToken(JsonTokenType.END_OBJECT, JsonToken.CLOSE_BRACE);
            case JsonToken.OPEN_BRACKET:
                return new JsonToken(JsonTokenType.BEGIN_ARRAY, JsonToken.OPEN_BRACKET);
            case JsonToken.CLOSE_BRACKET:
                return new JsonToken(JsonTokenType.END_ARRAY, JsonToken.CLOSE_BRACKET);
            case JsonToken.OPEN_PARENS:
                return new JsonToken(JsonTokenType.LEFT_PAREN, JsonToken.OPEN_PARENS);
            case JsonToken.CLOSE_PARENS:
                return new JsonToken(JsonTokenType.RIGHT_PAREN, JsonToken.CLOSE_PARENS);
            case JsonToken.COLON:
                return new JsonToken(JsonTokenType.COLON, JsonToken.CLOSE_BRACKET);
            case JsonToken.COMMA:
                return new JsonToken(JsonTokenType.COMMA, JsonToken.COMMA);
            case JsonToken.BACK_SLASH:
            case JsonToken.QUOTE:
                return scanString((char) c);
            case JsonToken.FORWARD_SLASH:
                return scanRegularExpression();
            default:
                if (c == JsonToken.MINUS || Character.isDigit(c)) {
                    return scanNumber((char) c);
                } else if (c == '$' || c == '_' || Character.isLetter(c)) {
                    return scanUnquotedString((char)c);
                } else {
                    reader.unread(c);
                    throw new JsonParseException("Invalid JSON input. Position: %d. Character: '%c'.", position, c);
                }
        }
    }

    protected int readCharacter() throws IOException {
        position++;
        return reader.read();
    }

    /**
     * Reads {@code RegularExpressionToken} from source. The following variants of lexemes are possible:
     * <pre>
     *  /pattern/
     *  /\(pattern\)/
     *  /pattern/ims
     * </pre>
     * Options can include 'i','m','x','s'
     *
     * @return The regular expression token.
     * @throws JsonParseException if regular expression representation is not valid.
     */
    private JsonToken scanRegularExpression() throws IOException {

        JsonScanner.RegularExpressionState state = JsonScanner.RegularExpressionState.IN_PATTERN;
        StringBuilder optionsBuilder = new StringBuilder();
        StringBuilder regexBuilder = new StringBuilder();
        while (true) {
            int c = readCharacter();
            switch (state) {
                case IN_PATTERN:
                    switch (c) {
                        case JsonToken.FORWARD_SLASH:
                            state = JsonScanner.RegularExpressionState.IN_OPTIONS;
                            break;
                        case JsonToken.BACK_SLASH:
                            state = JsonScanner.RegularExpressionState.IN_ESCAPE_SEQUENCE;
                            regexBuilder.append((char)c);
                            break;
                        default:
                            state = JsonScanner.RegularExpressionState.IN_PATTERN;
                            regexBuilder.append((char)c);
                            break;
                    }
                    break;
                case IN_ESCAPE_SEQUENCE:
                    state = RegularExpressionState.IN_PATTERN;
                    regexBuilder.append((char)c);
                    break;
                case IN_OPTIONS:
                    switch (c) {
                        case 'i':
                        case 'm':
                        case 'x':
                        case 's':
                            state = JsonScanner.RegularExpressionState.IN_OPTIONS;
                            optionsBuilder.append((char)c);
                            break;
                        case JsonToken.COMMA:
                        case JsonToken.CLOSE_BRACE:
                        case JsonToken.CLOSE_BRACKET:
                        case JsonToken.CLOSE_PARENS:
                        case -1:
                            state = JsonScanner.RegularExpressionState.DONE;
                            break;
                        default:
                            if (Character.isWhitespace(c)) {
                                state = JsonScanner.RegularExpressionState.DONE;
                            } else {
                                state = JsonScanner.RegularExpressionState.INVALID;
                            }
                            break;
                    }
                    break;
                default:
            }

            switch (state) {
                case DONE:
                    reader.unread(c);
                    BsonRegularExpression regex
                            = new BsonRegularExpression(regexBuilder.toString(), optionsBuilder.toString());
                    return new JsonToken(JsonTokenType.REGULAR_EXPRESSION, regex);
                case INVALID:
                    throw new JsonParseException("Invalid JSON regular expression. Position: %d.", position);
                default:
            }
        }
    }

    /**
     * Reads {@code StringToken} from source.
     *
     * @return The string token.
     * @param startChar
     */
    private JsonToken scanUnquotedString(char startChar) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append(startChar);
        int c = readCharacter();
        while (c == '$' || c == '_' || Character.isLetterOrDigit(c)) {
            builder.append((char)c);
            c = readCharacter();
        }
        reader.unread(c);
        String lexeme = builder.toString();
        return new JsonToken(JsonTokenType.UNQUOTED_STRING, lexeme);
    }

    /**
     * Reads number token from source. The following variants of lexemes are possible:
     * <pre>
     *  12
     *  123
     *  -0
     *  -345
     *  -0.0
     *  0e1
     *  0e-1
     *  -0e-1
     *  1e12
     *  -Infinity
     * </pre>
     *
     * @return The number token.
     * @throws JsonParseException if number representation is invalid.
     */
    //CHECKSTYLE:OFF
    private JsonToken scanNumber(final char firstChar) throws IOException {

        int c = firstChar;

        JsonScanner.NumberState state;

        StringBuilder numberBuilder = new StringBuilder();
        numberBuilder.append(firstChar);
        switch (c) {
            case '-':
                state = JsonScanner.NumberState.SAW_LEADING_MINUS;
                break;
            case '0':
                state = JsonScanner.NumberState.SAW_LEADING_ZERO;
                break;
            default:
                state = JsonScanner.NumberState.SAW_INTEGER_DIGITS;
                break;
        }

        JsonTokenType type = JsonTokenType.INT64;


        while (true) {
            c = readCharacter();

            switch (state) {
                case SAW_LEADING_MINUS:
                    switch (c) {
                        case '0':
                            numberBuilder.append((char)c);
                            state = JsonScanner.NumberState.SAW_LEADING_ZERO;
                            break;
                        case 'I':
                            numberBuilder.append((char)c);
                            state = JsonScanner.NumberState.SAW_MINUS_I;
                            break;
                        default:
                            numberBuilder.append((char)c);
                            if (Character.isDigit(c)) {
                                state = JsonScanner.NumberState.SAW_INTEGER_DIGITS;
                            } else {
                                state = JsonScanner.NumberState.INVALID;
                            }
                            break;
                    }
                    break;
                case SAW_LEADING_ZERO:
                case SAW_INTEGER_DIGITS:
                    switch (c) {
                        case '.':
                            numberBuilder.append((char)c);
                            state = JsonScanner.NumberState.SAW_DECIMAL_POINT;
                            break;
                        case 'e':
                        case 'E':
                            numberBuilder.append((char)c);
                            state = JsonScanner.NumberState.SAW_EXPONENT_LETTER;
                            break;
                        case JsonToken.COMMA:
                        case JsonToken.CLOSE_BRACE:
                        case JsonToken.CLOSE_BRACKET:
                        case JsonToken.CLOSE_PARENS:
                        case -1:
                            state = JsonScanner.NumberState.DONE;
                            break;
                        default:
                            if (Character.isDigit(c)) {
                                numberBuilder.append((char)c);
                                state = JsonScanner.NumberState.SAW_INTEGER_DIGITS;
                            } else if (Character.isWhitespace(c)) {
                                state = JsonScanner.NumberState.DONE;
                            } else {
                                state = JsonScanner.NumberState.INVALID;
                            }
                            break;
                    }
                    break;
                case SAW_DECIMAL_POINT:
                    type = JsonTokenType.DOUBLE;
                    if (Character.isDigit(c)) {
                        numberBuilder.append((char)c);
                        state = JsonScanner.NumberState.SAW_FRACTION_DIGITS;
                    } else {
                        state = JsonScanner.NumberState.INVALID;
                    }
                    break;
                case SAW_FRACTION_DIGITS:
                    switch (c) {
                        case 'e':
                        case 'E':
                            numberBuilder.append((char)c);
                            state = JsonScanner.NumberState.SAW_EXPONENT_LETTER;
                            break;
                        case JsonToken.COMMA:
                        case JsonToken.CLOSE_BRACE:
                        case JsonToken.CLOSE_BRACKET:
                        case JsonToken.CLOSE_PARENS:
                        case -1:
                            state = JsonScanner.NumberState.DONE;
                            break;
                        default:
                            if (Character.isDigit(c)) {
                                numberBuilder.append((char)c);
                                state = JsonScanner.NumberState.SAW_FRACTION_DIGITS;
                            } else if (Character.isWhitespace(c)) {
                                state = JsonScanner.NumberState.DONE;
                            } else {
                                state = JsonScanner.NumberState.INVALID;
                            }
                            break;
                    }
                    break;
                case SAW_EXPONENT_LETTER:
                    type = JsonTokenType.DOUBLE;
                    switch (c) {
                        case '+':
                        case '-':
                            numberBuilder.append((char)c);
                            state = JsonScanner.NumberState.SAW_EXPONENT_SIGN;
                            break;
                        default:
                            if (Character.isDigit(c)) {
                                numberBuilder.append((char)c);
                                state = JsonScanner.NumberState.SAW_EXPONENT_DIGITS;
                            } else {
                                state = JsonScanner.NumberState.INVALID;
                            }
                            break;
                    }
                    break;
                case SAW_EXPONENT_SIGN:
                    if (Character.isDigit(c)) {
                        numberBuilder.append((char)c);
                        state = JsonScanner.NumberState.SAW_EXPONENT_DIGITS;
                    } else {
                        state = JsonScanner.NumberState.INVALID;
                    }
                    break;
                case SAW_EXPONENT_DIGITS:
                    switch (c) {
                        case JsonToken.COMMA:
                        case JsonToken.CLOSE_BRACE:
                        case JsonToken.CLOSE_BRACKET:
                        case JsonToken.CLOSE_PARENS:
                            state = JsonScanner.NumberState.DONE;
                            break;
                        default:
                            if (Character.isDigit(c)) {
                                numberBuilder.append((char)c);
                                state = JsonScanner.NumberState.SAW_EXPONENT_DIGITS;
                            } else if (Character.isWhitespace(c)) {
                                state = JsonScanner.NumberState.DONE;
                            } else {
                                state = JsonScanner.NumberState.INVALID;
                            }
                            break;
                    }
                    break;
                case SAW_MINUS_I:
                    boolean sawMinusInfinity = true;
                    numberBuilder.append((char)c);
                    for (int i = 0; i < NFINITY.length; i++) {
                        if (c != NFINITY[i]) {
                            sawMinusInfinity = false;
                            break;
                        }
                        c = readCharacter();
                        numberBuilder.append((char)c);
                    }
                    if (sawMinusInfinity) {
                        type = JsonTokenType.DOUBLE;
                        switch (c) {
                            case JsonToken.COMMA:
                            case JsonToken.CLOSE_BRACE:
                            case JsonToken.CLOSE_BRACKET:
                            case JsonToken.CLOSE_PARENS:
                            case -1:
                                state = JsonScanner.NumberState.DONE;
                                break;
                            default:
                                if (Character.isWhitespace(c)) {
                                    state = JsonScanner.NumberState.DONE;
                                } else {
                                    state = JsonScanner.NumberState.INVALID;
                                }
                                break;
                        }
                    } else {
                        state = JsonScanner.NumberState.INVALID;
                    }
                    break;
                default:
            }

            switch (state) {
                case INVALID:
                    throw new JsonParseException("Invalid JSON number");
                case DONE:
                    reader.unread(c);
                    if (type == JsonTokenType.DOUBLE) {
                        return new JsonToken(JsonTokenType.DOUBLE, Double.parseDouble(numberBuilder.toString()));
                    } else {
                        long value = Long.parseLong(numberBuilder.toString());
                        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                            return new JsonToken(JsonTokenType.INT64, value);
                        } else {
                            return new JsonToken(JsonTokenType.INT32, (int) value);
                        }
                    }
                default:
            }
        }

    }
    //CHECKSTYLE:ON

    /**
     * Reads {@code StringToken} from source.
     *
     * @return The string token.
     */
    //CHECKSTYLE:OFF
    private JsonToken scanString(final char quoteCharacter) throws IOException {

        StringBuilder sb = new StringBuilder();

        while (true) {
            int c = readCharacter();
            switch (c) {
                case JsonToken.BACK_SLASH:
                    c = readCharacter();
                    switch (c) {
                        case '\'':
                            sb.append('\'');
                            break;
                        case JsonToken.QUOTE:
                            sb.append(JsonToken.QUOTE);
                            break;
                        case JsonToken.BACK_SLASH:
                            sb.append(JsonToken.BACK_SLASH);
                            break;
                        case JsonToken.FORWARD_SLASH:
                            sb.append(JsonToken.FORWARD_SLASH);
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            int u1 = readCharacter();
                            int u2 = readCharacter();
                            int u3 = readCharacter();
                            int u4 = readCharacter();
                            if (u4 != -1) {
                                String hex = new String(new char[]{(char) u1, (char) u2, (char) u3, (char) u4});
                                sb.append((char) Integer.parseInt(hex, 16));
                            }
                            break;
                        default:
                            throw new JsonParseException("Invalid escape sequence in JSON string '\\%c'.", c);
                    }
                    break;

                default:
                    if (c == quoteCharacter) {
                        return new JsonToken(JsonTokenType.STRING, sb.toString());
                    }
                    if (c != -1) {
                        sb.append((char) c);
                    }
            }
            if (c == -1) {
                throw new JsonParseException("End of file in JSON string.");
            }
        }
    }

    private enum NumberState {
        SAW_LEADING_MINUS,
        SAW_LEADING_ZERO,
        SAW_INTEGER_DIGITS,
        SAW_DECIMAL_POINT,
        SAW_FRACTION_DIGITS,
        SAW_EXPONENT_LETTER,
        SAW_EXPONENT_SIGN,
        SAW_EXPONENT_DIGITS,
        SAW_MINUS_I,
        DONE,
        INVALID
    }

    private enum RegularExpressionState {
        IN_PATTERN,
        IN_ESCAPE_SEQUENCE,
        IN_OPTIONS,
        DONE,
        INVALID
    }
}
