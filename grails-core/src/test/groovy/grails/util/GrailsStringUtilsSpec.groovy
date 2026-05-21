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
package grails.util

import grails.util.GrailsStringUtils
import spock.lang.Specification

/**
 */
class GrailsStringUtilsSpec extends Specification{

    static final String FOO = 'foo'
    static final String TRIMMABLE
    static final String NON_TRIMMABLE

    static {
        def trimmable = new StringBuilder()
        def nonTrimmable = new StringBuilder()
        (0..<Character.MAX_VALUE).each { i ->
            char ch = (char) i
            if (Character.isWhitespace(ch) && i > 32) {
                nonTrimmable.append(ch)
            }
        }
        (0..32).each { int i ->
            trimmable.append((char) i)
        }
        TRIMMABLE = trimmable.toString()
        NON_TRIMMABLE = nonTrimmable.toString()
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
