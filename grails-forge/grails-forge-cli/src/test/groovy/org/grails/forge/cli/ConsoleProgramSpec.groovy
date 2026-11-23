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
import java.util.function.Consumer
import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.terminal.impl.DumbTerminal
import org.jline.terminal.impl.ExternalTerminal
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * A line of the interactive console that starts with an exclamation mark, which runs what follows it
 * in the shell of the operating system. The console is driven as {@link InteractiveConsoleSpec}
 * drives it, as a program of its own whose input is the lines a feature gives it, where what the
 * program is given of the terminal matters; and in this virtual machine on a terminal that is none,
 * where the output of the console is what the feature reads.
 */
class ConsoleProgramSpec extends Specification {

    static final int SECONDS = 300
    static final String NOTHING_TO_REPEAT = '| Error There is no command to run again: ! alone runs the last command again'

    File dir = Files.createTempDirectory('grailsforgeprogram').toFile().canonicalFile
    File written = new File(dir, 'out.txt')
    Process program
    String previousUserDir = System.getProperty('user.dir')
    PrintStream originalOut = System.out
    PrintStream originalErr = System.err
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.dir', previousUserDir)
        if (program != null) {
            program.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
            program.destroyForcibly()
            program.waitFor(60, TimeUnit.SECONDS)
        }
        dir.deleteDir()
    }

    @IgnoreIf({ os.windows || !ForkedCli.available() })
    void "a line that starts with ! runs the rest in the shell, in the current directory, with the output of the console, and the console goes on after it"() {
        given: 'a project with two text files'
        project()
        new File(dir, 'one.txt').text = '1'
        new File(dir, 'two.txt').text = '2'

        when:
        String said = console(['SHELL': '/bin/sh'], '!echo hello" "from the shell\n' +
                '!echo "two  words" \'and $HOME\'\n' +
                '!printf \'b\\na\\n\' | sort\n' +
                '!ls *.txt\n' +
                '!pwd -P\n' +
                '!exit 3\n' +
                'gradle build\n' +
                'exit\n')

        then: 'the console ended as it does at exit'
        program.exitValue() == 0

        and: 'each line was given to the shell as it was typed: its quotes, its pipe and its pattern'
        said.contains('hello from the shell')
        said.contains('two  words and $HOME')
        said.contains('a\nb\n')
        said.contains('one.txt')
        said.contains('two.txt')
        said.contains(dir.path)

        and: 'an exit code that is not 0 is said in one line'
        said.readLines().count { it.contains('| Exit code') } == 1
        said.contains('| Exit code 3\n')

        and: 'what the programs wrote is where they were run, between the prompts, and the console asked again after each'
        said.count('grails>') == 8
        inOrder(said, 'hello from the shell', 'two  words and', 'a\nb', 'one.txt', dir.path, '| Exit code 3', 'the wrapper ran with build')
    }

    @IgnoreIf({ os.windows || !ForkedCli.available() })
    void "a program run from lines that were piped into the console reads nothing of them, and the lines after it are lines of the console"() {
        given:
        project()

        when: 'a program that reads its input to the end, and lines after it whose output differs from what was typed'
        String said = console(['SHELL': '/bin/sh'], '!cat\n' +
                '!wc -c | tr -d " "\n' +
                '!echo after" "cat\n' +
                'gradle build\n')

        then: 'cat read an empty input, wc counted none of it, and every line after them was run'
        program.exitValue() == 0
        said.readLines().any { it ==~ /(grails> )+0/ }
        said.contains('after cat')
        said.contains('the wrapper ran with build')
        !said.contains('| Exit code')
    }

    // A terminal that is none reads lines and has no history to move in, so this one is an xterm for the
    // reader of the console, whose program writes to what the feature reads.
    @IgnoreIf({ os.windows })
    void "a line that runs a program goes into the history of the console, as every line does"() {
        given:
        System.setProperty('user.dir', dir.path)
        InteractiveShell shell = new InteractiveShell(new CommandLine(new Root()), { String[] args -> } as Consumer<String[]>, null)
        captureOutput()
        // the arrow up of an xterm is ESC O A
        Terminal terminal = new ExternalTerminal('console', 'xterm-256color',
                new ByteArrayInputStream('!echo up$((1+1))\n\u001bOA\nexit\n'.getBytes('UTF-8')), new ByteArrayOutputStream(), StandardCharsets.UTF_8)

        when: 'the line, and then the arrow up and Enter'
        int exit = shell.start(terminal)

        then: 'it was run twice'
        exit == 0
        out.toString().readLines().count { it == 'up2' } == 2

        cleanup:
        terminal?.close()
    }

    @Unroll
    @IgnoreIf({ os.windows || !ForkedCli.available() })
    void "the shell is the one SHELL names when it is an absolute path, and /bin/sh otherwise: #shell"() {
        given: 'a shell of its own, which says that it ran and runs sh'
        project()
        File own = new File(dir, 'own-shell')
        own.text = '#!/bin/sh\necho "the own shell ran with $*"\nexec /bin/sh "$@"\n'
        own.setExecutable(true, false)

        when:
        String said = console(shell == null ? [:] : ['SHELL': shell.replace('OWN', own.path)], '!echo "it is $0"\n')

        then:
        program.exitValue() == 0
        said.contains('the own shell ran with -c echo "it is $0"') == (shell == 'OWN')
        said.contains('it is /bin/sh')

        where:
        shell << ['OWN', null, '', 'sh']
    }

    @Unroll
    @IgnoreIf({ os.windows || !ForkedCli.available() })
    void "on a command line of its own #typed is no command, and nothing is run for it"() {
        given:
        project()

        when:
        program = command([:], [typed]).redirectInput(ProcessBuilder.Redirect.from(new File('/dev/null'))).start()
        assert program.waitFor(SECONDS, TimeUnit.SECONDS)
        String said = written.text

        then: 'refused as a mistake in the command line'
        program.exitValue() == 2
        said.contains('There is no command !')
        !said.contains('grails>')
        !new File(dir, 'made').exists()

        where:
        typed << ['!touch made', '!']
    }

    void "the mark alone runs the last command again, and not a line that ran a program; before any command it says so"() {
        given:
        System.setProperty('user.dir', dir.path)
        List<List<String>> commands = []
        InteractiveShell shell = new InteractiveShell(new CommandLine(new Root()), { String[] args -> commands.add(args.toList()) } as Consumer<String[]>, null)
        captureOutput()

        when:
        int exit = shell.start(terminal('!\n' +
                'create-app "two words"\n' +
                '# a comment\n' +
                '!echo between\n' +
                '!\n' +
                '  !  \n' +
                'exit\n'))

        then: 'the command was run three times, as it was typed'
        exit == 0
        commands == [['create-app', 'two words']] * 3

        and: 'the first mark had nothing to run'
        out.toString().readLines().count { it.startsWith(NOTHING_TO_REPEAT) } == 1
        out.toString().contains('between')
    }

    void "a line that is a command is read as it was typed: an exclamation mark in it refers to no line of the history, and a backslash is read once"() {
        given:
        List<List<String>> commands = []
        InteractiveShell shell = new InteractiveShell(new CommandLine(new Root()), { String[] args -> commands.add(args.toList()) } as Consumer<String[]>, null)
        captureOutput()

        when:
        int exit = shell.start(terminal('create-app first\n' +
                'create-app !!\n' +
                'create-app !-1\n' +
                'create-app !cre\n' +
                'create-app a\\ b "c\\"d" e\\\\f\n' +
                'exit\n'))

        then: 'what a shell of Unix would read as the last line, as a line before it or as the last line that begins with cre is given as it is'
        exit == 0
        commands == [['create-app', 'first'], ['create-app', '!!'], ['create-app', '!-1'], ['create-app', '!cre'], ['create-app', 'a b', 'c"d', 'e\\f']]
    }

    // cmd runs each side of a pipe in a cmd of its own, which is given a block in parentheses written out again with a
    // space before & and ), and echo writes that space: on Windows the lines are written to a file before the pipe
    @Unroll
    void "in a JVM whose output is not that of the process the console is given what the program writes: #command"() {
        given:
        List<List<String>> commands = []
        InteractiveShell shell = new InteractiveShell(new CommandLine(new Root()), { String[] args -> commands.add(args.toList()) } as Consumer<String[]>, null)
        System.setProperty('user.dir', dir.path)
        captureOutput()

        when:
        int exit = shell.start(terminal("!${command}\nexit\n"))

        then: 'no command was given the line'
        exit == 0
        commands.isEmpty()

        and:
        out.toString().contains(shown.replace('DIR', dir.path))
        out.toString().contains('| Exit code') == (code != 0)
        out.toString().contains("| Exit code ${code}") == (code != 0)

        where:
        command                                                           | shown                             | code
        'echo hello'                                                      | 'hello'                           | 0
        (isWindows() ? 'echo b>lines.txt& echo a>>lines.txt& type lines.txt| sort' : 'printf \'b\\na\\n\' | sort') | (isWindows() ? 'a\r\nb' : 'a\nb') | 0
        (isWindows() ? 'cd' : 'pwd -P')                                   | 'DIR'                             | 0
        'exit 3'                                                          | ''                                | 3
    }

    @IgnoreIf({ os.windows })
    void "the interrupt of the terminal while a program runs is the program's: the console waits for it and goes on"() {
        given: 'a program that does not end by itself, and a line after it'
        AtomicInteger interrupted = new AtomicInteger()
        InteractiveShell shell = new InteractiveShell(new CommandLine(new Root()), { String[] args -> } as Consumer<String[]>, null) {

            @Override
            protected void interruptedOutsideGradle() {
                interrupted.incrementAndGet()
            }
        }
        System.setProperty('user.dir', dir.path)
        File started = new File(dir, 'started.txt')
        captureOutput()
        Terminal terminal = terminal('!echo $$ > started.txt; exec sleep 300\n!echo after" "it\nexit\n')
        AtomicInteger exit = new AtomicInteger(-1)
        Thread console = new Thread({ exit.set(shell.start(terminal)) })

        when: 'Ctrl-C: the terminal sends the interrupt to the program, and to the JVM of the console'
        console.start()
        new PollingConditions(timeout: 60, delay: 0.2).eventually {
            assert started.isFile() && started.text.endsWith('\n')
        }
        terminal.raise(Terminal.Signal.INT)
        new ProcessBuilder('kill', '-INT', started.text.trim()).inheritIO().start().waitFor(30, TimeUnit.SECONDS)
        console.join(60_000)

        then: 'the program ended by it, and the console ran the next line and left at exit'
        !console.alive
        exit.get() == 0
        interrupted.get() == 0
        out.toString().contains('| Exit code 130')
        out.toString().contains('after it')

        cleanup:
        terminal?.close()
        if (started.isFile() && started.text.trim()) {
            ProcessHandle.of(started.text.trim() as long).ifPresent { ProcessHandle each -> each.destroyForcibly() }
        }
    }

    // the parser of the console reads a backslash, the separator of a path on Windows, as an escape
    @IgnoreIf({ os.windows })
    void "after the program the paths of files are completed on a line that runs one, and nothing else is"() {
        given:
        System.setProperty('user.dir', dir.path)
        new File(dir, 'src/main').mkdirs()
        new File(dir, 'readme.txt').text = 'read me'
        InteractiveShell shell = new InteractiveShell(new CommandLine(new Root()), { String[] args -> } as Consumer<String[]>, null)
        Terminal terminal = TerminalBuilder.builder().type(Terminal.TYPE_DUMB)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()).build()
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build()
        Closure<List<String>> complete = { String typed ->
            List<Candidate> candidates = []
            shell.completer().complete(reader, new DefaultParser().parse(typed, typed.length()), candidates)
            candidates*.value()
        }

        expect: 'the paths of the current directory after the program, of which the reader offers those that begin with what was typed'
        complete('!cat sr').sort() == ['readme.txt', 'src/']
        complete('! cat re').sort() == ['readme.txt', 'src/']
        complete('!ls -l ').sort() == ['readme.txt', 'src/']
        complete('!cat src/m') == ['src/main/']

        and: 'neither the program, nor a command or a file in its place'
        complete('!') == []
        complete('!ca') == []
        complete('!re') == []
        complete('! re') == []

        and: 'a line that is a command is completed with the commands'
        complete('cre').contains('create-app')
        !complete('cre').contains('readme.txt')

        cleanup:
        terminal?.close()
    }

    private static boolean isWindows() {
        return System.getProperty('os.name', '').toLowerCase(Locale.ENGLISH).contains('windows')
    }

    // a terminal that is none: the console reads the lines from it, as it reads lines that were piped into it
    private static Terminal terminal(String typed) {
        return new DumbTerminal(new ByteArrayInputStream(typed.getBytes('UTF-8')), new ByteArrayOutputStream())
    }

    private void captureOutput() {
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
    }

    // whether the texts are in the output, in this order
    private static boolean inOrder(String said, String... texts) {
        List<Integer> found = texts.collect { String text -> said.indexOf(text) }
        return !found.contains(-1) && found == found.sort(false)
    }

    private void project() {
        new File(dir, 'grails-app').mkdirs()
        File script = new File(dir, 'gradlew')
        script.text = '#!/bin/sh\necho "the wrapper ran with $*"\n'
        script.setExecutable(true, false)
    }

    // the program grails, with the lines of its console, and SHELL as the feature gives it; null removes it
    private String console(Map<String, String> environment, String lines) {
        File input = new File(dir, 'typed.txt')
        input.text = lines
        program = command(environment, []).redirectInput(input).start()
        assert program.waitFor(SECONDS, TimeUnit.SECONDS)
        return written.text
    }

    private ProcessBuilder command(Map<String, String> environment, List<String> args) {
        ProcessBuilder builder = ForkedCli.builder(dir, [], args as String[]).redirectOutput(written).redirectErrorStream(true)
        int main = builder.command().indexOf(Application.name)
        builder.command().set(main, InteractiveConsoleSpec.AsGrails.name)
        builder.environment().remove('GRAILS_SAFE_DIRECTORY')
        builder.environment().put('NO_COLOR', '1')
        builder.environment().remove('SHELL')
        builder.environment().putAll(environment)
        return builder
    }

    @CommandLine.Command(name = 'grails', subcommands = [CreateApp])
    static class Root implements Runnable {

        @Override
        void run() {
        }
    }

    @CommandLine.Command(name = 'create-app')
    static class CreateApp implements Runnable {

        @CommandLine.Parameters
        List<String> names

        @Override
        void run() {
        }
    }
}
