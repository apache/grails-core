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
package org.apache.grails.datamapping.core.cli.querytail

import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.TempDir

import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * Runs the query-tail command the way the build of a project does: found by its name among the
 * commands on the class path, as a plugin has it, and run by the class the {@code runCommand} task
 * starts, in a JVM of its own, for an application that is never started.
 */
class QueryTailToolSpec extends Specification {

    private static final String RUNNER = 'grails.ui.command.GrailsApplicationContextCommandRunner'

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File tempDir

    void "the command reads a capture once and reports whether there was one, and the application is not started"() {
        given:
        File capture = capture(new File(tempDir, 'capture.log'))

        when:
        Map read = run(tempDir, 'query-tail', '--once', "--file=${capture.path}".toString())

        then: 'the summary of what was captured, by the page that ran it'
        read.exit == 0
        read.out.contains('book/show')
        !read.err.contains('Error')

        and: 'nothing of the application ran, not even the initializer of its class'
        !read.out.contains('APPLICATION')
        !read.err.contains('APPLICATION')

        when: 'no capture'
        File none = new File(tempDir, 'missing.log')
        Map missing = run(tempDir, 'query-tail', '--once', "--file=${none.path}".toString())

        then:
        missing.exit == 1
        missing.out.contains("No query-tail capture at ${none.path}.")
        missing.out.contains(QueryTailRunner.START_HINT)
    }

    void "the command reads a relative capture file against the application directory, as the application writes it"() {
        given: 'a capture in the application directory, and another directory that has none'
        File appDir = new File(tempDir, 'app')
        capture(new File(appDir, 'out/capture.log'))
        File other = new File(tempDir, 'other')
        other.mkdirs()

        when:
        Map read = run(appDir, 'query-tail', '--once', '--file=out/capture.log')

        then:
        read.exit == 0
        read.out.contains('book/show')

        when:
        Map elsewhere = run(other, 'query-tail', '--once', '--file=out/capture.log')

        then:
        elsewhere.exit == 1
        !elsewhere.out.contains('book/show')
    }

    void "without a file the command reads the capture the application writes: under the build directory, or where its configuration says"() {
        given: 'an application that captures to the default file of a build directory the build has moved'
        File appDir = new File(tempDir, 'app')
        capture(new File(appDir, 'moved/query-tail/capture.log'))

        when:
        Map read = run(appDir, ['-Dgrails.project.target.dir=moved'], 'query-tail', '--once')

        then:
        read.exit == 0
        read.out.contains('book/show')

        when: 'the build directory is the usual one, where there is no capture'
        Map usual = run(appDir, 'query-tail', '--once')

        then:
        usual.exit == 1
        usual.out.contains("No query-tail capture at ${new File(appDir, 'build/query-tail/capture.log').path}.")

        when: 'the configuration of the application names the file, which is read without starting the application'
        File conf = new File(appDir, 'grails-app/conf')
        conf.mkdirs()
        new File(conf, 'application.yml').text = 'grails:\n    gorm:\n        queryTail:\n            file: captured/statements.log\n'
        capture(new File(appDir, 'captured/statements.log'), 'author/list')
        Map configured = run(appDir, 'query-tail', '--once')

        then:
        configured.exit == 0
        configured.out.contains('author/list')
        !configured.out.contains('book/show')

        when: 'the build was given the file, which it hands to the application and to the command alike'
        capture(new File(appDir, 'given/statements.log'), 'publisher/index')
        Map given = run(appDir, ['-Dgrails.gorm.queryTail.file=given/statements.log'], 'query-tail', '--once')

        then:
        given.exit == 0
        given.out.contains('publisher/index')
        !given.out.contains('author/list')
    }

    void "the command ranks the statements, shows one page and takes the number of rows"() {
        given:
        File file = new File(tempDir, 'capture.log')
        file.text = [record('r1', 'book/show', 'select 1'), record('r1', 'book/show', 'select 1'),
                     record('r2', 'author/list', 'select 2')].join('\n') + '\n'

        when:
        Map statements = run(tempDir, 'query-tail', '--once', '--statements', "--file=${file.path}".toString())

        then:
        statements.exit == 0
        statements.out.contains('select 1')
        statements.out.contains('select 2')

        when:
        Map page = run(tempDir, 'query-tail', '--once', '--page=author/list', "--file=${file.path}".toString())

        then:
        page.exit == 0
        page.out.contains('select 2')
        !page.out.contains('select 1')

        when:
        Map top = run(tempDir, 'query-tail', '--once', '--statements', '--top=1', "--file=${file.path}".toString())

        then:
        top.exit == 0
        top.out.contains('select 1')
        !top.out.contains('select 2')
    }

