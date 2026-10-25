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
package org.grails.cli.command

import org.springframework.boot.cli.command.Command as BootCommand
import org.springframework.boot.cli.command.CommandException as BootCommandException
import org.springframework.boot.cli.command.HelpExample as BootHelpExample
import org.springframework.boot.cli.command.options.OptionHelp
import org.springframework.boot.cli.command.status.ExitStatus
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class CommandRunnerSpec extends Specification {

    ByteArrayOutputStream output = new ByteArrayOutputStream()
    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    PrintStream originalOut
    PrintStream originalErr

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(output, true, 'UTF-8'))
        System.setErr(new PrintStream(errors, true, 'UTF-8'))
    }

    void cleanup() {
        PrintStream capturedOut = System.out
        PrintStream capturedErr = System.err
        System.setOut(originalOut)
        System.setErr(originalErr)
        capturedOut.close()
        capturedErr.close()
    }

    def 'runner name is suitable for use as a prefix'() {
        expect:
        new CommandRunner(name).name == expected

        where:
        name     | expected
        null     | ''
        ''       | ''
        'grails' | 'grails '
    }

    def 'registered commands can be found and iterated in registration order'() {
        given:
        def runner = new CommandRunner('grails')
        def first = Stub(BootCommand) { getName() >> 'first' }
        def second = Stub(BootCommand) { getName() >> 'second' }

        when:
        runner.addCommand(first)
        runner.addCommands([second])

        then:
        runner.findCommand('first').is(first)
        runner.findCommand('second').is(second)
        runner.findCommand('missing') == null
        runner.iterator().toList() == [first, second]

        when:
        def iterator = runner.iterator()
        iterator.next()
        iterator.remove()

        then:
        thrown(UnsupportedOperationException)
    }

    def 'addCommand rejects a null command'() {
        given:
        def runner = new CommandRunner(null)

        when:
        runner.addCommand(null)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == 'Command must not be null'
    }

    def 'addCommands rejects a null command list'() {
        given:
        def runner = new CommandRunner(null)

        when:
        runner.addCommands(null)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == 'Commands must not be null'
    }

    def 'setOptionCommands rejects null command classes'() {
        given:
        def runner = new CommandRunner(null)

        when:
        runner.setOptionCommands((Class[]) null)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == 'CommandClasses must not be null'
    }

    def 'setHiddenCommands rejects null command classes'() {
        given:
        def runner = new CommandRunner(null)

        when:
        runner.setHiddenCommands((Class[]) null)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == 'CommandClasses must not be null'
    }

    def 'known command receives its arguments and its successful status is returned'() {
        given:
        def runner = new CommandRunner('grails')
        def command = Mock(BootCommand)
        command.name >> 'echo'
        runner.addCommand(command)

        when:
        def result = runner.runAndHandleErrors('echo', 'one', 'two')

        then:
        result == 0
        1 * command.run(['one', 'two'] as String[]) >> ExitStatus.OK
        errors.toString('UTF-8') == ''
    }

    def 'option commands accept a double dash alias and appear in usage'() {
        given:
        def runner = new CommandRunner('grails')
        def option = Stub(BootCommand) { getName() >> 'version' }
        runner.addCommand(option)
        runner.setOptionCommands(option.getClass())

        expect:
        runner.isOptionCommand(option)
        runner.findCommand('--version').is(option)
        runner.runAndHandleErrors() == 1
        output.toString('UTF-8').contains('usage: grails [--version]')
    }

    def 'missing command reports a useful error and fails'() {
        given:
        def runner = new CommandRunner('grails')

        expect:
        runner.runAndHandleErrors('missing') == 1
        errors.toString('UTF-8').contains("'missing' is not a valid command. See 'help'.")
    }

    def 'help without arguments shows visible commands but hides configured commands'() {
        given:
        def runner = new CommandRunner('grails')
        def visible = Stub(BootCommand) {
            getName() >> 'echo'
            getUsageHelp() >> '<message>'
            getDescription() >> 'Echo a message'
        }
        def hidden = new HiddenCommand()
        def help = Stub(BootCommand) {
            getName() >> 'help'
            run(_ as String[]) >> { throw new NoHelpCommandArgumentsException() }
        }
        runner.addCommands([visible, hidden, help])
        runner.setHiddenCommands(HiddenCommand)

        when:
        def result = runner.runAndHandleErrors('help')

        then:
        result == 1
        output.toString('UTF-8').contains('Echo a message')
        output.toString('UTF-8').contains('<message>')
        output.toString('UTF-8').contains("See 'grails help <command>'")
        !output.toString('UTF-8').contains('secret')
        errors.toString('UTF-8') == ''
        runner.findCommand('secret').is(hidden)
    }

    def 'debug flags before the application separator are removed but application flags remain'() {
        given:
        def runner = new CommandRunner('grails')
        def command = Mock(BootCommand)
        command.name >> 'echo'
        runner.addCommand(command)

        when:
        def result = runner.runAndHandleErrors('--debug', 'echo', '--', '--debug')

        then:
        result == 0
        System.getProperty('debug') == 'true'
        1 * command.run(['--', '--debug'] as String[]) >> ExitStatus.OK
    }

    def 'rethrow option preserves the original command exception'() {
        given:
        def failure = new BootCommandException('failed', BootCommandException.Option.RETHROW)
        def command = Stub(BootCommand) {
            getName() >> 'fail'
            run(_ as String[]) >> { throw failure }
        }
        def runner = new CommandRunner(null)
        runner.addCommand(command)

        when:
        runner.runAndHandleErrors('fail')

        then:
        def exception = thrown(BootCommandException)
        exception.is(failure)
    }

    def 'only positive hangup status codes are propagated to the caller'() {
        given:
        def command = Stub(BootCommand) {
            getName() >> 'status'
            run(_ as String[]) >> status
        }
        def runner = new CommandRunner(null)
        runner.addCommand(command)

        expect:
        runner.runAndHandleErrors('status') == expected

        where:
        status                                 | expected
        null                                   | 0
        new ExitStatus(7, 'failure', false)     | 0
        new ExitStatus(7, 'failure', true)      | 7
        new ExitStatus(-1, 'exit', true)        | 0
        new ExitStatus(0, 'success', true)      | 0
    }

    private static class HiddenCommand implements BootCommand {
        String getName() { 'secret' }
        String getDescription() { 'Hidden command' }
        String getUsageHelp() { null }
        String getHelp() { null }
        Collection<OptionHelp> getOptionsHelp() { [] }
        Collection<BootHelpExample> getExamples() { [] }
        ExitStatus run(String... args) { ExitStatus.OK }
    }
}
