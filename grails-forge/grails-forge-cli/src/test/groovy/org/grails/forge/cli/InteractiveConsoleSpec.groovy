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
package org.grails.forge.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiFunction
import java.util.function.Consumer
import org.apache.grails.forge.cli.console.ProjectTools
import org.apache.grails.forge.cli.console.GradleRunner
import org.jline.terminal.Terminal
import org.jline.terminal.impl.ExternalTerminal
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The command line without a command, which is the interactive console: that it is entered, reads
 * its lines from the input of the program, and ends when that input ends, and that it is not entered
 * where nothing may wait for what is typed.
 *
 * <p>The console reads the input the process has, which in the virtual machine of a specification is
 * the one its build talks to it through and never ends. So the features here run the command line
 * as a program of its own, with an input the feature gives it, wait for it for a time that is long
 * for the start of a virtual machine, and end it by force afterwards when it is still there. Only the
 * lines typed at a terminal are given to the console in this virtual machine, on a terminal of its
 * own.</p>
 */
@IgnoreIf({ os.windows || !ForkedCli.available() })
class InteractiveConsoleSpec extends Specification {

    static final String ESCAPE = '\u001b['
    static final int SECONDS = 300

    File dir = Files.createTempDirectory('grailsforgeconsole').toFile().canonicalFile
    File written = new File(dir, 'out.txt')
    Process program

    void cleanup() {
        if (program != null) {
            program.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
            program.destroyForcibly()
            program.waitFor(60, TimeUnit.SECONDS)
        }
        dir.deleteDir()
    }

    private void project() {
        new File(dir, 'grails-app').mkdirs()
        File script = new File(dir, 'gradlew')
        script.text = '#!/bin/sh\necho "the wrapper ran with $*"\n'
        script.setExecutable(true, false)
    }

    // the program `grails`, which enters the console in a project only, or the program of the Forge CLI itself
    private ProcessBuilder command(boolean grails, List<String> jvm, List<String> args) {
        ProcessBuilder builder = ForkedCli.builder(dir, jvm, args as String[]).redirectOutput(written).redirectErrorStream(true)
        if (grails) {
            int main = builder.command().indexOf(Application.name)
            builder.command().set(main, AsGrails.name)
        }
        // whatever the environment of whoever runs this names as directories to use, or asks of the output
        builder.environment().remove('GRAILS_SAFE_DIRECTORY')
        builder.environment().remove('NO_COLOR')
        return builder
    }

    private File typed(String lines) {
        File input = new File(dir, 'typed.txt')
        input.text = lines
        return input
    }

    @Unroll
    void "#name without a command #place enters the console, which ends when the input has ended: #input"() {
        given:
        if (inProject) {
            project()
        }
        ProcessBuilder builder = command(grails, [], [])
        if (input == 'nothing to read') {
            builder.redirectInput(ProcessBuilder.Redirect.from(new File('/dev/null')))
        }

        when:
        program = builder.start()
        if (input == 'closed at once') {
            program.outputStream.close()
        }
        boolean ended = program.waitFor(SECONDS, TimeUnit.SECONDS)

        then: 'it asked once, under the name it was started under, and left'
        ended
        program.exitValue() == 0
        written.text.count("${name}>") == 1
        !written.text.contains('Usage:')
        !written.text.contains('| Error')

        where:
        name               | grails | inProject | input
        'grails'           | true   | true      | 'nothing to read'
        'grails'           | true   | true      | 'closed at once'
        'grails-forge-cli' | false  | true      | 'nothing to read'
        'grails-forge-cli' | false  | false     | 'closed at once'
        place = inProject ? 'in a project' : 'outside a project'
    }

    void "the console runs each line as a command line, goes on after one that fails, and leaves at exit"() {
        given:
        project()

        when: 'a name that is no command is answered without a build: the wrapper here names no Gradle that would list the commands of the application'
        ProcessBuilder console = command(true, [], [])
        console.environment().put(ProjectTools.LISTING, 'false')
        program = console.redirectInput(typed('# a line that is a comment\n' +
                'no-such-command\n' +
                'gradle build "two  words"\n' +
                '  exit  \n' +
                'gradle never\n')).start()
        boolean ended = program.waitFor(SECONDS, TimeUnit.SECONDS)
        String said = written.text

        then:
        ended
        program.exitValue() == 0

        and: 'the line that is no command was answered as a mistake, and the next one was run with its arguments as they were typed'
        said.contains('no-such-command')
        said.contains("Run 'help' for the commands")
        said.contains('the wrapper ran with build two  words')

        and: 'nothing after exit was read'
        !said.contains('the wrapper ran with never')
    }

