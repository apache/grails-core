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
package org.grails.build.interactive

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for CandidateListCompletionHandler which wraps completion behavior
 * and provides utility methods for finding common prefixes.
 */
class CandidateListCompletionHandlerSpec extends Specification {

    def "Handler can be created without delegate"() {
        when: "handler is created without delegate"
        def handler = new CandidateListCompletionHandler()

        then: "it is created successfully"
        handler != null
    }

    def "Handler can be created with delegate"() {
        given: "a delegate completer"
        def delegate = Mock(Completer)

        when: "handler is created with delegate"
        def handler = new CandidateListCompletionHandler(delegate)

        then: "it is created successfully"
        handler != null
    }

    def "Handler delegates completion to wrapped completer"() {
        given: "a delegate completer that adds candidates"
        def delegate = new Completer() {
            @Override
            void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
                candidates.add(new Candidate("option1"))
                candidates.add(new Candidate("option2"))
            }
        }
        def handler = new CandidateListCompletionHandler(delegate)
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "completion is performed"
        handler.complete(null, parsedLine, candidates)

        then: "delegate's candidates are returned"
        candidates.size() == 2
        candidates*.value() == ["option1", "option2"]
    }

    def "Handler populates candidates without manipulating buffer"() {
        given: "a delegate completer with a shared prefix"
        def delegate = new TestCompleter(["create-app", "create-plugin"])
        def handler = new CandidateListCompletionHandler(delegate)
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "cre"
        }
        def reader = Mock(LineReader)

        when: "completion is performed"
        handler.complete(reader, parsedLine, candidates)

        then: "candidates are populated by the delegate"
        candidates.size() == 2
        candidates*.value() == ["create-app", "create-plugin"]

        and: "buffer is not directly manipulated - LineReader handles this"
        0 * reader.getBuffer()
        0 * reader.callWidget(_)
    }

    def "Handler does not alter buffer when prefix already matches"() {
        given: "a delegate completer with a shared prefix"
        def delegate = new TestCompleter(["create-app", "create-plugin"])
        def handler = new CandidateListCompletionHandler(delegate)
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "create-"
        }
        def reader = Mock(LineReader)
        def buffer = Mock(org.jline.reader.Buffer)
        reader.getBuffer() >> buffer

        when: "completion is performed"
        handler.complete(reader, parsedLine, candidates)

        then: "buffer is unchanged"
        0 * buffer.write(_)
        0 * reader.callWidget(_)
    }

    def "Handler with null delegate returns no candidates"() {
        given: "a handler without delegate"
        def handler = new CandidateListCompletionHandler()
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "completion is performed"
        handler.complete(null, parsedLine, candidates)

        then: "no candidates are added"
        candidates.isEmpty()
    }

    // Tests for getUnambiguousCompletions static method

    def "getUnambiguousCompletions returns null for null input"() {
        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(null) == null
    }

    def "getUnambiguousCompletions returns null for empty list"() {
        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions([]) == null
    }

    def "getUnambiguousCompletions returns full string for single candidate"() {
        given:
        def candidates = [new Candidate("foobar")]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == "foobar"
    }

    @Unroll
    def "getUnambiguousCompletions finds common prefix '#expected' for #description"() {
        given:
        def candidates = values.collect { new Candidate(it) }

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == expected

        where:
        values                              | expected    | description
        ["foobar", "foobaz", "foobuz"]     | "foob"      | "3 strings with common 'foob' prefix"
        ["apple", "apricot", "application"] | "ap"        | "3 strings with common 'ap' prefix"
        ["test", "testing", "tested"]       | "test"      | "3 strings with common 'test' prefix"
        ["abc", "def", "ghi"]               | ""          | "strings with no common prefix"
        ["same", "same"]                    | "same"      | "identical strings"
        ["a", "ab", "abc"]                  | "a"         | "incrementally longer strings"
    }

    def "getUnambiguousCompletions handles single character candidates"() {
        given:
        def candidates = [new Candidate("a"), new Candidate("b")]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == ""
    }

    def "getUnambiguousCompletions handles empty string candidates"() {
        given:
        def candidates = [new Candidate(""), new Candidate("")]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == ""
    }

    def "getUnambiguousCompletions with mixed empty and non-empty"() {
        given:
        def candidates = [new Candidate(""), new Candidate("foo")]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == ""
    }

    def "getUnambiguousCompletions handles special characters"() {
        given:
        def candidates = [
            new Candidate("--verbose"),
            new Candidate("--version"),
            new Candidate("--verify")
        ]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == "--ver"
    }

    def "getUnambiguousCompletions handles paths"() {
        given:
        def candidates = [
            new Candidate("/usr/local/bin"),
            new Candidate("/usr/local/lib"),
            new Candidate("/usr/local/share")
        ]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == "/usr/local/"
    }

    def "getUnambiguousCompletions handles Grails commands"() {
        given:
        def candidates = [
            new Candidate("create-app"),
            new Candidate("create-plugin"),
            new Candidate("create-domain-class")
        ]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == "create-"
    }

    def "getUnambiguousCompletions handles case-sensitive matching"() {
        given:
        def candidates = [
            new Candidate("Test"),
            new Candidate("test")
        ]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == ""
    }

    def "getUnambiguousCompletions handles unicode characters"() {
        given:
        def candidates = [
            new Candidate("café"),
            new Candidate("caféine")
        ]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == "café"
    }

    def "getUnambiguousCompletions handles numbers"() {
        given:
        def candidates = [
            new Candidate("123"),
            new Candidate("1234"),
            new Candidate("12345")
        ]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == "123"
    }

    def "getUnambiguousCompletions handles whitespace"() {
        given:
        def candidates = [
            new Candidate("hello world"),
            new Candidate("hello there")
        ]

        expect:
        CandidateListCompletionHandler.getUnambiguousCompletions(candidates) == "hello "
    }

    def "Handler implements Completer interface"() {
        expect:
        new CandidateListCompletionHandler() instanceof Completer
    }

    def "Handler can be composed with other completers"() {
        given: "multiple handlers"
        def handler1 = new CandidateListCompletionHandler(new TestCompleter(["opt1"]))
        def handler2 = new CandidateListCompletionHandler(new TestCompleter(["opt2"]))
        
        and: "aggregating them"
        def candidates = []
        def parsedLine = Stub(ParsedLine) { word() >> "" }
        
        when: "completing with both"
        handler1.complete(null, parsedLine, candidates)
        handler2.complete(null, parsedLine, candidates)

        then: "both contributions are present"
        candidates.size() == 2
        candidates*.value().containsAll(["opt1", "opt2"])
    }

    /**
     * Simple test completer for testing.
     */
    static class TestCompleter implements Completer {
        List<String> completions
        
        TestCompleter(List<String> completions) {
            this.completions = completions
        }
        
        @Override
        void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            completions.each { candidates.add(new Candidate(it)) }
        }
    }
}
