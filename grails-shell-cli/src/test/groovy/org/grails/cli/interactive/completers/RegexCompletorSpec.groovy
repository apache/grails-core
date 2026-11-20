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
package org.grails.cli.interactive.completers

import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import spock.lang.*

class RegexCompletorSpec extends Specification {

    @Unroll("String '#source' is matching")
    def "Simple pattern matches"() {
        given: "a regex completor and an empty candidate list"
        def completor = new RegexCompletor(/!\w+/)
        def candidateList = []
        def parsedLine = Stub(ParsedLine) {
            word() >> source
        }

        when: "the completor is invoked for a given string"
        completor.complete(null, parsedLine, candidateList)

        then: "that string is the sole candidate"
        candidateList.size() == 1
        candidateList[0].value() == source

        where:
        source << [ "!ls", "!test_stuff" ]
    }

    @Unroll("String '#source' is not matching")
    def "Non matching strings"() {
        given: "a regex completor and an empty candidate list"
        def completor = new RegexCompletor(/!\w+/)
        def candidateList = []
        def parsedLine = Stub(ParsedLine) {
            word() >> source
        }

        when: "the completor is invoked for a given (non-matching) string"
        completor.complete(null, parsedLine, candidateList)

        then: "the candidate list is empty"
        candidateList.size() == 0

        where:
        source << [ "!ls ls", "!", "test", "" ]
    }

    // Additional edge case tests

    def "Completor can be created with different patterns"() {
        given: "various regex patterns"
        def patterns = [/\d+/, /[a-z]+/, /.*test.*/, /^prefix/]

        expect: "all can be instantiated"
        patterns.every { new RegexCompletor(it) != null }
    }

    def "Completor matches numeric patterns"() {
        given: "a numeric regex completor"
        def completor = new RegexCompletor(/\d+/)
        def candidateList = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "12345"
        }

        when: "the completor is invoked"
        completor.complete(null, parsedLine, candidateList)

        then: "numeric string matches"
        candidateList.size() == 1
        candidateList[0].value() == "12345"
    }

    def "Completor handles complex patterns"() {
        given: "a complex regex completor for email-like patterns"
        def completor = new RegexCompletor(/\w+@\w+\.\w+/)
        def candidateList = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "test@example.com"
        }

        when: "the completor is invoked"
        completor.complete(null, parsedLine, candidateList)

        then: "complex pattern matches"
        candidateList.size() == 1
        candidateList[0].value() == "test@example.com"
    }

    def "Completor handles anchored patterns"() {
        given: "a regex with start anchor"
        def completor = new RegexCompletor(/^grails-.*/)
        def candidateList = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "grails-core"
        }

        when: "the completor is invoked"
        completor.complete(null, parsedLine, candidateList)

        then: "anchored pattern matches"
        candidateList.size() == 1
    }

    def "Completor with null word returns no candidates"() {
        given: "a regex completor"
        def completor = new RegexCompletor(/\w+/)
        def candidateList = []
        def parsedLine = Stub(ParsedLine) {
            word() >> null
        }

        when: "the completor is invoked with null"
        completor.complete(null, parsedLine, candidateList)

        then: "no candidates are returned"
        candidateList.isEmpty()
    }

    def "Completor candidates are proper Candidate objects"() {
        given: "a regex completor"
        def completor = new RegexCompletor(/test/)
        def candidateList = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "test"
        }

        when: "the completor is invoked"
        completor.complete(null, parsedLine, candidateList)

        then: "candidates are Candidate instances"
        candidateList.every { it instanceof Candidate }
    }

    def "Completor handles patterns with groups"() {
        given: "a regex with capture groups"
        def completor = new RegexCompletor(/(create|run|test)-app/)
        def candidateList = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "create-app"
        }

        when: "the completor is invoked"
        completor.complete(null, parsedLine, candidateList)

        then: "pattern with groups matches"
        candidateList.size() == 1
        candidateList[0].value() == "create-app"
    }

    def "Completor handles unicode in patterns"() {
        given: "a regex that matches unicode"
        def completor = new RegexCompletor(/café\w*/)
        def candidateList = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "cafébar"
        }

        when: "the completor is invoked"
        completor.complete(null, parsedLine, candidateList)

        then: "unicode pattern matches"
        candidateList.size() == 1
    }

    def "Completor preserves existing candidates"() {
        given: "a regex completor and pre-existing candidates"
        def completor = new RegexCompletor(/match/)
        def candidateList = [new Candidate("existing")]
        def parsedLine = Stub(ParsedLine) {
            word() >> "match"
        }

        when: "the completor is invoked"
        completor.complete(null, parsedLine, candidateList)

        then: "both existing and new candidates are present"
        candidateList.size() == 2
        candidateList*.value().containsAll(["existing", "match"])
    }
}
