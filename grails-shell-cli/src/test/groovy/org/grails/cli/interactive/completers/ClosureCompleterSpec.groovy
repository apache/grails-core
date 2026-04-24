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

class ClosureCompleterSpec extends Specification {

    def "Closure completer returns values from closure"() {
        given: "a closure completer with a simple closure"
        def completer = new ClosureCompleter({ ["apple", "banana", "cherry"] })
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "candidates from the closure are returned"
        candidates.size() == 3
        candidates*.value().containsAll(["apple", "banana", "cherry"])
    }

    def "Closure completer lazily evaluates the closure"() {
        given: "a closure that tracks invocations"
        def invocationCount = 0
        def completer = new ClosureCompleter({
            invocationCount++
            ["value"]
        })

        expect: "the closure has not been invoked yet"
        invocationCount == 0

        when: "the completer is accessed"
        completer.getCompleter()

        then: "the closure is invoked once"
        invocationCount == 1

        when: "the completer is accessed again"
        completer.getCompleter()

        then: "the closure is not invoked again (cached)"
        invocationCount == 1
    }

    def "Closure completer filters by prefix"() {
        given: "a closure completer with multiple values"
        def completer = new ClosureCompleter({ ["create-app", "create-plugin", "run-app", "test-app"] })
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "create"
        }

        when: "the completer is invoked with a prefix"
        completer.complete(null, parsedLine, candidates)

        then: "only matching candidates are returned"
        candidates.size() == 2
        candidates*.value() as Set == ["create-app", "create-plugin"] as Set
    }

    def "Closure completer works with empty closure result"() {
        given: "a closure completer that returns empty collection"
        def completer = new ClosureCompleter({ [] })
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "no candidates are returned"
        candidates.isEmpty()
    }

    def "Closure completer uses StringsCompleter internally"() {
        given: "a closure completer"
        def completer = new ClosureCompleter({ ["test"] })

        when: "the internal completer is retrieved"
        def internalCompleter = completer.getCompleter()

        then: "it is a StringsCompleter"
        internalCompleter instanceof StringsCompleter
    }

    def "Closure completer can use dynamic values"() {
        given: "a list that changes over time"
        def dynamicList = ["initial"]
        def completer = new ClosureCompleter({ dynamicList.clone() })
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is first invoked"
        completer.complete(null, parsedLine, candidates)

        then: "initial values are returned"
        candidates.size() == 1
        candidates[0].value() == "initial"

        when: "the list is modified and completer is invoked again"
        dynamicList.add("added")
        // Note: The completer caches the result, so new values won't be picked up
        candidates.clear()
        completer.complete(null, parsedLine, candidates)

        then: "still returns cached values (closure evaluated only once)"
        candidates.size() == 1
    }

    def "Closure completer works with Set return type"() {
        given: "a closure that returns a Set"
        def completer = new ClosureCompleter({ ["one", "two", "three"] as Set })
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
        }

        when: "the completer is invoked"
        completer.complete(null, parsedLine, candidates)

        then: "all values are available as candidates"
        candidates.size() == 3
    }
}
