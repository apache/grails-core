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

import spock.lang.Specification
import spock.lang.TempDir

import org.apache.grails.core.cli.script.ScriptCommandRunner
import org.apache.grails.datamapping.core.querytail.QueryTailRecord

/**
 * Runs the query-tail command script the way the build of a project does: found on the class path, as a
 * plugin has it, and compiled and run by the script host.
 */
class QueryTailCommandScriptSpec extends Specification {

    private static final String RETURNED_FALSE = 'The script query-tail returned false, which is how a script says that it did not succeed.'

    @TempDir
    File tempDir

    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void "the script reads a capture once and reports whether there was one"() {
        given:
        File capture = capture(new File(tempDir, 'capture.log'))

        when:
        int read = run(tempDir, 'query-tail', '--once', "--file=${capture.path}".toString())

        then: 'the summary of what was captured, by the page that ran it'
        read == 0
        err.toString() == ''
        out.toString().contains('book/show')

        when: 'no capture'
        int missing = run(tempDir, 'query-tail', '--once', "--file=${new File(tempDir, 'missing.log').path}".toString())

        then:
        missing == 1
        err.toString().readLines().last() == RETURNED_FALSE
    }

    void "the script reads a relative capture file against the application directory, as the application writes it"() {
        given: 'a capture in the application directory, and another directory that has none'
        File appDir = new File(tempDir, 'app')
        capture(new File(appDir, 'out/capture.log'))
        File other = new File(tempDir, 'other')
        other.mkdirs()

        when:
        int read = run(appDir, 'query-tail', '--once', '--file=out/capture.log')

        then:
        read == 0
        err.toString() == ''
        out.toString().contains('book/show')

        when:
        int elsewhere = run(other, 'query-tail', '--once', '--file=out/capture.log')

        then:
        elsewhere == 1
        !out.toString().contains('book/show')
    }

    void "the script reports a number of rows that is not one rather than failing"() {
        given:
        File capture = capture(new File(tempDir, 'capture.log'))

        when:
        int refused = run(tempDir, 'query-tail', '--once', '--top=ten', "--file=${capture.path}".toString())

        then: 'the script says so and returns false: nothing it threw reached the host'
        refused == 1
        err.toString().contains('ten')
        err.toString().readLines().last() == RETURNED_FALSE
        !out.toString().contains('book/show')
    }

    /**
     * Runs the command for the application in a directory, as the task of its build does.
     */
    private int run(File baseDir, String... args) {
        out.reset()
        err.reset()
        new ScriptCommandRunner(baseDir, getClass().classLoader, new PrintStream(out, true), new PrintStream(err, true))
                .start((['--'] + (args as List)) as String[])
    }

    private static File capture(File file) {
        file.parentFile.mkdirs()
        file.text = new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: 'r1', page: 'book/show',
                request: 'GET /book/show/1', sql: 'select 1').toLine() + '\n'
        file
    }
}
