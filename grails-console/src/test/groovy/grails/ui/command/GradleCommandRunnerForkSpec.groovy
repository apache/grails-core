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
import grails.ui.StartedJvmCoverage
import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.GradleCommand

/**
 * A {@link GradleCommand} as the {@code runCommand} task of a build starts it: by the {@code main}
 * method of {@link GrailsApplicationContextCommandRunner} in a JVM of its own, which runs the command
 * without the application and ends with the status of the command.
 */
class GradleCommandRunnerForkSpec extends Specification {

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File dir

    def "a command the build runs is given its arguments and its flags, without the application, and the JVM ends with 0"() {
        when:
        Map result = run('gradle-kind-spec-succeeds', 'one', '--flag=value', 'two', '--bare', GradleKindStartedApplication.name)

        then:
        result.ended
        result.exit == 0
        result.out.contains('COMMAND=gradle-kind-spec-succeeds [one, two] value true context=null injected=null')

        and: 'the class of the application was neither made nor initialized'
        !result.out.contains('APPLICATION')
    }

    def "a command the build runs is run for an application whose context does not load"() {
        when:
        Map result = run('gradle-kind-spec-succeeds', GradleKindFailingApplication.name)

        then:
        result.ended
        result.exit == 0
        result.out.contains('COMMAND=gradle-kind-spec-succeeds')
        !result.err.contains('Context failed to load')
    }

    def "the arguments of a file are those of a command the build runs, each as it is"() {
        given:
        File arguments = new File(dir, 'arguments.json')
        arguments.setText('["two words", "--flag=a value", "$HOME"]', 'UTF-8')

        when:
        Map result = run('gradle-kind-spec-succeeds', GradleKindStartedApplication.name, "--grails-args-file=${arguments.path}".toString())

        then:
        result.ended
        result.exit == 0
        result.out.contains('COMMAND=gradle-kind-spec-succeeds [two words, $HOME] a value null context=null injected=null')
    }

    @Unroll
    def "a command the build runs that #what ends the JVM with 1"() {
        when:
        Map result = run(command, GradleKindStartedApplication.name)

        then:
        result.ended
        result.exit == 1
        result.out.contains("COMMAND=${command}")
        said == null || result.err.contains(said)
        !result.out.contains('APPLICATION')

        where:
        what            | command                          | said
        'returns false' | 'gradle-kind-spec-returns-false' | null
        'throws'        | 'gradle-kind-spec-throws'        | 'Command execution error: this command fails on purpose'
    }

    def "a command the build runs renders a template of the project into the project without the application"() {
        given: 'a template, where the templates of a project are'
        File template = new File(dir, 'src/main/templates/artifacts/Report.groovy')
        template.parentFile.mkdirs()
        template.text = 'package ${packageName}\n\nclass ${className}Report {\n}\n'

        when:
        Map result = run('create-gradle-kind-report', 'com.example.Sales', GradleKindFailingApplication.name)

        then:
        result.ended
        result.exit == 0
        new File(dir, 'src/main/groovy/com/example/SalesReport.groovy').text == 'package com.example\n\nclass SalesReport {\n}\n'

        when: 'it is given no name'
        result = run('create-gradle-kind-report', GradleKindFailingApplication.name)

        then:
        result.ended
        result.exit == 1
        result.err.contains('Usage: create-gradle-kind-report <name>')
    }

    private Map run(String... arguments) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${dir.path}".toString(),
                                GrailsApplicationContextCommandRunner.name]
        command.addAll(1, StartedJvmCoverage.jvmArguments())
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

class GradleKindStartedApplication extends GrailsAutoConfiguration {

    static {
        println 'APPLICATION INITIALIZED'
    }

    GradleKindStartedApplication() {
        println 'APPLICATION STARTED'
    }
}

class GradleKindFailingApplication extends GrailsAutoConfiguration {

    @Bean
    Runnable failing() {
        throw new IllegalStateException('this context fails on purpose')
    }
}

class GradleKindSpecSucceedsCommand implements ApplicationCommand, GradleCommand {

    Object grailsApplication

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name} ${executionContext.commandLine.remainingArgs} ${executionContext.commandLine.optionValue('flag')} " +
                "${executionContext.commandLine.optionValue('bare')} context=${applicationContext} injected=${grailsApplication}"
        true
    }
}

class GradleKindSpecReturnsFalseCommand implements ApplicationCommand, GradleCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        false
    }
}

class GradleKindSpecThrowsCommand implements ApplicationCommand, GradleCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        throw new IllegalStateException('this command fails on purpose')
    }
}

/**
 * The command the guide shows for a command that renders a template without the application.
 */
class CreateGradleKindReportCommand implements GrailsApplicationCommand, GradleCommand {

    String description = 'Creates a report class from a template'

    boolean handle() {
        if (!args) {
            System.err.println('Usage: create-gradle-kind-report <name>')
            return false
        }
        def model = model(args[0])
        render template: template('artifacts/Report.groovy'),
               destination: file("src/main/groovy/${model.packagePath}/${model.simpleName}Report.groovy"),
               model: model
        true
    }
}
