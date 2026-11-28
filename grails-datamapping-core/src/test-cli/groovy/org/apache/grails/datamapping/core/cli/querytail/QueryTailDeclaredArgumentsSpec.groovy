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
import spock.lang.Unroll

import org.apache.grails.datamapping.core.querytail.QueryTailRecord

/**
 * The query-tail command declares its options, and is held to them as the {@code runTool} task of a build runs
 * it: by the class that runs a tool, in a JVM of its own. An option it does not declare, a value given to a
 * switch and an argument it takes no place for are refused with exit code 2 before the command runs.
 */
class QueryTailDeclaredArgumentsSpec extends Specification {

    private static final String RUNNER = 'grails.ui.command.GrailsApplicationContextCommandRunner'

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File tempDir

    File capture

    def setup() {
        capture = new File(tempDir, 'capture.log')
        capture.text = [record('r1', 'book/show', 'select 1'), record('r1', 'book/show', 'select 1'),
                        record('r2', 'author/list', 'select 2')].join('\n') + '\n'
    }

    void "the declared options take their values from the next argument as well as after an equals sign"() {
        when:
        Map read = run('query-tail', '--once', '--statements', '--top', '1', '--file', capture.path)

        then:
        read.exit == 0
        read.out.contains('select 1')
        !read.out.contains('select 2')
    }

    @Unroll
    void "#arguments is refused with exit code 2 and says #what"() {
        when:
        Map refused = run((['query-tail'] + arguments + ["--file=${capture.path}".toString()]) as String[])

        then:
        refused.exit == 2
        refused.err.readLines().last() == "| Error query-tail: ${what}. grails help query-tail shows its options".toString()
        !refused.out.contains('book/show')

        where:
        arguments                 || what
        ['--onse']                || '--onse is not one of its options'
        ['--once=true']           || '--once takes no value'
        ['--once', 'book/show']   || 'it takes no parameters, and was given 1'
        ['--once', '--page']      || '--page needs a value'
        ['--once', '--top=ten']   || '--top takes a whole number'
        ['--once', '--top', '1.5'] || '--top takes a whole number'
    }

    /**
     * Runs the command for an application in the directory of the spec, as the task of its build does: the class
     * that runs a command, the name of the command and its arguments, and the class of the application last.
     */
    private Map run(String... arguments) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${tempDir.path}".toString()]
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
        Process process = new ProcessBuilder(command).directory(tempDir).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        assert ended
        [exit: process.exitValue(), out: out.text, err: err.text]
    }

    private static String record(String requestId, String page, String sql) {
        new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: requestId, page: page,
                request: "GET /${page}".toString(), sql: sql).toLine()
    }
}
