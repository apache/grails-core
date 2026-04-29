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
import org.jline.reader.LineReaderBuilder
import org.jline.reader.ParsedLine
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for SimpleOrFileNameCompletor which combines fixed string options
 * with file name completion.
 */
class SimpleOrFileNameCompletorSpec extends Specification {

    @TempDir
    Path tempDir

    Terminal terminal
    LineReader reader

    def setup() {
        terminal = TerminalBuilder.builder().dumb(true).build()
        reader = LineReaderBuilder.builder().terminal(terminal).build()
    }

    def cleanup() {
        terminal?.close()
    }

    def "Completor can be constructed with String array"() {
        when: "completor is created with String array"
        def completor = new SimpleOrFileNameCompletor(["option1", "option2"] as String[])

        then: "it is created successfully"
        completor != null
    }

    def "Completor can be constructed with List"() {
        when: "completor is created with List"
        def completor = new SimpleOrFileNameCompletor(["option1", "option2"])

        then: "it is created successfully"
        completor != null
    }

    def "Completor implements Completer interface"() {
        when: "completor is created"
        def completor = new SimpleOrFileNameCompletor(["option1"])

        then: "it implements Completer"
        completor instanceof Completer
    }

    def "Completor returns fixed options when matching"() {
        given: "a completor with fixed options"
        def completor = new SimpleOrFileNameCompletor(["create-app", "create-plugin", "run-app"])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "create"
            line() >> "create"
            wordIndex() >> 0
            wordCursor() >> 6
            cursor() >> 6
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "fixed options matching the prefix are returned"
        candidates.any { it.value() == "create-app" }
        candidates.any { it.value() == "create-plugin" }
    }

    def "Completor returns all fixed options when buffer is empty"() {
        given: "a completor with fixed options"
        def completor = new SimpleOrFileNameCompletor(["option1", "option2", "option3"])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
            line() >> ""
            wordIndex() >> 0
            wordCursor() >> 0
            cursor() >> 0
        }

        when: "completion is performed with empty buffer"
        completor.complete(reader, parsedLine, candidates)

        then: "all fixed options are returned (plus file completions)"
        candidates.any { it.value() == "option1" }
        candidates.any { it.value() == "option2" }
        candidates.any { it.value() == "option3" }
    }

    def "Completor combines fixed options with file completions"() {
        given: "a file in the temp directory"
        def testFile = tempDir.resolve("testfile.groovy")
        Files.createFile(testFile)
        
        and: "a completor with fixed options"
        def completor = new SimpleOrFileNameCompletor(["test-option"])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> tempDir.toString() + File.separator + "test"
            line() >> tempDir.toString() + File.separator + "test"
            wordIndex() >> 0
            wordCursor() >> (tempDir.toString() + File.separator + "test").length()
            cursor() >> (tempDir.toString() + File.separator + "test").length()
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "file completions are included"
        candidates.any { it.value().contains("testfile") }
    }

    def "Completor returns only file completions when no fixed options match"() {
        given: "a file in the temp directory"
        def uniqueFile = tempDir.resolve("uniquefile.txt")
        Files.createFile(uniqueFile)
        
        and: "a completor with non-matching fixed options"
        def completor = new SimpleOrFileNameCompletor(["option1", "option2"])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> tempDir.toString() + File.separator + "unique"
            line() >> tempDir.toString() + File.separator + "unique"
            wordIndex() >> 0
            wordCursor() >> (tempDir.toString() + File.separator + "unique").length()
            cursor() >> (tempDir.toString() + File.separator + "unique").length()
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "only file completions are returned"
        candidates.any { it.value().contains("uniquefile") }
        !candidates.any { it.value() == "option1" }
        !candidates.any { it.value() == "option2" }
    }

    def "Completor handles empty fixed options list"() {
        given: "a completor with empty options"
        def completor = new SimpleOrFileNameCompletor([])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> ""
            line() >> ""
            wordIndex() >> 0
            wordCursor() >> 0
            cursor() >> 0
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "Completor preserves order: fixed options before file completions"() {
        given: "a file and a matching fixed option"
        def alphaFile = tempDir.resolve("alpha.txt")
        Files.createFile(alphaFile)
        
        and: "a completor with a fixed option that sorts after the file"
        def completor = new SimpleOrFileNameCompletor(["alpha-option"])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "alpha"
            line() >> "alpha"
            wordIndex() >> 0
            wordCursor() >> 5
            cursor() >> 5
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "fixed option appears in results"
        candidates.any { it.value() == "alpha-option" }
    }

    def "Completor handles special characters in fixed options"() {
        given: "a completor with special character options"
        def completor = new SimpleOrFileNameCompletor(["--verbose", "--help", "-v"])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "--"
            line() >> "--"
            wordIndex() >> 0
            wordCursor() >> 2
            cursor() >> 2
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "options with special characters are returned"
        candidates.any { it.value() == "--verbose" }
        candidates.any { it.value() == "--help" }
    }

    def "Completor handles case-sensitive matching for fixed options"() {
        given: "a completor with mixed case options"
        def completor = new SimpleOrFileNameCompletor(["CreateApp", "createPlugin"])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "Create"
            line() >> "Create"
            wordIndex() >> 0
            wordCursor() >> 6
            cursor() >> 6
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "only matching case option is returned"
        candidates.any { it.value() == "CreateApp" }
        !candidates.any { it.value() == "createPlugin" }
    }

    def "Completor works with Grails-style commands"() {
        given: "a completor with Grails commands"
        def completor = new SimpleOrFileNameCompletor([
            "create-app", 
            "create-plugin", 
            "create-domain-class",
            "run-app",
            "test-app",
            "generate-all"
        ])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> "create-"
            line() >> "create-"
            wordIndex() >> 0
            wordCursor() >> 7
            cursor() >> 7
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "all create commands are returned"
        candidates.any { it.value() == "create-app" }
        candidates.any { it.value() == "create-plugin" }
        candidates.any { it.value() == "create-domain-class" }
        !candidates.any { it.value() == "run-app" }
    }
}
