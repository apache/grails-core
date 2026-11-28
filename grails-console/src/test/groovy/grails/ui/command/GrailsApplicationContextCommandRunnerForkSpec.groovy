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
package grails.ui.command

import java.util.concurrent.TimeUnit

import org.springframework.context.annotation.Bean
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.boot.config.GrailsAutoConfiguration
import grails.core.GrailsApplication
import grails.ui.StartedJvmCoverage
import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.gradle.model.cli.JvmOptions

/**
 * {@link GrailsApplicationContextCommandRunner} as the {@code runTool} task of a build starts it: its
 * {@code main} method in a JVM of its own, which it ends itself with the status of the command.
 */
class GrailsApplicationContextCommandRunnerForkSpec extends Specification {

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File dir

    @Unroll
    def "#kind runs with the application context, its arguments and its flags, and the JVM ends with 0"() {
        when:
        Map result = run(command, 'one', '--flag=value', 'two', CommandRunnerApplication.name)

        then:
        result.ended
        result.exit == 0
        result.out.contains("COMMAND=${command} [one, two] value DefaultGrailsApplication true")

        where:
        kind                                     | command
        'an application tool'                    | 'fork-spec-application'
        'a tool written with a name of Grails 8' | 'fork-spec-succeeds'
    }

    def "the arguments of a file are those of the command, each as it is"() {
        given:
        File arguments = new File(dir, 'arguments.json')
        arguments.setText('["two words", "it\'s", "--flag=a value", "$HOME"]', 'UTF-8')

        when:
        Map result = run('fork-spec-succeeds', CommandRunnerApplication.name, "--grails-args-file=${arguments.path}".toString())

        then:
        result.ended
        result.exit == 0
        result.out.contains('COMMAND=fork-spec-succeeds [two words, it\'s, $HOME] a value DefaultGrailsApplication true')
    }

    @Unroll
    def "a command that #what ends the JVM with 1"() {
        when:
        Map result = run(command, CommandRunnerApplication.name)

        then:
        result.ended
        result.exit == 1
        result.out.contains("COMMAND=${command}")
        said == null || result.err.contains(said)

        where:
        what            | command                   | said
        'returns false' | 'fork-spec-returns-false' | null
        'throws'        | 'fork-spec-throws'        | 'Command execution error: this command fails on purpose'
    }

    def "a command there is none of ends the JVM with 1 and its name, and no application is started"() {
        when:
        Map result = run('fork-spec-absent', StartedApplication.name)

        then:
        result.ended
        result.exit == 1
        result.err.contains('Command not found for name: fork-spec-absent')
        !result.out.contains('APPLICATION STARTED')
    }

    def "an application whose context does not load ends the JVM with 1, and the command is not run"() {
        when:
        Map result = run('fork-spec-succeeds', FailingApplication.name)

        then:
        result.ended
        result.exit == 1
        result.err.contains('Context failed to load: ')
        result.err.contains('this context fails on purpose')
        !result.out.contains('COMMAND=')
    }

    @Unroll
    def "the JVM ends with 1 when it is started #what"() {
        when:
        Map result = run(arguments as String[])

        then:
        result.ended
        result.exit == 1
        result.err.contains(said)
        !result.out.contains('COMMAND=')

        where:
        what                                            | arguments                                                                     | said
        'with a class of the application there is none' | ['fork-spec-succeeds', 'no.such.Application']                                 | 'runTool: Application class no.such.Application not found'
        'without the class of the application'          | ['fork-spec-succeeds']                                                        | 'Missing application class name and script name arguments'
        'with nothing'                                  | []                                                                            | 'Missing application class name and script name arguments'
        'with a file of arguments there is none of'     | ['fork-spec-succeeds', CommandRunnerApplication.name, '--grails-args-file=/no/such'] | 'runTool: The file of arguments does not exist or is not a file.'
    }

    private Map run(String... arguments) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${dir.path}".toString(),
                                GrailsApplicationContextCommandRunner.name]
        command.addAll(1, StartedJvmCoverage.jvmArguments())
        // the options the Grails Gradle plugin gives the task that starts this JVM
        command.addAll(1, JvmOptions.of(Runtime.version().feature()))
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

class CommandRunnerApplication extends GrailsAutoConfiguration {
}

class StartedApplication extends GrailsAutoConfiguration {

    StartedApplication() {
        println 'APPLICATION STARTED'
    }
}

class FailingApplication extends GrailsAutoConfiguration {

    @Bean
    Runnable failing() {
        throw new IllegalStateException('this context fails on purpose')
    }
}

class ForkSpecSucceedsCommand implements ApplicationCommand {

    GrailsApplication grailsApplication

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name} ${executionContext.commandLine.remainingArgs} ${executionContext.commandLine.optionValue('flag')} " +
                "${grailsApplication?.getClass()?.simpleName} ${applicationContext?.isActive()}"
        true
    }
}

class ForkSpecApplicationTool implements ApplicationTool {

    GrailsApplication grailsApplication

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name} ${executionContext.commandLine.remainingArgs} ${executionContext.commandLine.optionValue('flag')} " +
                "${grailsApplication?.getClass()?.simpleName} ${applicationContext?.isActive()}"
        true
    }
}

class ForkSpecReturnsFalseCommand implements ApplicationCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        false
    }
}

class ForkSpecThrowsCommand implements ApplicationCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        throw new IllegalStateException('this command fails on purpose')
    }
}
