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
import grails.util.Environment
import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolSupport

/**
 * A tool that does not start the application, as the {@code runTool} task of a build starts it: by the
 * {@code main} method of {@link GrailsApplicationContextCommandRunner} in a JVM of its own, which runs the
 * tool without the application, injects nothing, and ends with the status of the tool. An application
 * tool says so with {@code startsApplication = false}; a standalone tool never starts it.
 */
class ApplicationToolRunnerForkSpec extends Specification {

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File dir

    @Unroll
    def "an application tool that says #how it does not start the application is given its arguments and flags, without the application, and the JVM ends with 0"() {
        when:
        Map result = run(command, 'one', '--flag=value', 'two', '--bare', ToolKindStartedApplication.name)

        then:
        result.ended
        result.exit == 0
        result.out.contains("COMMAND=${command} [one, two] value true context=null injected=null")

        and: 'the class of the application was neither made nor initialized'
        !result.out.contains('APPLICATION')

        where:
        how                       | command
        'with its property'       | 'tool-kind-spec-succeeds'
        'in its constructor'      | 'tool-kind-spec-constructed'
        'with a name of Grails 8' | 'tool-kind-spec-old'
    }

    def "a standalone tool that the build is asked to run is run as a tool the build runs: without the application"() {
        when:
        Map result = run('standalone-kind-spec', 'one', ToolKindFailingApplication.name)

        then:
        result.ended
        result.exit == 0
        result.out.contains('COMMAND=standalone-kind-spec [one] context=false')
        !result.out.contains('APPLICATION')
    }

    def "a tool that does not start the application is run for an application whose context does not load"() {
        when:
        Map result = run('tool-kind-spec-succeeds', ToolKindFailingApplication.name)

        then:
        result.ended
        result.exit == 0
        result.out.contains('COMMAND=tool-kind-spec-succeeds')
        !result.err.contains('Context failed to load')
    }

    def "a tool that starts the application is not run when its context does not load: the application is started for it"() {
        when:
        Map result = run('tool-kind-spec-starts', ToolKindFailingApplication.name)

        then:
        result.ended
        result.exit == 1
        result.err.contains('Context failed to load')
        !result.out.contains('COMMAND=')
    }

    def "the arguments of a file are those of a tool that does not start the application, each as it is"() {
        given:
        File arguments = new File(dir, 'arguments.json')
        arguments.setText('["two words", "--flag=a value", "$HOME"]', 'UTF-8')

        when:
        Map result = run('tool-kind-spec-succeeds', ToolKindStartedApplication.name, "--grails-args-file=${arguments.path}".toString())

        then:
        result.ended
        result.exit == 0
        result.out.contains('COMMAND=tool-kind-spec-succeeds [two words, $HOME] a value null context=null injected=null')
    }

    @Unroll
    def "a tool that does not start the application and #what ends the JVM with 1"() {
        when:
        Map result = run(command, ToolKindStartedApplication.name)

        then:
        result.ended
        result.exit == 1
        result.out.contains("COMMAND=${command}")
        said == null || result.err.contains(said)
        !result.out.contains('APPLICATION')

        where:
        what            | command                        | said
        'returns false' | 'tool-kind-spec-returns-false' | null
        'throws'        | 'tool-kind-spec-throws'        | 'Command execution error: this tool fails on purpose'
    }

    def "a tool that does not start the application renders a template of the project into the project"() {
        given: 'a template, where the templates of a project are'
        File template = new File(dir, 'src/main/templates/artifacts/Report.groovy')
        template.parentFile.mkdirs()
        template.text = 'package ${packageName}\n\nclass ${className}Report {\n}\n'

        when:
        Map result = run('create-tool-kind-report', 'com.example.Sales', ToolKindFailingApplication.name)

        then:
        result.ended
        result.exit == 0
        new File(dir, 'src/main/groovy/com/example/SalesReport.groovy').text == 'package com.example\n\nclass SalesReport {\n}\n'

        when: 'it is given no name'
        result = run('create-tool-kind-report', ToolKindFailingApplication.name)

        then:
        result.ended
        result.exit == 1
        result.err.contains('Usage: create-tool-kind-report <name>')
    }

    def "a tool run by the build is told what the project is, as the command line tells a standalone tool"() {
        given: 'the record of the command line in the directory of the project'
        File record = new File(dir, '.grails/forge.lock.yml')
        record.parentFile.mkdirs()
        record.text = 'format: 1\napplicationType: rest-api\nfeatures:\n- name: views-json\n  source: bundled-pack\n- name: h2\n  source: bundled-pack\n'

        when:
        Map result = run('tool-kind-spec-project', ToolKindStartedApplication.name)

        then:
        result.ended
        result.exit == 0
        result.out.contains('PROJECT=true rest-api [views-json, h2]')
    }

