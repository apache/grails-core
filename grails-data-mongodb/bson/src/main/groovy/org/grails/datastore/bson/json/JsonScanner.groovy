/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.bson.json

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.bson.BsonRegularExpression
import org.bson.json.JsonParseException

/**
 * Parses the string representation of a JSON object into a set of {@link JsonToken}-derived objects.
 *
 * <p>The scanner works on {@code int} character codes (so that {@code -1} can mark end of input) and
 * compares them against {@code char} constants with {@code ==}. A Groovy {@code switch} cannot be used
 * for those comparisons: it matches case values with {@code equals()}, and a {@code Character} never
 * equals an {@code Integer}, so the character switches of the original are written as {@code if} chains.</p>
 *
 * @since 3.0
 */
@CompileStatic
@PackageScope
class JsonScanner {

    private static final char[] NFINITY = ['n', 'f', 'i', 'n', 'i', 't', 'y'] as char[]
    private static final char DOLLAR = '$'
    private static final char UNDERSCORE = '_'
    private static final char SINGLE_QUOTE = '\''
    private static final char DOT = '.'
    private static final char PLUS = '+'
    private static final char ZERO = '0'
    private static final char UPPER_I = 'I'
    private static final char LOWER_E = 'e'
    private static final char UPPER_E = 'E'
    private static final int END_OF_INPUT = -1

    @PackageScope final PushbackReader reader
    @PackageScope int position

    /**
     * Constructs a a new {@code JSONScanner} that produces values scanned from specified {@code JSONBuffer}.
     *
     * @param reader A reader to be scanned.
     */
    JsonScanner(final Reader reader) {
        this.reader = new PushbackReader(reader)
    }

    /**
     * Finds and returns the next complete token from this scanner. If scanner reached the end of the source, it will return a token with
     * {@code JSONTokenType.END_OF_FILE} type.
     *
     * @return The next token.
     * @throws JsonParseException if source is invalid.
     */
    JsonToken nextToken() throws IOException {
        int c = readCharacter()
        while (c != END_OF_INPUT && Character.isWhitespace(c)) {
            c = readCharacter()
        }
        if (c == END_OF_INPUT) {
            return new JsonToken(JsonTokenType.END_OF_FILE, '<eof>')
        }

        if (c == JsonToken.OPEN_BRACE) {
            return new JsonToken(JsonTokenType.BEGIN_OBJECT, JsonToken.OPEN_BRACE)
        }
        if (c == JsonToken.CLOSE_BRACE) {
            return new JsonToken(JsonTokenType.END_OBJECT, JsonToken.CLOSE_BRACE)
        }
        if (c == JsonToken.OPEN_BRACKET) {
            return new JsonToken(JsonTokenType.BEGIN_ARRAY, JsonToken.OPEN_BRACKET)
        }
        if (c == JsonToken.CLOSE_BRACKET) {
            return new JsonToken(JsonTokenType.END_ARRAY, JsonToken.CLOSE_BRACKET)
        }
        if (c == JsonToken.OPEN_PARENS) {
            return new JsonToken(JsonTokenType.LEFT_PAREN, JsonToken.OPEN_PARENS)
        }
        if (c == JsonToken.CLOSE_PARENS) {
            return new JsonToken(JsonTokenType.RIGHT_PAREN, JsonToken.CLOSE_PARENS)
        }
        if (c == JsonToken.COLON) {
            return new JsonToken(JsonTokenType.COLON, JsonToken.CLOSE_BRACKET)
        }
        if (c == JsonToken.COMMA) {
            return new JsonToken(JsonTokenType.COMMA, JsonToken.COMMA)
        }
        if (c == JsonToken.BACK_SLASH || c == JsonToken.QUOTE) {
            return scanString((char) c)
        }
        if (c == JsonToken.FORWARD_SLASH) {
            return scanRegularExpression()
        }
        if (c == JsonToken.MINUS || Character.isDigit(c)) {
            return scanNumber((char) c)
        }
        if (c == DOLLAR || c == UNDERSCORE || Character.isLetter(c)) {
            return scanUnquotedString((char) c)
        }
        reader.unread(c)
        throw new JsonParseException("Invalid JSON input. Position: %d. Character: '%c'.", position, c)
    }

