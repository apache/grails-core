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

    private Map run(String... arguments) {
        File registrations = new File(dir, 'registrations')
        new File(registrations, 'META-INF').mkdirs()
        new File(registrations, 'META-INF/grails-cli.factories').text = "${GrailsTool.name}=" +
                [RunnerSpecSucceedsCommand, RunnerSpecReturnsFalseCommand, RunnerSpecThrowsCommand, RunnerSpecGradleCommand, RunnerSpecApplicationCommand,
                 RunnerSpecOldCommand]*.name.join(',') + '\n'
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', registrations.path + File.pathSeparator + System.getProperty('java.class.path'),
                                "-Dbase.dir=${dir.path}".toString(),
                                StandaloneToolRunner.name]
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

class RunnerSpecOldCommand implements ApplicationCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        true
    }
}
