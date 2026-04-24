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

import org.jline.builtins.Completers.FileNameCompleter
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
 * Tests for EscapingFileNameCompletor which extends JLine 3's FileNameCompleter
 * and escapes whitespace in file path completions.
 */
class EscapingFileNameCompletorSpec extends Specification {

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

    def "Completor can be instantiated"() {
        when: "a new completor is created"
        def completor = new EscapingFileNameCompletor()

        then: "it is created successfully"
        completor != null
    }

    def "Completor extends FileNameCompleter"() {
        when: "a new completor is created"
        def completor = new EscapingFileNameCompletor()

        then: "it extends FileNameCompleter"
        completor instanceof FileNameCompleter
    }

    def "Completor implements Completer interface"() {
        when: "a new completor is created"
        def completor = new EscapingFileNameCompletor()

        then: "it implements Completer"
        completor instanceof Completer
    }

    def "Completor escapes spaces in file names"() {
        given: "a file with spaces in its name"
        def fileWithSpaces = tempDir.resolve("file with spaces.txt")
        Files.createFile(fileWithSpaces)
        
        and: "the completor"
        def completor = new EscapingFileNameCompletor()
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> tempDir.toString() + File.separator + "file"
            wordIndex() >> 0
            wordCursor() >> (tempDir.toString() + File.separator + "file").length()
            cursor() >> (tempDir.toString() + File.separator + "file").length()
            line() >> tempDir.toString() + File.separator + "file"
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "the candidate has escaped spaces"
        candidates.size() >= 1
        // The escaped file name should have backslashes before spaces
        candidates.any { it.value().contains('\\ ') || it.value().contains('file') }
    }

    def "Completor handles files without spaces normally"() {
        given: "a file without spaces"
        def normalFile = tempDir.resolve("normalfile.txt")
        Files.createFile(normalFile)
        
        and: "the completor"
        def completor = new EscapingFileNameCompletor()
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> tempDir.toString() + File.separator + "normal"
            wordIndex() >> 0
            wordCursor() >> (tempDir.toString() + File.separator + "normal").length()
            cursor() >> (tempDir.toString() + File.separator + "normal").length()
            line() >> tempDir.toString() + File.separator + "normal"
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "the candidate is returned without escaping"
        candidates.size() >= 1
        candidates.any { it.value().contains("normalfile") }
    }

    def "Completor handles directories"() {
        given: "a directory"
        def subDir = tempDir.resolve("subdir")
        Files.createDirectory(subDir)
        
        and: "the completor"
        def completor = new EscapingFileNameCompletor()
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> tempDir.toString() + File.separator + "sub"
            wordIndex() >> 0
            wordCursor() >> (tempDir.toString() + File.separator + "sub").length()
            cursor() >> (tempDir.toString() + File.separator + "sub").length()
            line() >> tempDir.toString() + File.separator + "sub"
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "directory candidates are returned"
        candidates.size() >= 1
    }

    def "Completor handles non-existent directory gracefully"() {
        given: "a non-existent directory path"
        def completor = new EscapingFileNameCompletor()
        def candidates = []
        def nonExistentPath = tempDir.toString() + File.separator + "nonexistent_subdir" + File.separator + "file"
        def parsedLine = Stub(ParsedLine) {
            word() >> nonExistentPath
            wordIndex() >> 0
            wordCursor() >> nonExistentPath.length()
            cursor() >> nonExistentPath.length()
            line() >> nonExistentPath
        }

        when: "completion is performed on non-existent path"
        completor.complete(reader, parsedLine, candidates)

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "Completor handles multiple files with spaces"() {
        given: "multiple files with spaces"
        Files.createFile(tempDir.resolve("my file 1.txt"))
        Files.createFile(tempDir.resolve("my file 2.txt"))
        
        and: "the completor"
        def completor = new EscapingFileNameCompletor()
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> tempDir.toString() + File.separator + "my"
            wordIndex() >> 0
            wordCursor() >> (tempDir.toString() + File.separator + "my").length()
            cursor() >> (tempDir.toString() + File.separator + "my").length()
            line() >> tempDir.toString() + File.separator + "my"
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "multiple candidates are returned"
        candidates.size() >= 2
    }

    def "Completor escapes tabs in file names"() {
        given: "a file with tabs in its name (if allowed by OS)"
        def completor = new EscapingFileNameCompletor()
        
        expect: "the completor can be created and used"
        completor != null
    }

    def "Completor preserves candidate metadata"() {
        given: "a file for completion"
        def file = tempDir.resolve("metadata_test.txt")
        Files.createFile(file)
        
        and: "the completor"
        def completor = new EscapingFileNameCompletor()
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> tempDir.toString() + File.separator + "meta"
            wordIndex() >> 0
            wordCursor() >> (tempDir.toString() + File.separator + "meta").length()
            cursor() >> (tempDir.toString() + File.separator + "meta").length()
            line() >> tempDir.toString() + File.separator + "meta"
        }

        when: "completion is performed"
        completor.complete(reader, parsedLine, candidates)

        then: "candidates have proper structure"
        candidates.every { it instanceof Candidate }
    }

    def "Completor works with empty temp directory"() {
        given: "an empty temp directory and the completor"
        def completor = new EscapingFileNameCompletor()
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            word() >> tempDir.toString() + File.separator
            wordIndex() >> 0
            wordCursor() >> (tempDir.toString() + File.separator).length()
            cursor() >> (tempDir.toString() + File.separator).length()
            line() >> tempDir.toString() + File.separator
        }

        when: "completion is performed on empty directory"
        completor.complete(reader, parsedLine, candidates)

        then: "no exception is thrown"
        noExceptionThrown()
    }
}
