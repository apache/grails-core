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
package org.grails.datastore.gorm.jdbc.sqltail.cli

import java.util.concurrent.TimeUnit

import org.grails.datastore.gorm.jdbc.sqltail.SqlTailRecord
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.concurrent.PollingConditions

class SqlTailRunnerSpec extends Specification {

    @TempDir
    File tempDir

    ByteArrayOutputStream bytes = new ByteArrayOutputStream()
    PrintStream out = new PrintStream(bytes, true, 'UTF-8')
    File file

    void setup() {
        file = new File(tempDir, 'capture.log')
    }

    void "once prints the summary of the whole capture and exits"() {
        given:
        file.text = line('r1', 'book/show', 'select b1_0.id from book b1_0 where b1_0.id=?') + line('r2', 'book/index', 'select b1_0.id from book b1_0')
        SqlTailRunner runner = new SqlTailRunner(new SqlTailOptions(file: file, once: true), out, false)

        expect:
        runner.run() == 0
        output().startsWith("sql-tail ${file}  2 statements in 2 requests\n")
        output().contains('book/show')
        output().contains('book/index')
        !output().contains('refreshes')
    }

    void "once with a missing capture explains how to start capturing and fails"() {
        given:
        SqlTailRunner runner = new SqlTailRunner(new SqlTailOptions(file: file, once: true), out, false)

        expect:
        runner.run() == 1
        output() == "No sql-tail capture at ${file}.\n${SqlTailRunner.START_HINT}\n"
    }

    void "once with a page shows the statements of that page"() {
        given:
        file.text = line('r1', 'book/show', 'select 1') + line('r2', 'book/index', 'select 2')
        SqlTailRunner runner = new SqlTailRunner(
                new SqlTailOptions(file: file, once: true, mode: SqlTailMode.STATEMENTS, page: 'book/index'), out, false)

        expect:
        runner.run() == 0
        output().contains('Statements of book/index')
        output().contains('select 2')
        !output().contains('select 1')
    }

    void "a live run waits for the capture, follows it, and stops when told to"() {
        given:
        SqlTailRunner runner = new SqlTailRunner(new SqlTailOptions(file: file), out, false)
        runner.pollMillis = 20
        runner.refreshMillis = 0
        Thread thread = Thread.startDaemon('sql-tail-runner') { runner.run() }
        PollingConditions conditions = new PollingConditions(timeout: 10)

        expect: 'it waits for the application to start capturing'
        conditions.eventually {
            assert output().startsWith("Waiting for ${file}. ${SqlTailRunner.START_HINT}\n")
        }

        when: 'the application writes statements'
        file.text = line('r1', 'book/show', 'select b1_0.id from book b1_0 where b1_0.id=?')

        then: 'the summary appears and keeps refreshing'
        conditions.eventually {
            assert output().contains('1 statements in 1 requests  (refreshes as statements run; Ctrl-C to stop)')
            assert output().contains('book/show')
        }

        when:
        file << line('r2', 'book/index', 'select b1_0.id from book b1_0')

        then:
        conditions.eventually {
            assert output().contains('2 statements in 2 requests')
            assert output().contains('book/index')
        }

        when:
        runner.stop()
        thread.join(TimeUnit.SECONDS.toMillis(10))

        then:
        !thread.alive
        runner.stopped
    }

    void "a live run that is stopped while waiting ends without failing"() {
        given:
        SqlTailRunner runner = new SqlTailRunner(new SqlTailOptions(file: file), out, false)
        runner.pollMillis = 20
        int[] result = new int[1]
        Thread thread = Thread.startDaemon('sql-tail-runner') { result[0] = runner.run() }

        when:
        new PollingConditions(timeout: 10).eventually { assert output().contains('Waiting for') }
        runner.stop()
        thread.join(TimeUnit.SECONDS.toMillis(10))

        then:
        !thread.alive
        result[0] == 0
    }

    void "the stream prints statements as they are appended"() {
        given:
        file.text = line('r0', 'book/index', 'select 0')
        SqlTailRunner runner = new SqlTailRunner(new SqlTailOptions(file: file, mode: SqlTailMode.STREAM), out, false)
        runner.pollMillis = 20
        Thread thread = Thread.startDaemon('sql-tail-runner') { runner.run() }
        PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
        Thread.sleep(100)
        file << line('r1', 'book/show', 'select b1_0.id from book b1_0 where b1_0.id=?')

        then: 'what ran before the command started is not shown'
        conditions.eventually {
            assert output() == '── r1  GET /book/show/1  book/show\n   select ... from book b1_0 where b1_0.id=?\n'
        }

        cleanup:
        runner.stop()
        thread.join(TimeUnit.SECONDS.toMillis(10))
    }

    private String output() {
        new String(bytes.toByteArray(), 'UTF-8')
    }

    private static String line(String requestId, String page, String sql) {
        new SqlTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: requestId, page: page,
                request: "GET /${page}/1".toString(), sql: sql).toLine() + '\n'
    }
}
