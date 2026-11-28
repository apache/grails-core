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

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.ui.StartedJvmCoverage
import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.gradle.model.cli.JvmOptions
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolOption
import org.apache.grails.core.cli.ToolParameter

/**
 * A tool that declares its options and parameters, as the {@code runTool} task of a build starts it: by
 * the {@code main} method of {@link GrailsApplicationContextCommandRunner} in a JVM of its own. It is held
 * to its declarations before anything is started for it, is given them on its fields after the
 * application has injected what it injects, and gives its result beside the file of its arguments.
 */
class DeclaredToolArgumentsForkSpec extends Specification {

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File dir

    @Unroll
    def "a tool that declares its arguments and #how is given them on its fields, with a command line that agrees"() {
        when:
        Map result = run(command, '--force', 'out.txt', '-s', '2026-01-01', ToolKindStartedApplication.name)

        then:
        result.ended
        result.exit == 0
        result.out.readLines().contains("DECLARED since=2026-01-01 force=true output=out.txt remaining=[out.txt] s=2026-01-01 context=${context}".toString())

        where:
        how                                | command                       || context
        'does not start the application'   | 'declared-arguments-no-app'   || false
        'starts the application'           | 'declared-arguments-app'      || true
    }

    @Unroll
    def "a tool that declares its arguments and #how is refused before anything is started for it, and the JVM ends with 2"() {
        when:
        Map result = run(command, '--nope', 'out.txt', ToolKindFailingApplication.name)

        then:
        result.ended
        result.exit == 2
        result.err.readLines().contains("| Error ${command}: --nope is not one of its options. grails help ${command} shows its options".toString())
        !result.err.contains('Context failed to load')
        !result.out.contains('DECLARED')

        where:
        how                                | command
        'does not start the application'   | 'declared-arguments-no-app'
        'starts the application'           | 'declared-arguments-app'
    }

    @Unroll
    def "a tool that #how gives its result beside the file of its arguments, with its number"() {
        given:
        File runDirectory = new File(dir, '.grails/run')
        runDirectory.mkdirs()
        File arguments = new File(runDirectory, 'command-args-4.json')
        arguments.setText('["--force", "out.txt"]', 'UTF-8')
        File stale = new File(runDirectory, 'tool-result-4.json')
        stale.text = '{"stale":true}'

        when:
        Map result = run(command, ToolKindStartedApplication.name, "--grails-args-file=${arguments.path}".toString())

        then:
        result.ended
        result.exit == 0
        stale.text == '{"output":"out.txt","force":true}'

        where:
        how                                | command
        'does not start the application'   | 'declared-arguments-no-app'
        'starts the application'           | 'declared-arguments-app'
    }

    @Unroll
    def "what the runner says reaches the standard error also when a tool that #how has made the console of Grails, which writes System.err to the output"() {
        given: 'a tool that makes the console in a field, as the tools of scaffolding and Spring Security do, on the class path of this JVM only'
        File classes = compileConsoleTool(startsApplication)

        when:
        Map ran = runWith(classes, 'console-keeping', 'ran', application)
        Map refused = runWith(classes, 'console-keeping', '--nope', 'ran', application)
        Map thrown = runWith(classes, 'console-keeping', 'fail', application)

        then: 'what the tool prints to System.err goes to the output: the console is there'
        ran.exit == 0
        ran.out.contains('PRINTED TO SYSTEM.ERR BY ran')
        !ran.err.contains('PRINTED TO SYSTEM.ERR')

        and: 'the refusal is one line on the standard error, before anything is started, and nothing of it is in the output'
        refused.exit == 2
        refused.err.readLines().contains('| Error console-keeping: --nope is not one of its options. grails help console-keeping shows its options')
        !refused.out.contains('--nope')
        !refused.out.contains('Error |')
        !refused.out.contains('APPLICATION STARTED')

        and: 'so is what the runner says of a tool that throws'
        thrown.exit == 1
        thrown.err.readLines().contains('Command execution error: this tool fails on purpose')
        !thrown.out.contains('fails on purpose')

        where:
        how                                | startsApplication || application
        'does not start the application'   | false             || ToolKindFailingApplication.name
        'starts the application'           | true              || ToolKindStartedApplication.name
    }

