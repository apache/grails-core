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
import org.jline.reader.ParsedLine
import spock.lang.Specification
import spock.lang.Unroll

class StringsCompleterSpec extends Specification {

    def "Empty completer returns no candidates"() {
        given: "an empty strings completer"
        def completer = new StringsCompleter()
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "no candidates are returned"
        candidates.isEmpty()
    }

    def "Completer returns all strings when buffer is empty"() {
        given: "a strings completer with some values"
        def completer = new StringsCompleter("apple", "banana", "cherry")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked with empty buffer"
        completer.complete(null, parsedLine, candidates)

        then: "all strings are returned as candidates"
        candidates.size() == 3
        candidates*.value() == ["apple", "banana", "cherry"]
    }

    def "Completer returns all strings when buffer is null"() {
        given: "a strings completer with some values"
        def completer = new StringsCompleter("apple", "banana", "cherry")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> null
        }

        when: "the completer is invoked with null buffer"
        completer.complete(null, parsedLine, candidates)

        then: "all strings are returned as candidates"
        candidates.size() == 3
    }

    @Unroll("Prefix '#prefix' matches #expectedMatches")
    def "Completer filters strings by prefix"() {
        given: "a strings completer with various values"
        def completer = new StringsCompleter("create-app", "create-plugin", "create-domain-class", "run-app", "test-app")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> prefix
        }

        when: "the completer is invoked with a prefix"
        completer.complete(null, parsedLine, candidates)

        then: "only matching strings are returned"
        candidates*.value() == expectedMatches

        where:
        prefix    | expectedMatches
        "create"  | ["create-app", "create-domain-class", "create-plugin"]
        "run"     | ["run-app"]
        "test"    | ["test-app"]
        "xyz"     | []
        "create-a" | ["create-app"]
    }

    def "Completer can be constructed with a collection"() {
        given: "a strings completer constructed with a list"
        def completer = new StringsCompleter(["one", "two", "three"])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "all strings from the collection are available"
        candidates.size() == 3
        candidates*.value().containsAll(["one", "two", "three"])
    }

    def "Strings can be modified via getStrings()"() {
        given: "a strings completer"
        def completer = new StringsCompleter("initial")
        
        when: "strings are added via getStrings()"
        completer.getStrings().add("added")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }
        completer.complete(null, parsedLine, candidates)

        then: "the new string is included in completions"
        candidates.size() == 2
        candidates*.value().containsAll(["initial", "added"])
    }

    def "Strings are sorted alphabetically"() {
        given: "a strings completer with unsorted input"
        def completer = new StringsCompleter("zebra", "apple", "mango")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "candidates are sorted"
        candidates*.value() == ["apple", "mango", "zebra"]
    }

    def "setStrings replaces all strings"() {
        given: "a strings completer with initial values"
        def completer = new StringsCompleter("old1", "old2")
        
        when: "strings are replaced"
        def newStrings = new TreeSet<String>()
        newStrings.addAll(["new1", "new2", "new3"])
        completer.setStrings(newStrings)
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }
        completer.complete(null, parsedLine, candidates)

        then: "only new strings are available"
        candidates.size() == 3
        candidates*.value() == ["new1", "new2", "new3"]
    }

    // Additional edge case tests

    def "Completer handles duplicates in input"() {
        given: "a strings completer with duplicate values"
        def completer = new StringsCompleter("duplicate", "duplicate", "unique")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "duplicates are removed (TreeSet behavior)"
        candidates.size() == 2
        candidates*.value() == ["duplicate", "unique"]
    }

    def "Completer handles special characters"() {
        given: "a strings completer with special characters"
        def completer = new StringsCompleter("--verbose", "--help", "-v", "!shell")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "--"
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "special character strings are matched"
        candidates*.value() == ["--help", "--verbose"]
    }

    def "Completer is case-sensitive"() {
        given: "a strings completer with mixed case"
        def completer = new StringsCompleter("Apple", "apple", "APPLE")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "app"
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "only lowercase match is returned"
        candidates.size() == 1
        candidates[0].value() == "apple"
    }

    def "Completer handles unicode strings"() {
        given: "a strings completer with unicode"
        def completer = new StringsCompleter("café", "naïve", "résumé")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "caf"
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "unicode strings are matched correctly"
        candidates.size() == 1
        candidates[0].value() == "café"
    }

    def "Completer handles very long strings"() {
        given: "a strings completer with a very long string"
        def longString = "a" * 1000
        def completer = new StringsCompleter(longString, "short")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "a" * 500
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "long string is matched"
        candidates.size() == 1
        candidates[0].value() == longString
    }

    def "Completer handles strings with whitespace"() {
        given: "a strings completer with whitespace in strings"
        def completer = new StringsCompleter("hello world", "hello there", "goodbye")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "hello"
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "strings with whitespace are matched"
        candidates.size() == 2
        candidates*.value().containsAll(["hello there", "hello world"])
    }

    def "Completer handles numeric strings"() {
        given: "a strings completer with numbers"
        def completer = new StringsCompleter("123", "1234", "456", "12abc")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "12"
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "numeric strings are matched by prefix"
        candidates.size() == 3
        candidates*.value() == ["123", "1234", "12abc"]
    }

    def "Completer handles exact match"() {
        given: "a strings completer"
        def completer = new StringsCompleter("exact", "exactly", "exactness")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "exact"
        }

        when: "the completer is invoked with exact match"
        completer.complete(null, parsedLine, candidates)

        then: "exact match and extensions are returned"
        candidates.size() == 3
        candidates*.value() == ["exact", "exactly", "exactness"]
    }

    def "Completer handles single character strings"() {
        given: "a strings completer with single characters"
        def completer = new StringsCompleter("a", "b", "c", "ab", "abc")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "a"
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "single and multi-char strings are matched"
        candidates.size() == 3
        candidates*.value() == ["a", "ab", "abc"]
    }

    def "Completer does not add to candidate list if no match"() {
        given: "a strings completer"
        def completer = new StringsCompleter("alpha", "beta", "gamma")
        def candidates = [new Candidate("existing")]
        def parsedLine = Stub(ParsedLine) {
            word() >> "nomatch"
        }

        when: "the completer is invoked with no matching prefix"
        completer.complete(null, parsedLine, candidates)

        then: "existing candidates are preserved, no new ones added"
        candidates.size() == 1
        candidates[0].value() == "existing"
    }

    def "Completer candidate objects have correct type"() {
        given: "a strings completer"
        def completer = new StringsCompleter("test")
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "candidates are Candidate instances"
        candidates.every { it instanceof Candidate }
    }

    def "Completer throws on null candidates list"() {
        given: "a strings completer"
        def completer = new StringsCompleter("test")
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked with null candidates"
        completer.complete(null, parsedLine, null)

        then: "NullPointerException is thrown"
        thrown(NullPointerException)
    }
}
