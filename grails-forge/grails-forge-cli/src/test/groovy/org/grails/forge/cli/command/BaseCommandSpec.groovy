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
package org.grails.forge.cli.command

import java.util.concurrent.Callable
import java.util.function.Supplier
import org.grails.forge.application.OperatingSystem
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import org.springframework.context.ApplicationContext
import picocli.CommandLine
import spock.lang.Specification
import spock.lang.Unroll

/**
 * What every command of the command line has from {@link BaseCommand}: where what it says is written,
 * what is written when it fails, the options that are taken in front of its name as after it, and
 * the system it takes itself to run on. The command here is one of the specification, added to the
 * command line as the commands of a project are; the application that generates is not started.
 */
class BaseCommandSpec extends Specification {

    static final String ESCAPE = '\u001b['

    PrintStream originalOut
    PrintStream originalErr
    Map<String, String> previous = [:]
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    ForgeContext context = new ForgeContext({ throw new IllegalStateException('no command here generates') } as Supplier<ApplicationContext>)
    Says command = new Says()

    void setup() {
        ['picocli.ansi', 'os.name', 'os.arch'].each { String name -> previous[name] = System.getProperty(name) }
        // an output that is no terminal, whatever runs this
        System.setProperty('picocli.ansi', 'false')
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        previous.each { String name, String value ->
            if (value == null) {
                System.clearProperty(name)
            } else {
                System.setProperty(name, value)
            }
        }
        context.close()
    }

    private int run(String... args) {
        CommandLine commandLine = ForgeCli.createCommandLine(context, true, false)
        commandLine.addSubcommand(command)
        return ForgeCli.execute(commandLine, args)
    }

    void "what a command says is its output, and an error or a warning about the command line is on the error stream"() {
        when:
        int exit = run('says')

        then:
        exit == 0
        out.toString().readLines() == ['said', '| Warning careful', 'fine', 'bad']
        err.toString().readLines() == ['| Error wrong', '| Warning of the project']
    }

    @Unroll
    void "on a terminal what a command says is marked up for it, and with --plain-output #place the command it is not"() {
        given: 'an output that is taken for a terminal'
        System.setProperty('picocli.ansi', 'true')

        when:
        int exit = run('says')
        String marked = out.toString() + err.toString()

        then:
        exit == 0
        ['said', '| Warning', 'careful', 'fine', 'bad', '| Error', 'wrong', 'of the project'].every { String text -> marked.contains(text) }
        marked.contains(ESCAPE)

        when:
        out.reset()
        err.reset()
        exit = run(line as String[])

        then: 'the same lines, as they are where the output is no terminal'
        exit == 0
        out.toString().readLines() == ['said', '| Warning careful', 'fine', 'bad']
        err.toString().readLines() == ['| Error wrong', '| Warning of the project']

        where:
        place    | line
        'before' | ['--plain-output', 'says']
        'after'  | ['says', '--plain-output']
    }

    @Unroll
    void "a command that fails says why in one line and ends with 1, and shows where it failed only when #line asks for it"() {
        when:
        int exit = run(line as String[])

        then:
        exit == 1
        err.toString().readLines().first() == '| Error the command broke on purpose'
        err.toString().contains('\tat ') == trace
        err.toString().contains('java.lang.IllegalStateException: the command broke on purpose') == trace
        out.toString().isEmpty()

        and: 'it is no mistake in the use of the command'
        !err.toString().contains('Usage:')

        where:
        line                             | trace
        ['says', '--fail']               | false
        ['-v', 'says', '--fail']         | false
        ['says', '--fail', '-x']         | true
        ['says', '--stacktrace', '--fail'] | true
        ['-x', 'says', '--fail']         | true
        ['--stacktrace', 'says', '--fail'] | true
    }

    @Unroll
    void "a command is told what #line asks of every command, in front of its name as after it"() {
        when:
        int exit = run(line as String[])

        then:
        exit == 0
        command.verbose == verbose
        command.stacktrace == stacktrace
        command.nonInteractive == nonInteractive

        where:
        line                                    | verbose | stacktrace | nonInteractive
        ['says']                                | false   | false      | false
        ['-v', 'says']                          | true    | false      | false
        ['says', '--verbose']                   | true    | false      | false
        ['-x', 'says']                          | false   | true       | false
        ['says', '-x']                          | false   | true       | false
        ['--non-interactive', 'says']           | false   | false      | true
        ['says', '--non-interactive']           | false   | false      | true
        ['-xv', '--non-interactive', 'says']    | true    | true       | true
        ['-x', 'says', '-v']                    | true    | true       | false
    }

    @Unroll
    void "on #name (#architecture) a command takes itself to run on #expected"() {
        given:
        System.setProperty('os.name', name)
        System.setProperty('os.arch', architecture)

        expect:
        command.operatingSystem == expected

        where:
        name         | architecture | expected
        'Mac OS X'   | 'aarch64'    | OperatingSystem.MACOS_ARCH64
        'Mac OS X'   | 'x86_64'     | OperatingSystem.MACOS
        // has the letters of Windows in it, and is none
        'Darwin'     | 'x86_64'     | OperatingSystem.MACOS
        'Linux'      | 'aarch64'    | OperatingSystem.LINUX
        'Linux'      | 'amd64'      | OperatingSystem.LINUX
        'Windows 11' | 'amd64'      | OperatingSystem.WINDOWS
        'SunOS'      | 'sparcv9'    | OperatingSystem.SOLARIS
        'Solaris'    | 'amd64'      | OperatingSystem.SOLARIS
        'FreeBSD'    | 'amd64'      | null
        ''           | ''           | null
    }

    @CommandLine.Command(name = 'says', description = 'Says something in every way a command can')
    static class Says extends BaseCommand implements Callable<Integer> {

        @CommandLine.Option(names = ['--fail'])
        boolean fail

        boolean verbose
        boolean stacktrace
        boolean nonInteractive

        @Override
        Integer call() {
            verbose = verbose()
            stacktrace = showStacktrace()
            nonInteractive = nonInteractive()
            if (fail) {
                throw new IllegalStateException('the command broke on purpose')
            }
            out('said')
            warning('careful')
            green('fine')
            red('bad')
            err('wrong')
            warned('of the project')
            return 0
        }
    }
}