    @Unroll
    def "-Dgrails.env=production among the arguments of a tool that #how is one of them, and does not change the environment the tool runs in"() {
        when: 'the JVM is started as the runTool task starts it, for the environment development'
        Map result = run(command, 'report', '-Dgrails.env=production', ToolKindStartedApplication.name)

        then: 'the tool runs in development, and finds what it was given in the system properties of its command line'
        result.ended
        result.exit == 0
        result.out.readLines().contains("ENVIRONMENT=${command} development given=production remaining=[report] context=${context}".toString())

        where:
        how                                | command                          || context
        'does not start the application'   | 'tool-kind-spec-environment'     || false
        'starts the application'           | 'tool-kind-spec-environment-app' || true
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

class ToolKindStartedApplication extends GrailsAutoConfiguration {

    static {
        println 'APPLICATION INITIALIZED'
    }

    ToolKindStartedApplication() {
        println 'APPLICATION STARTED'
    }
}

class ToolKindFailingApplication extends GrailsAutoConfiguration {

    @Bean
    Runnable failing() {
        throw new IllegalStateException('this context fails on purpose')
    }
}

class ToolKindSpecSucceedsTool implements ApplicationTool {

    boolean startsApplication = false

    Object grailsApplication

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name} ${executionContext.commandLine.remainingArgs} ${executionContext.commandLine.optionValue('flag')} " +
                "${executionContext.commandLine.optionValue('bare')} context=${applicationContext} injected=${grailsApplication}"
        true
    }
}

class ToolKindSpecConstructedTool implements ApplicationTool {

    Object grailsApplication

    ToolKindSpecConstructedTool() {
        startsApplication = false
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name} ${executionContext.commandLine.remainingArgs} ${executionContext.commandLine.optionValue('flag')} " +
                "${executionContext.commandLine.optionValue('bare')} context=${applicationContext} injected=${grailsApplication}"
        true
    }
}

class ToolKindSpecOldCommand implements GrailsApplicationCommand {

    boolean startsApplication = false

    Object grailsApplication

    boolean handle() {
        println "COMMAND=${name} ${args} ${executionContext.commandLine.optionValue('flag')} " +
                "${executionContext.commandLine.optionValue('bare')} context=${applicationContext} injected=${grailsApplication}"
        true
    }
}

class ToolKindSpecStartsTool implements ApplicationTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        true
    }
}

class StandaloneKindSpecTool implements StandaloneTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name} ${executionContext.commandLine.remainingArgs} context=${metaClass.hasProperty(this, 'applicationContext') != null}"
        true
    }
}

class ToolKindSpecReturnsFalseTool implements ApplicationTool {

    boolean startsApplication = false

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        false
    }
}

class ToolKindSpecThrowsTool implements ApplicationTool {

    boolean startsApplication = false

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "COMMAND=${name}"
        throw new IllegalStateException('this tool fails on purpose')
    }
}

class ToolKindSpecProjectTool implements ApplicationTool {

    boolean startsApplication = false

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "PROJECT=${executionContext.project.recorded} ${executionContext.project.applicationType} ${executionContext.project.features}"
        true
    }
}

class ToolKindSpecEnvironmentTool implements ApplicationTool {

    boolean startsApplication = false

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "ENVIRONMENT=${name} ${Environment.current.name} given=${executionContext.commandLine.systemProperties.getProperty(Environment.KEY)} " +
                "remaining=${executionContext.commandLine.remainingArgs} context=${applicationContext != null}"
        true
    }
}

class ToolKindSpecEnvironmentAppTool implements ApplicationTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "ENVIRONMENT=${name} ${Environment.current.name} given=${executionContext.commandLine.systemProperties.getProperty(Environment.KEY)} " +
                "remaining=${executionContext.commandLine.remainingArgs} context=${applicationContext != null}"
        true
    }
}

/**
 * The tool the guide shows for a tool that renders a template without the application.
 */
class CreateToolKindReportTool implements ApplicationTool, ToolSupport {

    boolean startsApplication = false

    String description = 'Creates a report class from a template'

    boolean handle() {
        if (!args) {
            System.err.println('Usage: create-tool-kind-report <name>')
            return false
        }
        def model = model(args[0])
        render template: template('artifacts/Report.groovy'),
               destination: file("src/main/groovy/${model.packagePath}/${model.simpleName}Report.groovy"),
               model: model
        true
    }
}
