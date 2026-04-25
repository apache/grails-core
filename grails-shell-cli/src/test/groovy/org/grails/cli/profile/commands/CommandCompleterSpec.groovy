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
package org.grails.cli.profile.commands

import org.grails.cli.profile.Command
import org.grails.cli.profile.CommandDescription
import org.grails.cli.profile.ExecutionContext
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import spock.lang.Specification

/**
 * Tests for CommandCompleter which provides tab completion for Grails commands.
 */
class CommandCompleterSpec extends Specification {

    def "CommandCompleter can be instantiated with empty commands"() {
        when: "a completer is created with empty command list"
        def completer = new CommandCompleter([])

        then: "it is created successfully"
        completer != null
        completer.commands.isEmpty()
    }

    def "CommandCompleter can be instantiated with commands"() {
        given: "some mock commands"
        def cmd1 = createMockCommand("create-app")
        def cmd2 = createMockCommand("run-app")

        when: "a completer is created"
        def completer = new CommandCompleter([cmd1, cmd2])

        then: "it contains the commands"
        completer.commands.size() == 2
    }

    def "CommandCompleter delegates to command if it implements Completer"() {
        given: "a command that implements Completer"
        def completingCommand = createCompletingCommand("create-app", ["--verbose", "--help"])
        def completer = new CommandCompleter([completingCommand])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> "create-app "
            word() >> ""
        }

        when: "completion is performed on that command"
        completer.complete(null, parsedLine, candidates)

        then: "the command's completer is invoked"
        candidates.size() == 2
        candidates*.value().containsAll(["--verbose", "--help"])
    }

    def "CommandCompleter finds command by exact name match"() {
        given: "a completing command"
        def cmd = createCompletingCommand("run-app", ["--port"])
        def completer = new CommandCompleter([cmd])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> "run-app"
            word() >> "run-app"
        }

        when: "completion is performed"
        completer.complete(null, parsedLine, candidates)

        then: "the matching command is found and completion delegated"
        candidates.size() == 1
        candidates[0].value() == "--port"
    }

    def "CommandCompleter finds command by prefix with arguments"() {
        given: "a completing command"
        def cmd = createCompletingCommand("create-domain-class", ["--package"])
        def completer = new CommandCompleter([cmd])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> "create-domain-class MyClass"
            word() >> "MyClass"
        }

        when: "completion is performed"
        completer.complete(null, parsedLine, candidates)

        then: "the command is found and completion delegated"
        candidates.size() == 1
    }

    def "CommandCompleter returns nothing for non-completing command"() {
        given: "a command that does not implement Completer"
        def cmd = createMockCommand("simple-command")
        def completer = new CommandCompleter([cmd])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> "simple-command"
            word() >> "simple-command"
        }

        when: "completion is performed"
        completer.complete(null, parsedLine, candidates)

        then: "no candidates are returned"
        candidates.isEmpty()
    }

    def "CommandCompleter returns nothing for unknown command"() {
        given: "a completer with specific commands"
        def cmd = createMockCommand("known-command")
        def completer = new CommandCompleter([cmd])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> "unknown-command"
            word() >> "unknown-command"
        }

        when: "completion is performed for unknown command"
        completer.complete(null, parsedLine, candidates)

        then: "no candidates are returned"
        candidates.isEmpty()
    }

    def "CommandCompleter handles multiple commands"() {
        given: "multiple completing commands"
        def cmd1 = createCompletingCommand("create-app", ["--app-option"])
        def cmd2 = createCompletingCommand("create-plugin", ["--plugin-option"])
        def completer = new CommandCompleter([cmd1, cmd2])
        
        and: "parsed line for first command"
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> "create-app "
            word() >> ""
        }

        when: "completion is performed"
        completer.complete(null, parsedLine, candidates)

        then: "correct command's completer is used"
        candidates.size() == 1
        candidates[0].value() == "--app-option"
    }

    def "CommandCompleter handles empty line"() {
        given: "a completer"
        def cmd = createCompletingCommand("test-cmd", ["option"])
        def completer = new CommandCompleter([cmd])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> ""
            word() >> ""
        }

        when: "completion is performed on empty line"
        completer.complete(null, parsedLine, candidates)

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "CommandCompleter handles whitespace-only line"() {
        given: "a completer"
        def cmd = createCompletingCommand("test-cmd", ["option"])
        def completer = new CommandCompleter([cmd])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> "   "
            word() >> ""
        }

        when: "completion is performed on whitespace line"
        completer.complete(null, parsedLine, candidates)

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "CommandCompleter finds first matching command"() {
        given: "commands with similar prefixes"
        def cmd1 = createCompletingCommand("create", ["--general"])
        def cmd2 = createCompletingCommand("create-app", ["--specific"])
        def completer = new CommandCompleter([cmd1, cmd2])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> "create "
            word() >> ""
        }

        when: "completion is performed"
        completer.complete(null, parsedLine, candidates)

        then: "the first matching command is used"
        candidates.size() == 1
        candidates[0].value() == "--general"
    }

    def "CommandCompleter handles command with multiple arguments"() {
        given: "a completing command"
        def cmd = createCompletingCommand("generate-all", ["--arg1", "--arg2"])
        def completer = new CommandCompleter([cmd])
        def candidates = []
        def parsedLine = Stub(ParsedLine) {
            line() >> "generate-all Domain --arg1 value"
            word() >> "value"
        }

        when: "completion is performed"
        completer.complete(null, parsedLine, candidates)

        then: "completion is delegated"
        candidates.size() == 2
    }

    /**
     * Creates a mock command that does not implement Completer.
     */
    private Command createMockCommand(String name) {
        return Stub(Command) {
            getName() >> name
            getDescription() >> Stub(CommandDescription) {
                getName() >> name
            }
        }
    }

    /**
     * Creates a command that implements Completer and returns the given completions.
     */
    private Command createCompletingCommand(String name, List<String> completions) {
        return new TestCompletingCommand(name, completions)
    }

    /**
     * Test command that implements both Command and Completer interfaces.
     */
    static class TestCompletingCommand implements Command, Completer {
        final String name
        final List<String> completions

        TestCompletingCommand(String name, List<String> completions) {
            this.name = name
            this.completions = completions
        }

        @Override
        CommandDescription getDescription() {
            return Stub(CommandDescription) {
                getName() >> name
            }
        }

        @Override
        boolean handle(ExecutionContext executionContext) {
            return true
        }

        @Override
        void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            completions.each { candidates.add(new Candidate(it)) }
        }
    }
}