    @Unroll
    void "lines piped into the console end it at #shown, as exit and quit ended the interactive mode of the Grails shell, and nothing after it is read"() {
        given:
        project()

        when:
        ProcessBuilder console = command(true, [], [])
        console.environment().put(ProjectTools.LISTING, 'false')
        console.environment().put('NO_COLOR', '1')
        program = console.redirectInput(typed("gradle build\n${line}\ngradle never\n")).start()
        boolean ended = program.waitFor(SECONDS, TimeUnit.SECONDS)
        String said = written.text

        then: 'it left as at the end of its input, after the command before the line'
        ended
        program.exitValue() == 0
        said.contains('the wrapper ran with build')
        said.count('grails>') == 2

        and: 'the line was no command, and nothing after it was read'
        !said.contains('There is no command')
        !said.contains('| Error')
        !said.contains('the wrapper ran with never')

        where:
        line       | shown
        'exit'     | 'exit'
        'quit'     | 'quit'
        '  exit  ' | 'exit with spaces around it'
        '  quit  ' | 'quit with spaces around it'
        'quit   '  | 'quit with spaces after it'
    }

    @Unroll
    void "#shown is a line of the console as any other, alike for exit and quit, and the console goes on after it"() {
        given:
        project()

        when:
        ProcessBuilder console = command(true, [], [])
        console.environment().put(ProjectTools.LISTING, 'false')
        console.environment().put('NO_COLOR', '1')
        program = console.redirectInput(typed("${line}\ngradle after\n")).start()
        boolean ended = program.waitFor(SECONDS, TimeUnit.SECONDS)
        String said = written.text

        then: 'the line was refused as no command, and the next one was run'
        ended
        program.exitValue() == 0
        said.contains("There is no command ${line.split(' ')[0]}")
        said.contains('the wrapper ran with after')
        said.count('grails>') == 3

        and: 'a name of exactly exit or quit has the line that says what it is'
        said.contains("${line.split(' ')[0]} ends the interactive mode, which grails starts when it is given no command. It is no command outside it") == withLine

        where:
        line       | withLine | shown
        'EXIT'     | false    | 'exit in upper case'
        'QUIT'     | false    | 'quit in upper case'
        'Quit'     | false    | 'quit in another case'
        'exit now' | true     | 'exit with an argument'
        'quit now' | true     | 'quit with an argument'
    }

    // A terminal that is none reads the lines as they were piped; this one is an xterm, at which the lines are typed.
    @Unroll
    void "typed at a terminal, #shown #what the console"() {
        given:
        List<List<String>> commands = []
        InteractiveShell shell = new InteractiveShell(new CommandLine(new ConsoleProgramSpec.Root()),
                { String[] args -> commands.add(args.toList()) } as Consumer<String[]>, null)
        Terminal terminal = new ExternalTerminal('console', 'xterm-256color',
                new ByteArrayInputStream("create-app first\n${line}\ncreate-app after\n".getBytes('UTF-8')), new ByteArrayOutputStream(), StandardCharsets.UTF_8)

        when:
        int exit = shell.start(terminal)

        then: 'it left with 0 after the command before the line, and ran the line and the one after it only where the line was no end'
        exit == 0
        commands == [['create-app', 'first']] + (ends ? [] : [line.trim().split(' ').toList(), ['create-app', 'after']])

        cleanup:
        terminal?.close()

        where:
        line       | ends
        'exit'     | true
        'quit'     | true
        '  exit  ' | true
        '  quit  ' | true
        'EXIT'     | false
        'QUIT'     | false
        'exit now' | false
        'quit now' | false
        shown = "'${line}'"
        what = ends ? 'ends' : 'is a command line of'
    }

    @Unroll
    void "the terminal the console opens is closed when the console is left #how, also in a JVM that goes on after it"() {
        given:
        List<List<String>> commands = []
        List<Throwable> failures = []
        AtomicInteger closings = new AtomicInteger()
        Terminal terminal = new ExternalTerminal('console', 'xterm-256color',
                new ByteArrayInputStream(typed.getBytes('UTF-8')), new ByteArrayOutputStream(), StandardCharsets.UTF_8) {

            @Override
            protected void doClose() throws IOException {
                closings.incrementAndGet()
                super.doClose()
            }
        }
        InteractiveShell shell = new InteractiveShell(new CommandLine(new ConsoleProgramSpec.Root()),
                { String[] args -> commands.add(args.toList()) } as Consumer<String[]>,
                { Throwable failure, CommandLine commandLine -> failures.add(failure); 1 } as BiFunction<Throwable, CommandLine, Integer>) {

            @Override
            protected Terminal openTerminal() {
                return terminal
            }
        }

        when:
        int exit = shell.start()

        then:
        exit == 0
        failures == []
        commands == [['create-app', 'first']]
        closings.get() == 1

        where:
        how                       | typed
        'at exit'                 | 'create-app first\nexit\n'
        'at the end of its input' | 'create-app first\n'
    }

    void "the console reads the short forms the command line reads, of lower-case letters too"() {
        given:
        project()

        when:
        ProcessBuilder console = command(true, [], [])
        console.environment().put(ProjectTools.LISTING, 'false')
        console.environment().put('NO_COLOR', '1')
        program = console.redirectInput(typed('ra --help\n' +
                'help sa\n' +
                'co\n' +
                'rn\n' +
                'g build\n' +
                'exit\n')).start()
        boolean ended = program.waitFor(SECONDS, TimeUnit.SECONDS)
        String said = written.text

        then:
        ended
        program.exitValue() == 0

        and: 'the help of the commands the forms are short for, which says the form'
        said.contains('Usage: grails run-app ')
        said.contains('Short form: ra')
        said.contains('Usage: grails stop-app ')
        said.contains('Short form: sa')

        and: 'a form two commands fit runs neither and says how each is typed, and one that fits none says what it nearly is'
        said.contains('co is short for more than one command: compile (com), console (con). ' +
                'Type more of the name of the one that is meant, or the short form in brackets after it')
        said.contains('There is no command rn. Did you mean: run-app or run-script or run-tool?')
        said.contains('the wrapper ran with build')
    }

