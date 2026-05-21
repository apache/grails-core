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
package org.grails.plugins.encoders

import grails.encoders.JSONEncoder
import spock.lang.Issue
import spock.lang.Specification

class JSONEncoderSpec extends Specification {

    @Issue("GRAILS-11513")
    def "should properly escape quotes when using encodeToWriter method of JSONEncoder"() {
        given:
        char[] inputBuf = input.toCharArray()
        JSONEncoder encoder = new JSONEncoder()
        StringWriter writerStrings = new StringWriter()
        StringWriter writerCharArrays = new StringWriter()
        when:
        encoder.encodeToWriter(input, 0, input.length(), writerStrings, null)
        encoder.encodeToWriter(inputBuf, 0, inputBuf.length, writerCharArrays, null)
        then:
        writerStrings.toString() == result
        writerCharArrays.toString() == result
        where:
        input | result
        "I contain a TAB \u000B" | "I contain a TAB \\u000B"
        "I contain a \"Quote\"!" | 'I contain a \\"Quote\\"!'
        "\"Quote\"" | '\\"Quote\\"'
        "\"Quote\"-" | '\\"Quote\\"-'
        "-\"Quote\"" | '-\\"Quote\\"'
        "-\"Quote\"-" | '-\\"Quote\\"-'
        "\"" | '\\"'
        "\"\"" | '\\"\\"'
        "\"Q\"" | '\\"Q\\"'
        "\"Q" | '\\"Q'
        "Q\"" | 'Q\\"'
    }
}
