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
package org.apache.grails.core.cli

import java.util.concurrent.TimeUnit

import groovy.json.JsonOutput

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.util.Environment

/**
 * A {@link StandaloneTool} as the {@code grails} command line starts it: by the {@code main}
 * method of {@link StandaloneToolRunner} in a JVM of its own, which is no build and starts no
 * application, and ends with the status of the command.
 */
class StandaloneToolRunnerSpec extends Specification {

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File dir

    def "a standalone command is given its arguments, its flags and the directory of the project, and the JVM ends with 0"() {
        when:
        Map result = run('runner-spec-succeeds', 'one', '--flag=value', 'two', '--bare')

        then:
        result.ended
        result.exit == 0
        result.out.readLines() == ["COMMAND=runner-spec-succeeds [one, two] value true context=null base=${dir.canonicalPath}".toString()]
        result.err.isEmpty()
    }

    def "the arguments of a file are those of the command, each as it is"() {
        given:
        File arguments = new File(dir, 'arguments.json')
        arguments.setText(JsonOutput.toJson(['runner-spec-succeeds', 'two words', '--flag=a value', '$HOME']), 'UTF-8')

        when:
        Map result = run("--grails-args-file=${arguments.path}".toString())

        then:
        result.exit == 0
        result.out.readLines() == ["COMMAND=runner-spec-succeeds [two words, \$HOME] a value null context=null base=${dir.canonicalPath}".toString()]
    }

    def "a file of arguments that cannot be used ends the JVM with 1, and no command is run"() {
        when:
        Map result = run("--grails-args-file=${new File(dir, 'missing.json').path}".toString())

        then:
        result.exit == 1
        result.out.isEmpty()
        result.err.readLines() == ['The file of arguments does not exist or is not a file.']
    }

    @Unroll
    def "a standalone command that #what ends the JVM with 1"() {
        when:
        Map result = run(command)

        then:
        result.ended
        result.exit == 1
        result.out.readLines() == ["COMMAND=${command}".toString()]
        result.err.readLines() == said

        where:
        what            | command                     | said
        'returns false' | 'runner-spec-returns-false' | []
        'throws'        | 'runner-spec-throws'        | ['Command execution error: this command fails on purpose']
    }

    @Unroll
    def "an application tool that is #kind is not run as a process of its own, and is told how it is run"() {
        when:
        Map result = run(command, 'argument')

        then:
        result.exit == 1
        result.out.isEmpty()
        result.err.readLines() == ["The tool ${command} is not a StandaloneTool, so it is not run as a process of its own. " +
                                           "The build of the project runs it: grails run-tool ${command}"]

        where:
        kind                                          | command
        'run by the build without the application'    | 'runner-spec-gradle'
        'run with the application'                    | 'runner-spec-application'
        'written with a name of Grails 8'             | 'runner-spec-old'
    }

    @Unroll
    def "-Dgrails.env=production among the arguments of a standalone tool is #how, and does not change the environment the tool runs in"() {
        when: 'the JVM is started as the command line starts it, for the environment development'
        Map result = runWith(['-Dgrails.env=development'], 'runner-spec-environment', *arguments)

        then: 'the tool runs in development, and finds what it was given in the system properties of its command line'
        result.ended
        result.exit == 0
        result.out.readLines() == ["ENVIRONMENT=development given=production remaining=${remaining}".toString()]

        where:
        how                                     | arguments                                      || remaining
        'its only argument'                     | ['-Dgrails.env=production']                    || []
        'one of its arguments'                  | ['report', '-Dgrails.env=production', 'more'] || ['report', 'more']
    }

    def "a tool the compiler did not see, as a Java tool, is held to the declarations of its class: its fields are given what it was given"() {
        when:
        Map result = run('runner-spec-declaring', '--count', '3', '-v', 'report.txt')

        then:
        result.exit == 0
        result.err.isEmpty()
        result.out.readLines() == ['DECLARED count=3 verbose=true output=report.txt remaining=[report.txt] v=true verbose-option=true']
    }

    @Unroll
    def "a tool that declares its arguments and is given #what is refused before it runs, and the JVM ends with 2"() {
        when:
        Map result = run((['runner-spec-declaring'] + given) as String[])

        then:
        result.exit == 2
        result.out.isEmpty()
        result.err.readLines() == ["| Error runner-spec-declaring: ${refused}. grails help runner-spec-declaring shows its options".toString()]

        where:
        what                     | given                          || refused
        'an option it lacks'     | ['--nope', 'report.txt']       || '--nope is not one of its options'
        'no parameter'           | ['--count', '3']               || 'OUTPUT is required'
        'a count that is none'   | ['--count', 'three', 'a.txt']  || '--count takes a whole number'
    }

    def "a tool whose declarations break a rule is not run, and the JVM ends with 1 and the rule"() {
        when:
        Map result = run('runner-spec-broken', 'anything')

        then:
        result.exit == 1
        result.out.isEmpty()
        result.err.readLines() == ['| Error runner-spec-broken: @ToolOption on RunnerSpecBrokenTool.since has a name that is neither a long name ' +
                                           '(two hyphens, a lower-case letter, then letters, digits and single hyphens, at most 32 characters, as --since) ' +
                                           'nor a short name (a hyphen and one letter, as -s).']
    }

    def "a name that no command has, and no name at all, end the JVM with 1"() {
        when:
        Map unknown = run('runner-spec-no-such-command')
        Map none = run()

        then:
        unknown.exit == 1
        unknown.err.readLines()[0] == 'Command not found for name: runner-spec-no-such-command'
        none.exit == 1
        none.err.readLines() == ['Missing the name of the command']
    }