    protected int readCharacter() throws IOException {
        position++
        return reader.read()
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
        RegularExpressionState state = RegularExpressionState.IN_PATTERN
        StringBuilder optionsBuilder = new StringBuilder()
        StringBuilder regexBuilder = new StringBuilder()
        while (true) {
            int c = readCharacter()
            switch (state) {
                case RegularExpressionState.IN_PATTERN:
                    if (c == JsonToken.FORWARD_SLASH) {
                        state = RegularExpressionState.IN_OPTIONS
                    } else if (c == JsonToken.BACK_SLASH) {
                        state = RegularExpressionState.IN_ESCAPE_SEQUENCE
                        regexBuilder.append((char) c)
                    } else {
                        state = RegularExpressionState.IN_PATTERN
                        regexBuilder.append((char) c)
                    }
                    break
                case RegularExpressionState.IN_ESCAPE_SEQUENCE:
                    state = RegularExpressionState.IN_PATTERN
                    regexBuilder.append((char) c)
                    break
                case RegularExpressionState.IN_OPTIONS:
                    if (isRegularExpressionOption(c)) {
                        state = RegularExpressionState.IN_OPTIONS
                        optionsBuilder.append((char) c)
                    } else if (isValueTerminator(c)) {
                        state = RegularExpressionState.DONE
                    } else if (Character.isWhitespace(c)) {
                        state = RegularExpressionState.DONE
                    } else {
                        state = RegularExpressionState.INVALID
                    }
                    break
                default:
                    break
            }

            if (state == RegularExpressionState.DONE) {
                reader.unread(c)
                BsonRegularExpression regex = new BsonRegularExpression(regexBuilder.toString(), optionsBuilder.toString())
                return new JsonToken(JsonTokenType.REGULAR_EXPRESSION, regex)
            }
            if (state == RegularExpressionState.INVALID) {
                throw new JsonParseException('Invalid JSON regular expression. Position: %d.', position)
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
        StringBuilder builder = new StringBuilder()
        builder.append(startChar)
        int c = readCharacter()
        while (c == DOLLAR || c == UNDERSCORE || Character.isLetterOrDigit(c)) {
            builder.append((char) c)
            c = readCharacter()
        }
        reader.unread(c)
        String lexeme = builder.toString()
        return new JsonToken(JsonTokenType.UNQUOTED_STRING, lexeme)
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
    private JsonToken scanNumber(final char firstChar) throws IOException {
        int c = (int) firstChar

        NumberState state

        StringBuilder numberBuilder = new StringBuilder()
        numberBuilder.append(firstChar)
        if (c == JsonToken.MINUS) {
            state = NumberState.SAW_LEADING_MINUS
        } else if (c == ZERO) {
            state = NumberState.SAW_LEADING_ZERO
        } else {
            state = NumberState.SAW_INTEGER_DIGITS
        }

        JsonTokenType type = JsonTokenType.INT64

        while (true) {
            c = readCharacter()

            switch (state) {
                case NumberState.SAW_LEADING_MINUS:
                    if (c == ZERO) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_LEADING_ZERO
                    } else if (c == UPPER_I) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_MINUS_I
                    } else {
                        numberBuilder.append((char) c)
                        if (Character.isDigit(c)) {
                            state = NumberState.SAW_INTEGER_DIGITS
                        } else {
                            state = NumberState.INVALID
                        }
                    }
                    break
                case NumberState.SAW_LEADING_ZERO:
                case NumberState.SAW_INTEGER_DIGITS:
                    if (c == DOT) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_DECIMAL_POINT
                    } else if (c == LOWER_E || c == UPPER_E) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_EXPONENT_LETTER
                    } else if (isValueTerminator(c)) {
                        state = NumberState.DONE
                    } else if (Character.isDigit(c)) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_INTEGER_DIGITS
                    } else if (Character.isWhitespace(c)) {
                        state = NumberState.DONE
                    } else {
                        state = NumberState.INVALID
                    }
                    break
                case NumberState.SAW_DECIMAL_POINT:
                    type = JsonTokenType.DOUBLE
                    if (Character.isDigit(c)) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_FRACTION_DIGITS
                    } else {
                        state = NumberState.INVALID
                    }
                    break
                case NumberState.SAW_FRACTION_DIGITS:
                    if (c == LOWER_E || c == UPPER_E) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_EXPONENT_LETTER
                    } else if (isValueTerminator(c)) {
                        state = NumberState.DONE
                    } else if (Character.isDigit(c)) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_FRACTION_DIGITS
                    } else if (Character.isWhitespace(c)) {
                        state = NumberState.DONE
                    } else {
                        state = NumberState.INVALID
                    }
                    break
                case NumberState.SAW_EXPONENT_LETTER:
                    type = JsonTokenType.DOUBLE
                    if (c == PLUS || c == JsonToken.MINUS) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_EXPONENT_SIGN
                    } else if (Character.isDigit(c)) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_EXPONENT_DIGITS
                    } else {
                        state = NumberState.INVALID
                    }
                    break
                case NumberState.SAW_EXPONENT_SIGN:
                    if (Character.isDigit(c)) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_EXPONENT_DIGITS
                    } else {
                        state = NumberState.INVALID
                    }
                    break
                case NumberState.SAW_EXPONENT_DIGITS:
                    if (isValueTerminator(c)) {
                        state = NumberState.DONE
                    } else if (Character.isDigit(c)) {
                        numberBuilder.append((char) c)
                        state = NumberState.SAW_EXPONENT_DIGITS
                    } else if (Character.isWhitespace(c)) {
                        state = NumberState.DONE
                    } else {
                        state = NumberState.INVALID
                    }
                    break
                case NumberState.SAW_MINUS_I:
                    boolean sawMinusInfinity = true
                    numberBuilder.append((char) c)
                    for (int i = 0; i < NFINITY.length; i++) {
                        if (c != NFINITY[i]) {
                            sawMinusInfinity = false
                            break
                        }
                        c = readCharacter()
                        if (i < NFINITY.length - 1) {
                            numberBuilder.append((char) c)
                        }
                    }
                    if (sawMinusInfinity) {
                        type = JsonTokenType.DOUBLE
                        if (isValueTerminator(c)) {
                            state = NumberState.DONE
                        } else if (Character.isWhitespace(c)) {
                            state = NumberState.DONE
                        } else {
                            state = NumberState.INVALID
                        }
                    } else {
                        state = NumberState.INVALID
                    }
                    break
                default:
                    break
            }

            if (state == NumberState.INVALID) {
                throw new JsonParseException('Invalid JSON number')
            }
            if (state == NumberState.DONE) {
                reader.unread(c)
                if (type == JsonTokenType.DOUBLE) {
                    return new JsonToken(JsonTokenType.DOUBLE, Double.parseDouble(numberBuilder.toString()))
                }
                long value = Long.parseLong(numberBuilder.toString())
                if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                    return new JsonToken(JsonTokenType.INT64, value)
                }
                return new JsonToken(JsonTokenType.INT32, (int) value)
            }
        }
    }

    /**
     * Reads {@code StringToken} from source.
     *
     * @return The string token.
     */
    private JsonToken scanString(final char quoteCharacter) throws IOException {
        StringBuilder sb = new StringBuilder()

        while (true) {
            int c = readCharacter()
            if (c == JsonToken.BACK_SLASH) {
                c = readCharacter()
                if (c == SINGLE_QUOTE) {
                    sb.append(SINGLE_QUOTE)
                } else if (c == JsonToken.QUOTE) {
                    sb.append(JsonToken.QUOTE)
                } else if (c == JsonToken.BACK_SLASH) {
                    sb.append(JsonToken.BACK_SLASH)
                } else if (c == JsonToken.FORWARD_SLASH) {
                    sb.append(JsonToken.FORWARD_SLASH)
                } else if (c == ('b' as char)) {
                    sb.append('\b' as char)
                } else if (c == ('f' as char)) {
                    sb.append('\f' as char)
                } else if (c == ('n' as char)) {
                    sb.append('\n' as char)
                } else if (c == ('r' as char)) {
                    sb.append('\r' as char)
                } else if (c == ('t' as char)) {
                    sb.append('\t' as char)
                } else if (c == ('u' as char)) {
                    int u1 = readCharacter()
                    int u2 = readCharacter()
                    int u3 = readCharacter()
                    int u4 = readCharacter()
                    if (u4 != END_OF_INPUT) {
                        String hex = new String([(char) u1, (char) u2, (char) u3, (char) u4] as char[])
                        sb.append((char) Integer.parseInt(hex, 16))
                    }
                } else {
                    throw new JsonParseException("Invalid escape sequence in JSON string '\\%c'.", c)
                }
            } else {
                if (c == quoteCharacter) {
                    return new JsonToken(JsonTokenType.STRING, sb.toString())
                }
                if (c != END_OF_INPUT) {
                    sb.append((char) c)
                }
            }
            if (c == END_OF_INPUT) {
                throw new JsonParseException('End of file in JSON string.')
            }
        }
    }

    /**
     * Whether the character ends a scalar value: a separator, a closing bracket or the end of input.
     */
    private static boolean isValueTerminator(int c) {
        return c == JsonToken.COMMA ||
                c == JsonToken.CLOSE_BRACE ||
                c == JsonToken.CLOSE_BRACKET ||
                c == JsonToken.CLOSE_PARENS ||
                c == END_OF_INPUT
    }

    private static boolean isRegularExpressionOption(int c) {
        return c == ('i' as char) || c == ('m' as char) || c == ('x' as char) || c == ('s' as char)
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