    void "the console takes run and stop for run-app and stop-app, as the Grails shell did, and answers test and a Groovy script with what to type"() {
        given:
        project()

        when:
        ProcessBuilder console = command(true, [], [])
        console.environment().put(ProjectTools.LISTING, 'false')
        console.environment().put('NO_COLOR', '1')
        program = console.redirectInput(typed('run --help\n' +
                'help stop\n' +
                'test\n' +
                'run app.groovy\n' +
                'ru\n' +
                'exit\n')).start()
        boolean ended = program.waitFor(SECONDS, TimeUnit.SECONDS)
        String said = written.text

        then:
        ended
        program.exitValue() == 0

        and: 'the help of the commands they are other names of'
        said.contains('Usage: grails run-app ')
        said.contains('Usage: grails stop-app ')

        and: 'test is no command, and run with a Groovy script is no script, each with what is typed at the prompt instead'
        said.contains('test was another name of test-app in the Grails shell, and in front of another command it named the environment test, ' +
                'which now follows the command as -Dgrails.env=test. Type: test-app')
        said.contains('run-app takes no argument app.groovy. A Groovy script is run in a Grails project with run-script, ' +
                'with the application started around it. Type: run-script app.groovy')

        and: 'no short form is read from run'
        said.contains('There is no command ru. Did you mean: run-app or run-script or run-tool?')
        !said.contains('the wrapper ran with')
    }

    void "the console ends with the input also after its last line was a command"() {
        given:
        project()

        when: 'the input ends after a command, without exit'
        program = command(true, [], []).redirectInput(typed('gradle build\n')).start()
        boolean ended = program.waitFor(SECONDS, TimeUnit.SECONDS)

        then:
        ended
        program.exitValue() == 0
        written.text.contains('the wrapper ran with build')
    }

    @Unroll
    void "with --plain-output and no command the console is entered and writes nothing a terminal reads, and ends with its input: #input"() {
        given: 'an output that is taken for a terminal that has colours, by what writes the messages and by what writes the prompt'
        project()
        // The prompt is written by JLine, to an output that is a file here and so no terminal for it: whether it
        // then keeps the colours is a matter of the environment (COLORTERM), unless it is told.
        List<String> asATerminal = ['-Dpicocli.ansi=true', '-Dorg.jline.terminal.dumb.color=true']

        when: 'without the option'
        program = command(true, asATerminal, []).redirectInput(typed(input)).start()
        boolean ended = program.waitFor(SECONDS, TimeUnit.SECONDS)
        String marked = written.text

        then:
        ended
        program.exitValue() == 0
        marked.contains(ESCAPE)

        when:
        program = command(true, asATerminal, ['--plain-output']).redirectInput(typed(input)).start()
        ended = program.waitFor(SECONDS, TimeUnit.SECONDS)
        String plain = written.text

        then: 'the same console, and Gradle is asked for its plain console by the commands that run it'
        ended
        program.exitValue() == 0
        !plain.contains(ESCAPE)
        plain.contains('grails>')
        plain.contains('the wrapper ran with') == input.contains('gradle')
        !plain.contains('the wrapper ran with') || plain.contains('the wrapper ran with --console=plain build')

        where:
        input << ['', 'no-such-command\n', 'gradle build\n']
    }

    @Unroll
    void "#name #place with #args does not enter the console: it shows the help and ends while its input is still open"() {
        given:
        if (inProject) {
            project()
        }

        when: 'an input that does not end: nothing is written to it, and it is not closed'
        program = command(grails, [], args).start()
        boolean ended = program.waitFor(SECONDS, TimeUnit.SECONDS)

        then:
        ended
        program.exitValue() == 0
        written.text.startsWith("Usage: ${name} ")
        !written.text.contains("${name}>")

        where:
        name               | grails | inProject | args
        'grails'           | true   | true      | ['--non-interactive']
        'grails'           | true   | true      | ['--non-interactive', '--plain-output']
        'grails'           | true   | true      | ['--plain-output', '--non-interactive']
        'grails'           | true   | false     | []
        'grails'           | true   | false     | ['--plain-output']
        'grails-forge-cli' | false  | false     | ['--non-interactive']
        place = inProject ? 'in a project' : 'outside a project'
    }

    /** The command line as the program {@code grails} starts it: under that name, with the console for projects only. */
    static class AsGrails {

        static void main(String[] args) {
            GradleRunner.outputOfProcess(System.out, System.err)
            System.exit(ForgeCli.launch('grails', true, args))
        }
    }
}