    def "what the runner says reaches the standard error also when a tool has made the console of Grails, which writes System.err to the output"() {
        when: 'the tools are made, and one makes the console in a field, as the tools of scaffolding and Spring Security do'
        Map ran = runWithTools([RunnerSpecConsoleTool], [], 'runner-spec-console', 'ran')
        Map refused = runWithTools([RunnerSpecConsoleTool], [], 'runner-spec-console', '--nope', 'ran')
        Map thrown = runWithTools([RunnerSpecConsoleTool], [], 'runner-spec-console', 'fail')
        Map unknown = runWithTools([RunnerSpecConsoleTool], [], 'runner-spec-no-such-command')

        then: 'what the tool prints to System.err goes to the output: the console is there'
        ran.exit == 0
        ran.out.contains('PRINTED TO SYSTEM.ERR BY ran')
        !ran.err.contains('PRINTED TO SYSTEM.ERR')

        and: 'the refusal is one line on the standard error, and nothing of it is in the output'
        refused.exit == 2
        refused.err.readLines() == ['| Error runner-spec-console: --nope is not one of its options. grails help runner-spec-console shows its options']
        !refused.out.contains('--nope')
        !refused.out.contains('Error |')

        and: 'so is what the runner says of a tool that throws, and of a name no tool has'
        thrown.exit == 1
        thrown.err.readLines() == ['Command execution error: this tool fails on purpose']
        !thrown.out.contains('fails on purpose')
        unknown.exit == 1
        unknown.err.readLines()[0] == 'Command not found for name: runner-spec-no-such-command'
        !unknown.out.contains('Command not found')
    }

    private Map run(String... arguments) {
        return runWith([], arguments)
    }

    private Map runWith(List<String> jvmArguments, String... arguments) {
        runWithTools([RunnerSpecSucceedsCommand, RunnerSpecReturnsFalseCommand, RunnerSpecThrowsCommand, RunnerSpecGradleCommand, RunnerSpecApplicationCommand,
                      RunnerSpecOldCommand, RunnerSpecEnvironmentCommand, RunnerSpecDeclaringTool, RunnerSpecBrokenTool], jvmArguments, arguments)
    }

    // the tools of the class path are these, which are all made when the registry is
    private Map runWithTools(List<Class> tools, List<String> jvmArguments, String... arguments) {
        File registrations = new File(dir, 'registrations')
        new File(registrations, 'META-INF').mkdirs()
        new File(registrations, 'META-INF/grails-cli.factories').text = "${GrailsTool.name}=" + tools*.name.join(',') + '\n'
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', registrations.path + File.pathSeparator + System.getProperty('java.class.path'),
                                "-Dbase.dir=${dir.path}".toString()]
        command.addAll(jvmArguments)
        command.add(StandaloneToolRunner.name)
        command.addAll(arguments.toList())
        File out = new File(dir, 'out.txt')
        File err = new File(dir, 'err.txt')
        Process process = new ProcessBuilder(command).directory(dir).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        [ended: ended, exit: ended ? process.exitValue() : null, out: out.text, err: err.text]
    }
}

class RunnerSpecSucceedsCommand implements StandaloneTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name} ${executionContext.commandLine.remainingArgs} ${executionContext.commandLine.optionValue('flag')} " +
                "${executionContext.commandLine.optionValue('bare')} context=${metaClass.hasProperty(this, 'applicationContext')} base=${executionContext.baseDir.canonicalPath}"
        true
    }
}

class RunnerSpecReturnsFalseCommand implements StandaloneTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        false
    }
}

class RunnerSpecThrowsCommand implements StandaloneTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        throw new IllegalStateException('this command fails on purpose')
    }
}

class RunnerSpecGradleCommand implements ApplicationTool {

    boolean startsApplication = false

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        true
    }
}

class RunnerSpecApplicationCommand implements ApplicationTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        true
    }
}

class RunnerSpecEnvironmentCommand implements StandaloneTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "ENVIRONMENT=${Environment.current.name} given=${executionContext.commandLine.systemProperties.getProperty(Environment.KEY)} " +
                "remaining=${executionContext.commandLine.remainingArgs}"
        true
    }
}

class RunnerSpecDeclaringTool implements StandaloneTool {

    @ToolOption(names = ['--count'], description = 'How many')
    int count

    @ToolOption(names = ['--verbose', '-v'])
    boolean verbose

    @ToolParameter(index = 0, label = 'OUTPUT')
    String output

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "DECLARED count=${count} verbose=${verbose} output=${output} remaining=${executionContext.commandLine.remainingArgs} " +
                "v=${executionContext.commandLine.optionValue('v')} verbose-option=${executionContext.commandLine.optionValue('verbose')}"
        true
    }
}

class RunnerSpecConsoleTool implements StandaloneTool {

    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @ToolOption(names = ['--force'], description = 'Writes over')
    boolean force

    @ToolParameter(index = 0, label = 'NAME')
    String target

    @Override
    boolean handle(ExecutionContext executionContext) {
        if (target == 'fail') {
            throw new IllegalStateException('this tool fails on purpose')
        }
        System.err.println("PRINTED TO SYSTEM.ERR BY ${target}")
        true
    }
}

class RunnerSpecBrokenTool implements StandaloneTool {

    @ToolOption(names = ['since'])
    String since

    @Override
    boolean handle(ExecutionContext executionContext) {
        println 'BROKEN RAN'
        true
    }
}

class RunnerSpecOldCommand implements ApplicationCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        true
    }
}