    // compiles, as a project compiles a tool, a tool that makes the console of Grails when it is made
    private File compileConsoleTool(boolean startsApplication) {
        File source = new File(dir, 'src/cli/groovy/example/ConsoleKeepingTool.groovy')
        source.parentFile.mkdirs()
        source.text = """package example

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolOption
import org.apache.grails.core.cli.ToolParameter

@ToolHints(readOnlyHint = true)
class ConsoleKeepingTool implements ApplicationTool {

    boolean startsApplication = ${startsApplication}

    String description = 'Has the console of Grails'

    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @ToolOption(names = ['--force'], description = 'Writes over')
    boolean force

    @ToolParameter(index = 0, label = 'NAME', description = 'What it is run for')
    String target

    boolean handle(ExecutionContext executionContext) {
        if (target == 'fail') {
            throw new IllegalStateException('this tool fails on purpose')
        }
        System.err.println("PRINTED TO SYSTEM.ERR BY \${target}")
        true
    }
}
"""
        File classes = new File(dir, 'build/classes/groovy/cli')
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: classes, disabledGlobalASTTransformations: [
                'org.grails.compiler.injection.GlobalGrailsClassInjectorTransformation',
                'org.grails.compiler.injection.GlobalImportTransformation',
        ] as Set))
        unit.addSource(source)
        unit.compile(Phases.ALL)
        classes
    }

    private Map run(String... arguments) {
        runWith(null, arguments)
    }

    // The JVM the runTool task starts, with more classes in front of the class path when they are given, and the
    // options the Grails Gradle plugin gives that task. It has a home of its own: the console of Grails, which a tool
    // can make, keeps its history there, and neither reads nor writes the one of whoever runs the specification.
    private Map runWith(File classes, String... arguments) {
        File home = new File(dir, 'home')
        home.mkdirs()
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', (classes == null ? '' : classes.path + File.pathSeparator) + System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${dir.path}".toString(), "-Duser.home=${home.path}".toString(),
                                GrailsApplicationContextCommandRunner.name]
        command.addAll(1, StartedJvmCoverage.jvmArguments())
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

@ToolHints(readOnlyHint = true)
class DeclaredArgumentsNoAppTool implements ApplicationTool {

    boolean startsApplication = false

    String description = 'Reports without the application'

    @ToolOption(names = ['--since', '-s'], description = 'The first day')
    String since

    @ToolOption(names = ['--force'], description = 'Writes over the report')
    boolean force

    @ToolParameter(index = 0, label = 'OUTPUT', description = 'The report')
    String output

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "DECLARED since=${since} force=${force} output=${output} remaining=${executionContext.commandLine.remainingArgs} " +
                "s=${executionContext.commandLine.optionValue('s')} context=${applicationContext != null}"
        executionContext.result = [output: output, force: force]
        true
    }
}

@ToolHints(readOnlyHint = true)
class DeclaredArgumentsAppTool implements ApplicationTool {

    String description = 'Reports with the application'

    @ToolOption(names = ['--since', '-s'], description = 'The first day')
    String since

    @ToolOption(names = ['--force'], description = 'Writes over the report')
    boolean force

    @ToolParameter(index = 0, label = 'OUTPUT', description = 'The report')
    String output

    @Override
    boolean handle(ExecutionContext executionContext) {
        println "DECLARED since=${since} force=${force} output=${output} remaining=${executionContext.commandLine.remainingArgs} " +
                "s=${executionContext.commandLine.optionValue('s')} context=${applicationContext != null}"
        executionContext.result = [output: output, force: force]
        true
    }
}
