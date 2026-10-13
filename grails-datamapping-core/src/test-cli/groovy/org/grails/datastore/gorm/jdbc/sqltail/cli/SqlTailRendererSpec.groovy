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

import org.grails.datastore.gorm.jdbc.sqltail.SqlTailRecord
import spock.lang.Specification

class SqlTailRendererSpec extends Specification {

    ByteArrayOutputStream bytes = new ByteArrayOutputStream()
    PrintStream out = new PrintStream(bytes, true, 'UTF-8')

    void "the page table ranks pages and shows the statement that repeats most in one request"() {
        given:
        SqlTailSummary summary = new SqlTailSummary()
        3.times { summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select a1_0.id from author a1_0 where a1_0.id=?')) }
        summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select b1_0.id from book b1_0 where b1_0.id=?'))
        summary.add(record('r2', 'book/index', 'GET /book/index', 'select b1_0.id from book b1_0'))
        SqlTailRenderer renderer = new SqlTailRenderer(out, false)

        when:
        renderer.renderTitle(new File('build/sql-tail/capture.log'), summary, false)
        renderer.renderPages(summary, 30)

        then:
        output() == '''\
sql-tail build/sql-tail/capture.log  5 statements in 2 requests
PAGE        REQUESTS  AVG/REQ    MAX   TOTAL  MOST REPEATED IN ONE REQUEST
book/show          1      4.0      4       4  3× select ... from author a1_0 where a1_0.id=?
book/index         1      1.0      1       1  1× select ... from book b1_0
'''
    }

    void "the statement table ranks statements"() {
        given:
        SqlTailSummary summary = new SqlTailSummary()
        2.times { summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select a1_0.id from author a1_0 where a1_0.id=?')) }
        summary.add(record('r2', 'book/index', 'GET /book/index', 'insert into audit (id) values (?)'))
        SqlTailRenderer renderer = new SqlTailRenderer(out, false)

        when:
        renderer.renderStatements(summary, 30, null)

        then:
        output() == '''\
  COUNT  PAGES  STATEMENT
      2      1  select ... from author a1_0 where a1_0.id=?
      1      1  insert into audit ...
'''
    }

    void "the statements of one page are headed by the page"() {
        given:
        SqlTailSummary summary = new SqlTailSummary()
        summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select 1'))
        summary.add(record('r2', 'book/index', 'GET /book/index', 'select 2'))
        SqlTailRenderer renderer = new SqlTailRenderer(out, false)

        when:
        renderer.renderStatements(summary, 30, 'book/show')

        then:
        output() == '''\
Statements of book/show
  COUNT  PAGES  STATEMENT
      1      1  select 1
'''
    }

    void "an empty summary says so"() {
        given:
        SqlTailRenderer renderer = new SqlTailRenderer(out, false)

        when:
        renderer.renderPages(new SqlTailSummary(), 30)
        renderer.renderStatements(new SqlTailSummary(), 30, 'book/show')

        then:
        output() == 'No statements yet.\nStatements of book/show\nNo statements for book/show yet.\n'
    }

    void "the stream prints a header per request and the count of the request that ended"() {
        given:
        SqlTailRenderer renderer = new SqlTailRenderer(out, false)
        List<SqlTailRecord> records = [
                record('r1', 'book/show', 'GET /book/show/1', 'select b1_0.id from book b1_0 where b1_0.id=?'),
                record('r1', 'book/show', 'GET /book/show/1', 'select a1_0.id from author a1_0 where a1_0.id=?'),
                record('r2', 'book/index', 'GET /book/index', 'select b1_0.id from book b1_0'),
                new SqlTailRecord(page: 'thread:scheduler-1', sql: 'update book set title=? where id=?'),
        ]

        when:
        SqlTailRecord previous = null
        for (SqlTailRecord record : records) {
            renderer.renderStream(record, previous)
            previous = record
        }

        then:
        output() == '''\
── r1  GET /book/show/1  book/show
   select ... from book b1_0 where b1_0.id=?
   select ... from author a1_0 where a1_0.id=?
   r1: 2 statements
── r2  GET /book/index  book/index
   select ... from book b1_0
   r2: 1 statements
── thread:scheduler-1
   update book ... where id=?
'''
    }

    void "long statements are cut to the width"() {
        given:
        SqlTailRenderer renderer = new SqlTailRenderer(out, false)
        renderer.width = 40
        SqlTailSummary summary = new SqlTailSummary()
        summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select b1_0.id from a_rather_long_table_name b1_0 where b1_0.id=?'))

        when:
        renderer.renderStatements(summary, 30, null)

        then:
        output().readLines()[1] == '      1      1  select ... from a_rathe…'
    }

    void "statements are coloured by kind when the console takes ANSI escapes"() {
        given:
        SqlTailRenderer renderer = new SqlTailRenderer(out, true)

        expect:
        renderer.colour(SqlStatementType.SELECT, 'x') == '\u001B[36mx\u001B[0m'
        renderer.colour(SqlStatementType.INSERT, 'x') == '\u001B[32mx\u001B[0m'
        renderer.colour(SqlStatementType.UPDATE, 'x') == '\u001B[33mx\u001B[0m'
        renderer.colour(SqlStatementType.DELETE, 'x') == '\u001B[31mx\u001B[0m'
        renderer.colour(SqlStatementType.OTHER, 'x') == 'x'
        new SqlTailRenderer(out, false).colour(SqlStatementType.SELECT, 'x') == 'x'
    }

    void "clearing the screen is an escape on an ANSI console and a separator otherwise"() {
        when:
        new SqlTailRenderer(out, true).clearScreen()
        new SqlTailRenderer(out, false).clearScreen()

        then:
        output() == '\u001B[H\u001B[2J\n' + ('=' * 80) + '\n'
    }

    private String output() {
        new String(bytes.toByteArray(), 'UTF-8')
    }

    private static SqlTailRecord record(String requestId, String page, String request, String sql) {
        new SqlTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: requestId, page: page, request: request, sql: sql)
    }
}