    void "the command reports a number of rows that is not one rather than failing"() {
        given:
        File capture = capture(new File(tempDir, 'capture.log'))

        when:
        Map refused = run(tempDir, 'query-tail', '--once', '--top=ten', "--file=${capture.path}".toString())

        then: 'the command says so and returns false: nothing it threw reached what runs it'
        refused.exit == 1
        refused.err.readLines().last() == "| Error --top takes a number of rows, such as --top=10, not 'ten'"
        !refused.err.contains('Command execution error')
        !refused.out.contains('book/show')
    }

    void "a JVM that runs the command again and again keeps no shutdown hook of a run that has ended"() {
        given:
        File capture = capture(new File(tempDir, 'capture.log'))
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
                                '-cp', System.getProperty('java.class.path'), "-Dbase.dir=${tempDir.path}".toString()]
        String coverage = System.getProperty('grails.test.fork.jacoco')
        if (coverage) {
            command.add(coverage.replace('@fork@', UUID.randomUUID().toString()))
        }
        command.addAll([QueryTailHooksMain.name, capture.path, '3'])
        File out = File.createTempFile('out', '.txt', tempDir)
        File err = File.createTempFile('err', '.txt', tempDir)

        when:
        Process process = new ProcessBuilder(command).directory(tempDir).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }

        then: 'each run printed its summary, and took its hook away when it ended'
        ended
        process.exitValue() == 0
        out.text.count('book/show') == 3
        out.text.readLines().last() == 'hooks left: 0'
    }

    void "the command says what its options are by declaring them, which grails help shows, and --help is refused as an option of its own"() {
        when: 'what the compiler wrote for the command, which grails help query-tail shows'
        InputStream resource = QueryTailTool.classLoader.getResourceAsStream(ToolDescriptor.location(QueryTailTool.name))
        ToolDescriptor.Described described = ToolDescriptor.read(QueryTailTool.name, resource.bytes)

        then: 'every option, each with what it does'
        described.declarations.options.collect { Map option -> option.names } == QueryTailTool.FLAGS.collect { String flag -> ["--${flag}".toString()] }
        described.declarations.options.every { Map option -> option.description }

        when: '--help, which the command line keeps for itself and a tool cannot declare'
        Map help = run(tempDir, 'query-tail', '--help')

        then: 'the command is refused before it runs, and is told where its options are shown'
        help.exit == 2
        help.err.readLines().last() == '| Error query-tail: --help is not one of its options. grails help query-tail shows its options'
        !help.out.contains('Usage: grails query-tail')
    }

    void "the command is listed by a build from its descriptor, as one the build runs without the application"() {
        when: 'what the compiler wrote for the command, which is what the jar of the command line tier has'
        InputStream resource = QueryTailTool.classLoader.getResourceAsStream(ToolDescriptor.location(QueryTailTool.name))
        ToolDescriptor.Described described = ToolDescriptor.read(QueryTailTool.name, resource.bytes)
        QueryTailTool command = new QueryTailTool()

        then:
        described.name == 'query-tail'
        described.name == command.name
        described.description == command.description
        described.target == ToolDescriptor.TARGET_APPLICATION
        !described.startsApplication
    }

    /**
     * Runs the command for the application in a directory, as the task of its build does: the class that
     * runs a command, the name of the command and its arguments, and the class of the application last.
     */
    private Map run(File baseDir, List<String> jvmArguments = [], String... arguments) {
        baseDir.mkdirs()
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${baseDir.path}".toString()]
        command.addAll(jvmArguments)
        // the JVM that is started is counted as tested: the build says how (the grails-jacoco convention plugin)
        String coverage = System.getProperty('grails.test.fork.jacoco')
        if (coverage) {
            command.add(coverage.replace('@fork@', UUID.randomUUID().toString()))
        }
        command.add(RUNNER)
        command.addAll(arguments.toList())
        command.add(QueryTailSpecApplication.name)
        File out = File.createTempFile('out', '.txt', tempDir)
        File err = File.createTempFile('err', '.txt', tempDir)
        Process process = new ProcessBuilder(command).directory(baseDir).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        assert ended
        [exit: process.exitValue(), out: out.text, err: err.text]
    }

    private static File capture(File file, String page = 'book/show') {
        file.parentFile.mkdirs()
        file.text = record('r1', page, 'select 1') + '\n'
        file
    }

    private static String record(String requestId, String page, String sql) {
        new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: requestId, page: page,
                request: "GET /${page}".toString(), sql: sql).toLine()
    }
}

/**
 * Stands for the class of the application, which the command is run for and which is never started.
 */
class QueryTailSpecApplication {

    static {
        println 'APPLICATION INITIALIZED'
    }

    QueryTailSpecApplication() {
        println 'APPLICATION STARTED'
    }
}
