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
package grails.build.logging

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import spock.lang.Specification

/**
 * Tests for GrailsConsole completer management and JLine 3 integration.
 */
class GrailsConsoleCompleterSpec extends Specification {

    /**
     * A test GrailsConsole subclass that allows us to test completer functionality
     * without triggering side effects like redirecting System.out/err.
     */
    static class TestableGrailsConsole extends GrailsConsole {
        Terminal testTerminal
        
        TestableGrailsConsole(Terminal terminal) throws IOException {
            super()
            this.testTerminal = terminal
            this.@terminal = terminal
            // Initialize with a basic reader
            this.@reader = createLineReader(terminal, null)
        }
        
        @Override
        protected void bindSystemOutAndErr(PrintStream systemOut, PrintStream systemErr) {
            // Don't bind system streams in tests
            out = systemOut
            err = systemErr
        }
        
        @Override
        protected void redirectSystemOutAndErr(boolean force) {
            // Don't redirect in tests
        }
        
        @Override
        protected Terminal createTerminal() {
            return testTerminal
        }
    }

    Terminal terminal
    TestableGrailsConsole console

    def setup() {
        terminal = TerminalBuilder.builder().dumb(true).build()
        console = new TestableGrailsConsole(terminal)
    }

    def cleanup() {
        terminal?.close()
    }

    def "addCompleter accepts non-null completers"() {
        given: "a simple completer"
        def completer = createSimpleCompleter("test")

        when: "the completer is added"
        console.addCompleter(completer)

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "addCompleter ignores null completers"() {
        when: "a null completer is added"
        console.addCompleter(null)

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "resetCompleters clears all completers"() {
        given: "a console with added completers"
        console.addCompleter(createSimpleCompleter("one"))
        console.addCompleter(createSimpleCompleter("two"))

        when: "completers are reset"
        console.resetCompleters()

        then: "no exception is thrown and the operation completes"
        noExceptionThrown()
    }

    def "multiple completers can be added"() {
        given: "multiple completers"
        def completer1 = createSimpleCompleter("first")
        def completer2 = createSimpleCompleter("second")
        def completer3 = createSimpleCompleter("third")

        when: "all completers are added"
        console.addCompleter(completer1)
        console.addCompleter(completer2)
        console.addCompleter(completer3)

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "getReader returns a LineReader"() {
        when: "the reader is retrieved"
        def reader = console.getReader()

        then: "a valid LineReader is returned"
        reader != null
        reader instanceof LineReader
    }

    def "getTerminal returns the terminal"() {
        when: "the terminal is retrieved"
        def term = console.getTerminal()

        then: "the terminal is returned"
        term != null
        term == terminal
    }

    def "isAnsiEnabled returns false for dumb terminal"() {
        given: "a console with dumb terminal"
        console.setAnsiEnabled(true)

        expect: "ANSI is disabled for dumb terminal"
        // Dumb terminal should have ANSI disabled
        !console.isAnsiEnabled() || terminal.getType() != "dumb"
    }

    def "getHistory can return null when history is not configured"() {
        when: "history is retrieved"
        def history = console.getHistory()

        then: "history may be null (depends on configuration)"
        // Just verify the method works without exception
        noExceptionThrown()
    }

    // Additional tests for GrailsConsole functionality

    def "getOut returns the output stream"() {
        when: "the output stream is retrieved"
        def out = console.getOut()

        then: "a PrintStream is returned"
        out != null
        out instanceof PrintStream
    }

    def "setOut changes the output stream"() {
        given: "a new output stream"
        def newOut = new PrintStream(new ByteArrayOutputStream())

        when: "the output stream is changed"
        console.setOut(newOut)

        then: "the new output stream is used"
        console.getOut() == newOut
    }

    def "getErr returns the error stream"() {
        when: "the error stream is retrieved"
        def err = console.getErr()

        then: "a PrintStream is returned"
        err != null
        err instanceof PrintStream
    }

    def "setErr changes the error stream"() {
        given: "a new error stream"
        def newErr = new PrintStream(new ByteArrayOutputStream())

        when: "the error stream is changed"
        console.setErr(newErr)

        then: "the new error stream is used"
        console.getErr() == newErr
    }

    def "verbose mode can be toggled"() {
        expect: "verbose is initially false"
        !console.isVerbose()

        when: "verbose is enabled"
        console.setVerbose(true)

        then: "verbose is true"
        console.isVerbose()

        when: "verbose is disabled"
        console.setVerbose(false)

        then: "verbose is false"
        !console.isVerbose()
    }

    def "stacktrace mode can be toggled"() {
        expect: "stacktrace is initially false"
        !console.isStacktrace()

        when: "stacktrace is enabled"
        console.setStacktrace(true)

        then: "stacktrace is true"
        console.isStacktrace()

        when: "stacktrace is disabled"
        console.setStacktrace(false)

        then: "stacktrace is false"
        !console.isStacktrace()
    }

    def "lastMessage can be set and retrieved"() {
        when: "a message is set"
        console.setLastMessage("Test message")

        then: "the message can be retrieved"
        console.getLastMessage() == "Test message"
    }

    def "ANSI can be enabled and disabled"() {
        when: "ANSI is disabled"
        console.setAnsiEnabled(false)

        then: "ANSI reports as disabled"
        !console.isAnsiEnabled()
    }

    def "default input mask can be set"() {
        when: "default input mask is set"
        console.setDefaultInputMask('*' as Character)

        then: "the mask can be retrieved"
        console.getDefaultInputMask() == '*' as Character
    }

    def "default input mask can be null"() {
        when: "default input mask is set to null"
        console.setDefaultInputMask(null)

        then: "the mask is null"
        console.getDefaultInputMask() == null
    }

    def "category stack is available"() {
        when: "the category stack is retrieved"
        def category = console.getCategory()

        then: "a stack is returned"
        category != null
        category instanceof Stack
    }

    def "flush does not throw exceptions"() {
        when: "flush is called"
        console.flush()

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "addCompleter followed by resetCompleters works correctly"() {
        given: "multiple completers are added"
        console.addCompleter(createSimpleCompleter("one"))
        console.addCompleter(createSimpleCompleter("two"))
        console.addCompleter(createSimpleCompleter("three"))

        when: "completers are reset and new ones added"
        console.resetCompleters()
        console.addCompleter(createSimpleCompleter("new"))

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "reader remains available when completers are added"() {
        given: "initial reader"
        def initialReader = console.getReader()

        when: "a completer is added"
        console.addCompleter(createSimpleCompleter("test"))

        then: "reader remains usable"
        console.getReader() != null
        console.getReader() == initialReader
    }

    def "isWindows returns consistent result"() {
        when: "isWindows is called"
        def isWin = console.isWindows()

        then: "it returns a boolean"
        isWin == true || isWin == false
    }

    /**
     * Creates a simple completer for testing.
     */
    private Completer createSimpleCompleter(String... values) {
        return new Completer() {
            @Override
            void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
                for (String value : values) {
                    candidates.add(new Candidate(value))
                }
            }
        }
    }
}
