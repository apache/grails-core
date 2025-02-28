/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.util

import grails.util.GrailsStringUtils
import spock.lang.Specification

/**
 */
class GrailsStringUtilsSpec extends Specification{

    private static final String FOO = "foo";
    static final String WHITESPACE;
    static final String NON_WHITESPACE;
    static final String HARD_SPACE;
    static final String TRIMMABLE;
    static final String NON_TRIMMABLE;

    static {
        final StringBuilder ws = new StringBuilder();
        final StringBuilder nws = new StringBuilder();
        final String hs = String.valueOf((char) 160);
        final StringBuilder tr = new StringBuilder();
        final StringBuilder ntr = new StringBuilder();
        for (int i = 0; i < Character.MAX_VALUE; i++) {
            if (Character.isWhitespace((char) i)) {
                ws.append((char) i);
                if (i > 32) {
                    ntr.append((char) i);
                }
            } else if (i < 40) {
                nws.append((char) i);
            }
        }
        for (int i = 0; i <= 32; i++) {
            tr.append((char) i);
        }
        WHITESPACE = ws.toString();
        NON_WHITESPACE = nws.toString();
        HARD_SPACE = hs;
        TRIMMABLE = tr.toString();
        NON_TRIMMABLE = ntr.toString();
    }

    void "Test toBoolean"() {
        expect:
            GrailsStringUtils.toBoolean("on") == true
            GrailsStringUtils.toBoolean("yes") == true
            GrailsStringUtils.toBoolean("true") == true
            GrailsStringUtils.toBoolean("1") == true
            GrailsStringUtils.toBoolean("ON") == true
            GrailsStringUtils.toBoolean("Yes") == true
            GrailsStringUtils.toBoolean("TRue") == true
            GrailsStringUtils.toBoolean("false") == false
            GrailsStringUtils.toBoolean("0") == false
            GrailsStringUtils.toBoolean("off") == false
    }
    void "Test substringBefore method"() {
        expect:
            GrailsStringUtils.substringBefore("abc", "a")   == ""
            GrailsStringUtils.substringBefore("abcba", "b") == "a"
            GrailsStringUtils.substringBefore("abc", "c")   == "ab"
            GrailsStringUtils.substringBefore("abc", "d")   == "abc"
            GrailsStringUtils.substringBefore("abc", "")    == ""
            GrailsStringUtils.substringBefore("abc", null)  == "abc"
    }

    void "Test substringAfter method"() {
        expect:
            GrailsStringUtils.substringAfter("abc", "a")   == "bc"
            GrailsStringUtils.substringAfter("abcba", "b") == "cba"
            GrailsStringUtils.substringAfter("abc", "c")   == ""
            GrailsStringUtils.substringAfter("abc", "d")   == "abc"
            GrailsStringUtils.substringAfter("abc", "")    == "abc"
    }

    void "Test trimStart method"() {
        expect:
            GrailsStringUtils.trimStart("abc", "") == 'abc'
            GrailsStringUtils.trimStart("abc", null) == 'abc'
            GrailsStringUtils.trimStart("abc", "a") == 'bc'
            GrailsStringUtils.trimStart("abc", "ab") == 'c'
            GrailsStringUtils.trimStart("abc", "c") == 'abc'
    }

    void "Test trimToNull method"() {
        expect:
            GrailsStringUtils.trimToNull(FOO + "  ") == FOO
            GrailsStringUtils.trimToNull(" " + FOO + "  ") == FOO
            GrailsStringUtils.trimToNull(" " + FOO) == FOO
            GrailsStringUtils.trimToNull(FOO + "") == FOO
            GrailsStringUtils.trimToNull(" \t\r\n\b ") == null
            GrailsStringUtils.trimToNull(TRIMMABLE) == null
            GrailsStringUtils.trimToNull(NON_TRIMMABLE) == NON_TRIMMABLE
            GrailsStringUtils.trimToNull("") == null
            GrailsStringUtils.trimToNull(null) == null
    }
}
