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
package org.grails.web.json

import java.util.Date
import java.util.regex.Matcher

import spock.lang.Specification

class JSONTokenerApiSpec extends Specification {

    void "consume characters backtrack and match expected characters"() {
        given:
        def tokener = new JSONTokener('abcd')

        when:
        tokener.back()

        then:
        tokener.more()
        tokener.next() == (char) 'a'
        tokener.next((char) 'b') == (char) 'b'

        when:
        tokener.back()

        then:
        tokener.next(2) == 'bc'
        tokener.next() == (char) 'd'
        !tokener.more()
        tokener.next() == (char) 0
        tokener.toString() == ' at character 4 of abcd'
    }

    void "next(n) returns the next n characters up to exactly the end of the source"() {
        given:
        def tokener = new JSONTokener('abc')

        expect:
        tokener.next(2) == 'ab'
        tokener.next(1) == 'c'
        new JSONTokener('abc').next(3) == 'abc'
    }

    void "character and substring mismatches include the source position"() {
        when:
        new JSONTokener('abc').next((char) 'z')

        then:
        def error = thrown(JSONException)
        error.message == "Expected 'z' and instead saw 'a'. at character 1 of abc"

        when:
        new JSONTokener('abc').next(4)

        then:
        def bounds = thrown(JSONException)
        bounds.message.contains('Substring bounds error')
    }

    void "decode hexadecimal characters"() {
        expect:
        JSONTokener.dehexchar((char) character) == expected

        where:
        character | expected
        '0'       | 0
        '9'       | 9
        'A'       | 10
        'F'       | 15
        'a'       | 10
        'f'       | 15
        'g'       | -1
        '/'       | -1
    }

    void "skip whitespace and each supported comment style"() {
        expect:
        new JSONTokener(source).nextClean() == (char) expected

        where:
        source                         | expected
        ' \t\r\n value'               | 'v'
        '// comment\nvalue'            | 'v'
        '// comment\rvalue'            | 'v'
        '/* comment * x */value'        | 'v'
        '# comment\nvalue'             | 'v'
        '/value'                       | '/'
        '// comment'                   | 0
        '# comment'                    | 0
        '   '                          | 0
    }

    void "unclosed block comments fail with a syntax error"() {
        when:
        new JSONTokener('/* unfinished').nextClean()

        then:
        def error = thrown(JSONException)
        error.message.contains('Unclosed comment.')
    }

    void "decode quoted strings with standard and legacy escapes"() {
        expect:
        new JSONTokener('text\\b\\t\\n\\f\\r\\u0041\\x42\\"\\\\\\/\\q"').nextString((char) '"') == 'text\b\t\n\f\rAB"\\/q'
        new JSONTokener("single'").nextString((char) "'") == 'single'
    }

    void "unterminated strings reject end of input and line breaks"() {
        when:
        new JSONTokener(source).nextString((char) '"')

        then:
        def error = thrown(JSONException)
        error.message.contains('Unterminated string')

        where:
        source << ['unfinished', 'line\n"', 'line\r"']
    }

    void "invalid hexadecimal string escapes cannot be decoded"() {
        when:
        new JSONTokener(source).nextString((char) '"')

        then:
        thrown(NumberFormatException)

        where:
        source << ['\\uZZZZ"', '\\xZZ"']
    }

    void "nextTo trims text and leaves delimiters available"() {
        given:
        def character = new JSONTokener('  value  ,tail')
        def delimiters = new JSONTokener(' value ;tail')
        def newline = new JSONTokener(' value\nrest')

        expect:
        character.nextTo((char) ',') == 'value'
        character.next() == (char) ','
        delimiters.nextTo(',;') == 'value'
        delimiters.next() == (char) ';'
        newline.nextTo(',;') == 'value'
        newline.next() == (char) '\n'
        new JSONTokener(' value ').nextTo((char) ',') == 'value'
        new JSONTokener(' value ').nextTo(',;') == 'value'
        new JSONTokener(' value\rrest').nextTo((char) ',') == 'value'
    }

    void "nextValue recognizes literals numbers dates and nested structures"() {
        expect:
        def actual = new JSONTokener(source).nextValue()
        actual == expected
        actual == null || actual.getClass() == type

        where:
        source                          | expected                           | type
        'true'                          | true                               | Boolean
        'FALSE'                         | false                              | Boolean
        'null'                          | null                               | null
        '"quoted"'                      | 'quoted'                           | String
        "'single'"                      | 'single'                           | String
        'plain'                         | 'plain'                            | String
        '0x10'                          | 16                                 | Integer
        '0Xff'                          | 255                                | Integer
        '010'                           | 8                                  | Integer
        '09'                            | 9                                  | Integer
        '-12'                           | -12                                | Integer
        '+12'                           | 12                                 | Integer
        '2147483648'                    | 2147483648L                        | Long
        '9223372036854775808'            | 9223372036854775808G                | BigInteger
        '2.5'                           | 2.5d                               | Double
        '1.2300'                        | 1.2300G                            | BigDecimal
        '0.123456789012345678901'        | 0.123456789012345678901G            | BigDecimal
        '1e400'                         | 1e400G                             | BigDecimal
        '-1e400'                        | -1e400G                            | BigDecimal
        '0xZZ'                          | '0xZZ'                             | String
        '-invalid'                      | '-invalid'                         | String
        'new Date(123)'                 | new Date(123L)                     | Date
        'new Date(invalid)'             | 'new Date(invalid)'                | String
        'new Date(99999999999999999999)' | 'new Date(99999999999999999999)'    | String
        '[1,true,null]'                 | new JSONArray([1, true, null])     | JSONArray
        '{"name":"Grails"}'            | new JSONObject([name: 'Grails'])   | JSONObject
    }

    void "missing values are rejected"() {
        when:
        new JSONTokener(source).nextValue()

        then:
        def error = thrown(JSONException)
        error.message.contains('Missing value.')

        where:
        source << ['', ' ', ',', ']']
    }

    void "skipTo preserves the position when missing and skipPast consumes the match"() {
        given:
        def tokener = new JSONTokener('abc:def:end')

        expect:
        tokener.skipTo((char) '?') == (char) 0
        tokener.next() == (char) 'a'
        tokener.skipTo((char) ':') == (char) ':'
        tokener.next() == (char) ':'

        when:
        tokener.skipPast('def:')

        then:
        tokener.next() == (char) 'e'

        when:
        tokener.skipPast('missing')

        then:
        !tokener.more()
    }

    void "syntax errors escape replacement metacharacters and truncate long input"() {
        given:
        def shortSource = new JSONTokener('$\\value')
        def longSource = new JSONTokener('123456789012345678901234567890')

        expect:
        shortSource.toRegexSafeString() == Matcher.quoteReplacement(' at character 0 of $\\value')
        shortSource.syntaxError('Invalid').message == 'Invalid' + shortSource.toRegexSafeString()
        longSource.toRegexSafeString() == ' at character 0 of 1234567890123456789...'
    }
}
