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
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import spock.lang.Specification

class SortedAggregateCompleterSpec extends Specification {

    def "Empty aggregate completer returns no candidates"() {
        given: "an empty aggregate completer"
        def completer = new SortedAggregateCompleter()
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "no candidates are returned"
        candidates.isEmpty()
    }

    def "Aggregate completer combines results from multiple completers"() {
        given: "an aggregate completer with two string completers"
        def completer1 = new StringsCompleter("apple", "apricot")
        def completer2 = new StringsCompleter("banana", "blueberry")
        def aggregateCompleter = new SortedAggregateCompleter(completer1, completer2)
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the aggregate completer is invoked"
        aggregateCompleter.complete(null, parsedLine, candidates)

        then: "candidates from all completers are combined and sorted"
        candidates.size() == 4
        candidates*.value() == ["apple", "apricot", "banana", "blueberry"]
    }

    def "Aggregate completer sorts candidates alphabetically"() {
        given: "completers that would return unsorted results"
        def completer1 = new StringsCompleter("zebra", "mango")
        def completer2 = new StringsCompleter("apple", "kiwi")
        def aggregateCompleter = new SortedAggregateCompleter(completer1, completer2)
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the aggregate completer is invoked"
        aggregateCompleter.complete(null, parsedLine, candidates)

        then: "all candidates are sorted alphabetically"
        candidates*.value() == ["apple", "kiwi", "mango", "zebra"]
    }

    def "Aggregate completer can be constructed with a collection"() {
        given: "an aggregate completer constructed with a list of completers"
        def completers = [
            new StringsCompleter("one"),
            new StringsCompleter("two"),
            new StringsCompleter("three")
        ]
        def aggregateCompleter = new SortedAggregateCompleter(completers)
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the aggregate completer is invoked"
        aggregateCompleter.complete(null, parsedLine, candidates)

        then: "candidates from all completers are available"
        candidates.size() == 3
        candidates*.value() == ["one", "three", "two"]
    }

    def "getCompleters returns the internal completer collection"() {
        given: "an aggregate completer with some completers"
        def completer1 = new StringsCompleter("a")
        def completer2 = new StringsCompleter("b")
        def aggregateCompleter = new SortedAggregateCompleter(completer1, completer2)

        when: "getCompleters is called"
        def completers = aggregateCompleter.getCompleters()

        then: "the internal collection is returned"
        completers.size() == 2
        completers.contains(completer1)
        completers.contains(completer2)
    }

    def "Completers can be added dynamically"() {
        given: "an empty aggregate completer"
        def aggregateCompleter = new SortedAggregateCompleter()
        
        when: "a completer is added via getCompleters()"
        aggregateCompleter.getCompleters().add(new StringsCompleter("dynamic"))
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }
        aggregateCompleter.complete(null, parsedLine, candidates)

        then: "the new completer's candidates are included"
        candidates.size() == 1
        candidates[0].value() == "dynamic"
    }

    def "Aggregate completer respects individual completer filtering"() {
        given: "completers with different strings"
        def completer1 = new StringsCompleter("create-app", "create-plugin")
        def completer2 = new StringsCompleter("run-app", "test-app")
        def aggregateCompleter = new SortedAggregateCompleter(completer1, completer2)
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "create"
        }

        when: "the aggregate completer is invoked with a prefix"
        aggregateCompleter.complete(null, parsedLine, candidates)

        then: "only matching candidates from all completers are returned"
        candidates.size() == 2
        candidates*.value() == ["create-app", "create-plugin"]
    }

    def "toString returns a meaningful representation"() {
        given: "an aggregate completer"
        def completer = new SortedAggregateCompleter(new StringsCompleter("test"))

        when: "toString is called"
        def result = completer.toString()

        then: "it contains the class name and completers info"
        result.contains("SortedAggregateCompleter")
        result.contains("completers=")
    }

    def "Aggregate completer works with custom Completer implementations"() {
        given: "an aggregate completer with a custom completer"
        def customCompleter = new Completer() {
            @Override
            void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
                candidates.add(new Candidate("custom-value"))
            }
        }
        def stringsCompleter = new StringsCompleter("strings-value")
        def aggregateCompleter = new SortedAggregateCompleter(customCompleter, stringsCompleter)
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the aggregate completer is invoked"
        aggregateCompleter.complete(null, parsedLine, candidates)

        then: "candidates from both completers are combined"
        candidates.size() == 2
        candidates*.value() == ["custom-value", "strings-value"]
    }
}
