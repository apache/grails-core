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

class SqlTailSummarySpec extends Specification {

    static final String BOOK = 'select b1_0.id,b1_0.title from book b1_0 where b1_0.id=?'
    static final String AUTHOR = 'select a1_0.id,a1_0.name from author a1_0 where a1_0.id=?'
    static final String BOOKS = 'select b1_0.id,b1_0.title from book b1_0'

    SqlTailSummary summary = new SqlTailSummary()

    void "pages are ranked by the statements they run per request and show the worst repeat"() {
        given:
        request('r1', 'book/show', 'GET /book/show/1', BOOK, AUTHOR, AUTHOR, AUTHOR)
        request('r2', 'book/show', 'GET /book/show/2', BOOK, AUTHOR, AUTHOR, AUTHOR, AUTHOR, AUTHOR)
        request('r3', 'book/index', 'GET /book/index', BOOKS)
        summary.add(new SqlTailRecord(page: 'thread:scheduler-1', sql: 'update book set title=? where id=?'))

        when:
        List<SqlTailSummary.PageSummary> pages = summary.pages()

        then:
        summary.recordCount == 12
        summary.requestCount == 4
        pages*.page == ['book/show', 'book/index', 'thread:scheduler-1']

        and: 'book/show ran 4 and 6 statements in its two requests'
        with(pages[0]) {
            requests == 2
            total == 10
            average == 5.0d
            max == 6
            worstStatement == 'select ... from author a1_0 where a1_0.id=?'
            worstCount == 5
        }

        and: 'a page with one request averages what it ran'
        with(pages[1]) {
            requests == 1
            total == 1
            average == 1.0d
            max == 1
            worstStatement == 'select ... from book b1_0'
            worstCount == 1
        }

        and: 'work outside a request is one request per thread'
        with(pages[2]) {
            requests == 1
            total == 1
            worstStatement == 'update book ... where id=?'
        }
    }

    void "statements are ranked by how often they ran and say on how many pages"() {
        given:
        request('r1', 'book/show', 'GET /book/show/1', BOOK, AUTHOR, AUTHOR)
        request('r2', 'author/show', 'GET /author/show/1', AUTHOR)
        request('r3', 'book/index', 'GET /book/index', BOOKS)

        when:
        List<SqlTailSummary.StatementSummary> statements = summary.statements()

        then:
        statements*.statement == ['select ... from author a1_0 where a1_0.id=?', 'select ... from book b1_0 where b1_0.id=?',
                                  'select ... from book b1_0']
        statements*.count == [3, 1, 1]
        statements*.pages == [2, 1, 1]
        statements*.type.every { it == SqlStatementType.SELECT }
    }

    void "the statements of one page can be asked for by page or by controller"() {
        given:
        request('r1', 'book/show', 'GET /book/show/1', BOOK, AUTHOR, AUTHOR)
        request('r2', 'author/show', 'GET /author/show/1', AUTHOR)
        request('r3', 'book/index', 'GET /book/index', BOOKS)
        request('r4', 'admin:book/index', 'GET /admin/book', BOOKS)

        expect:
        summary.statements(0, 'book/show')*.statement == ['select ... from author a1_0 where a1_0.id=?', 'select ... from book b1_0 where b1_0.id=?']
        summary.statements(0, 'book/show')*.count == [2, 1]
        summary.statements(0, 'book')*.statement == ['select ... from author a1_0 where a1_0.id=?', 'select ... from book b1_0',
                                                     'select ... from book b1_0 where b1_0.id=?']
        summary.statements(0, 'book')*.count == [2, 2, 1]
        summary.statements(0, 'book')*.pages == [1, 2, 1]
        summary.statements(0, 'author')*.count == [1]
        summary.statements(0, 'nothing').isEmpty()
    }

    void "top limits what is returned"() {
        given:
        request('r1', 'book/show', 'GET /book/show/1', BOOK, AUTHOR)
        request('r2', 'book/index', 'GET /book/index', BOOKS)

        expect:
        summary.pages(1)*.page == ['book/show']
        summary.pages(5).size() == 2
        summary.statements(1)*.count == [1]
        summary.statements(0).size() == 3
    }

    void "a request without a controller is counted under its path, and one without a path under no page"() {
        given:
        summary.add(new SqlTailRecord(requestId: 'r1', request: 'GET /health', sql: 'select 1'))
        summary.add(new SqlTailRecord(requestId: 'r2', sql: 'select 1'))

        expect:
        summary.pages()*.page == ['GET /health', SqlTailSummary.NO_PAGE]
    }

    void "a page filter matches a page, a controller, or a namespaced controller"() {
        expect:
        SqlTailSummary.matchesPage('book/show', 'book/show')
        SqlTailSummary.matchesPage('book/show', 'book')
        SqlTailSummary.matchesPage('admin:book/show', 'book')
        SqlTailSummary.matchesPage('admin:book/show', 'admin:book/show')
        SqlTailSummary.matchesPage('thread:main', 'thread:main')
        !SqlTailSummary.matchesPage('book/show', 'book/index')
        !SqlTailSummary.matchesPage('bookmark/show', 'book')
        !SqlTailSummary.matchesPage('admin:book/show', 'admin')
    }

    private void request(String id, String page, String request, String... statements) {
        for (String sql : statements) {
            summary.add(new SqlTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: id, page: page, request: request, sql: sql))
        }
    }
}
