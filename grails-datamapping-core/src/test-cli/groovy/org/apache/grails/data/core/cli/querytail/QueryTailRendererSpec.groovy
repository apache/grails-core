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
package org.apache.grails.data.core.cli.querytail

import org.apache.grails.data.core.querytail.QueryTailRecord
import spock.lang.Specification

class QueryTailRendererSpec extends Specification {

    ByteArrayOutputStream bytes = new ByteArrayOutputStream()
    PrintStream out = new PrintStream(bytes, true, 'UTF-8')

    void "the page table ranks pages and shows the statement that repeats most in one request"() {
        given:
        QueryTailSummary summary = new QueryTailSummary()
        3.times { summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select a1_0.id from author a1_0 where a1_0.id=?')) }
        summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select b1_0.id from book b1_0 where b1_0.id=?'))
        summary.add(record('r2', 'book/index', 'GET /book/index', 'select b1_0.id from book b1_0'))
        QueryTailRenderer renderer = new QueryTailRenderer(out, false)
        File file = new File('build/query-tail/capture.log')

        when:
        renderer.renderTitle(file, summary, false)
        renderer.renderPages(summary, 30)

        then:
        output() == "query-tail ${file}  5 statements in 2 requests\n" + '''\
PAGE        REQUESTS  AVG/REQ    MAX   TOTAL  MOST REPEATED IN ONE REQUEST
book/show          1      4.0      4       4  3× select ... from author a1_0 where a1_0.id=?
book/index         1      1.0      1       1  1× select ... from book b1_0
'''
    }

    void "the average is written with a point whatever the locale"() {
        given:
        Locale before = Locale.default
        Locale.default = Locale.GERMANY
        QueryTailSummary summary = new QueryTailSummary()
        3.times { summary.add(record('r1', 'book/show', 'GET /book/show/?', 'select 1')) }
        summary.add(record('r2', 'book/show', 'GET /book/show/?', 'select 1'))

        when:
        new QueryTailRenderer(out, false).renderPages(summary, 30)

        then:
        output().contains('      2.0      3       4')

        cleanup:
        Locale.default = before
    }

    void "the statement table ranks statements"() {
        given:
        QueryTailSummary summary = new QueryTailSummary()
        2.times { summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select a1_0.id from author a1_0 where a1_0.id=?')) }
        summary.add(record('r2', 'book/index', 'GET /book/index', 'insert into audit (id) values (?)'))
        QueryTailRenderer renderer = new QueryTailRenderer(out, false)

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
        QueryTailSummary summary = new QueryTailSummary()
        summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select 1'))
        summary.add(record('r2', 'book/index', 'GET /book/index', 'select 2'))
        QueryTailRenderer renderer = new QueryTailRenderer(out, false)

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
        QueryTailRenderer renderer = new QueryTailRenderer(out, false)

        when:
        renderer.renderPages(new QueryTailSummary(), 30)
        renderer.renderStatements(new QueryTailSummary(), 30, 'book/show')

        then:
        output() == 'No statements yet.\nStatements of book/show\nNo statements for book/show yet.\n'
    }

    void "the stream prints a header per request and the count of the request that ended"() {
        given:
        QueryTailRenderer renderer = new QueryTailRenderer(out, false)
        List<QueryTailRecord> records = [
                record('r1', 'book/show', 'GET /book/show/1', 'select b1_0.id from book b1_0 where b1_0.id=?'),
                record('r1', 'book/show', 'GET /book/show/1', 'select a1_0.id from author a1_0 where a1_0.id=?'),
                record('r2', 'book/index', 'GET /book/index', 'select b1_0.id from book b1_0'),
                new QueryTailRecord(page: 'thread:scheduler-1', sql: 'update book set title=? where id=?'),
        ]

        when:
        QueryTailRecord previous = null
        for (QueryTailRecord record : records) {
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

    void "interleaved requests keep their counts, and a request that comes back is headed as continued"() {
        given:
        QueryTailRenderer renderer = new QueryTailRenderer(out, false)
        List<QueryTailRecord> records = [
                record('r1', 'book/show', 'GET /book/show/1', 'select b1_0.id from book b1_0 where b1_0.id=?'),
                record('r2', 'book/index', 'GET /book/index', 'select b1_0.id from book b1_0'),
                record('r1', 'book/show', 'GET /book/show/1', 'select a1_0.id from author a1_0 where a1_0.id=?'),
                record('r2', 'book/index', 'GET /book/index', 'select b1_0.id from book b1_0'),
        ]

        when:
        QueryTailRecord previous = null
        for (QueryTailRecord record : records) {
            renderer.renderStream(record, previous)
            previous = record
        }

        then:
        output() == '''\
── r1  GET /book/show/1  book/show
   select ... from book b1_0 where b1_0.id=?
   r1: 1 statements
── r2  GET /book/index  book/index
   select ... from book b1_0
   r2: 1 statements
── r1  GET /book/show/1  book/show  (continued)
   select ... from author a1_0 where a1_0.id=?
   r1: 2 statements
── r2  GET /book/index  book/index  (continued)
   select ... from book b1_0
'''
    }

    void "a stream that is reset forgets the requests it counted"() {
        given:
        QueryTailRenderer renderer = new QueryTailRenderer(out, false)
        QueryTailRecord first = record('r1', 'book/show', 'GET /book/show/1', 'select b1_0.id from book b1_0')
        renderer.renderStream(first, null)
        renderer.renderStream(record('r2', 'book/index', 'GET /book/index', 'select b1_0.id from book b1_0'), first)

        when: 'the application restarts and numbers its requests from r1 again'
        renderer.reset()
        bytes.reset()
        renderer.renderStream(record('r1', 'author/show', 'GET /author/show/1', 'select a1_0.id from author a1_0'), null)

        then:
        output() == '── r1  GET /author/show/1  author/show\n   select ... from author a1_0\n'
    }

    void "the stream keeps counts for the requests heard from most recently"() {
        given:
        QueryTailRenderer renderer = new QueryTailRenderer(out, false)
        QueryTailRecord previous = null
        for (int i = 0; i <= QueryTailRenderer.STREAMED_REQUEST_LIMIT; i++) {
            QueryTailRecord record = record("r${i}".toString(), 'book/show', 'GET /book/show/1', 'select 1')
            renderer.renderStream(record, previous)
            previous = record
        }

        when: 'the oldest request comes back after more requests than the limit'
        bytes.reset()
        renderer.renderStream(record('r0', 'book/show', 'GET /book/show/1', 'select 1'), previous)

        then: 'it is no longer known, so it is headed as new'
        output().readLines()[1] == '── r0  GET /book/show/1  book/show'
    }

    void "long statements are cut to the width"() {
        given:
        QueryTailRenderer renderer = new QueryTailRenderer(out, false)
        renderer.width = 40
        QueryTailSummary summary = new QueryTailSummary()
        summary.add(record('r1', 'book/show', 'GET /book/show/1', 'select b1_0.id from a_rather_long_table_name b1_0 where b1_0.id=?'))

        when:
        renderer.renderStatements(summary, 30, null)

        then:
        output().readLines()[1] == '      1      1  select ... from a_rathe…'
    }

    void "statements are coloured by kind when the console takes ANSI escapes"() {
        given:
        QueryTailRenderer renderer = new QueryTailRenderer(out, true)

        expect:
        renderer.colour(StatementType.SELECT, 'x') == '\u001B[36mx\u001B[0m'
        renderer.colour(StatementType.INSERT, 'x') == '\u001B[32mx\u001B[0m'
        renderer.colour(StatementType.UPDATE, 'x') == '\u001B[33mx\u001B[0m'
        renderer.colour(StatementType.DELETE, 'x') == '\u001B[31mx\u001B[0m'
        renderer.colour(StatementType.OTHER, 'x') == 'x'
        new QueryTailRenderer(out, false).colour(StatementType.SELECT, 'x') == 'x'
    }

    void "clearing the screen is an escape on an ANSI console and a separator otherwise"() {
        when:
        new QueryTailRenderer(out, true).clearScreen()
        new QueryTailRenderer(out, false).clearScreen()

        then:
        output() == '\u001B[H\u001B[2J\n' + ('=' * 80) + '\n'
    }

    /** The output with the platform's line separator, which println writes, read as a newline. */
    private String output() {
        new String(bytes.toByteArray(), 'UTF-8').replace(System.lineSeparator(), '\n')
    }

    private static QueryTailRecord record(String requestId, String page, String request, String sql) {
        new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: requestId, page: page, request: request, sql: sql)
    }
}
