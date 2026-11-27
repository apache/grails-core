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

import java.util.function.Supplier

import org.apache.grails.datamapping.core.querytail.QueryTailCapture
import org.apache.grails.datamapping.core.querytail.QueryTailOtherJvm
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datamapping.core.querytail.QueryTailRedactor
import org.apache.grails.datamapping.core.querytail.QueryTailStubRequestReader
import org.apache.grails.datamapping.core.querytail.QueryTailSyntax
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification
import spock.lang.TempDir

class QueryTailSummarySpec extends Specification {

    static final String BOOK = 'select b1_0.id,b1_0.title from book b1_0 where b1_0.id=?'
    static final String AUTHOR = 'select a1_0.id,a1_0.name from author a1_0 where a1_0.id=?'
    static final String BOOKS = 'select b1_0.id,b1_0.title from book b1_0'

    QueryTailSummary summary = new QueryTailSummary()

    @TempDir
    File tempDir

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void "pages are ranked by the statements they run per request and show the worst repeat"() {
        given:
        request('r1', 'book/show', 'GET /book/show/1', BOOK, AUTHOR, AUTHOR, AUTHOR)
        request('r2', 'book/show', 'GET /book/show/2', BOOK, AUTHOR, AUTHOR, AUTHOR, AUTHOR, AUTHOR)
        request('r3', 'book/index', 'GET /book/index', BOOKS)
        summary.add(new QueryTailRecord(page: 'thread:scheduler-1', sql: 'update book set title=? where id=?'))

        when:
        List<QueryTailSummary.PageSummary> pages = summary.pages()

        then: 'the thread is no request'
        summary.recordCount == 12
        summary.requestCount == 3
        pages*.page == ['book/show', 'book/index', 'thread:scheduler-?']

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

        and: 'work outside a request is a page of its thread, which has no requests'
        with(pages[2]) {
            thread
            requests == 0
            average == 0.0d
            max == 0
            total == 1
            worstStatement == 'update book ... where id=?'
        }
    }

    void "a thread that ran many statements outside a request ranks after every page of requests"() {
        given: 'an application whose startup inserted far more than any request runs'
        800.times { summary.add(new QueryTailRecord(page: 'thread:main', sql: 'insert into book (title,id) values (?,?)')) }
        5.times { summary.add(new QueryTailRecord(page: 'thread:Test worker', sql: BOOKS)) }
        request('r1', 'book/show', 'GET /book/show/1', BOOK, AUTHOR, AUTHOR)
        request('r2', 'book/index', 'GET /book/index', BOOKS)

        expect: 'the pages of requests first, then the threads, the busiest first'
        summary.pages()*.page == ['book/show', 'book/index', 'thread:main', 'thread:Test worker']
        summary.pages()*.thread == [false, false, true, true]
        summary.pages()*.total == [3, 1, 800, 5]
        summary.requestCount == 2
    }

    void "the threads of one pool, which an executor numbers as it starts them, are one page"() {
        given:
        50.times { int i -> summary.add(new QueryTailRecord(page: "thread:SimpleAsyncTaskExecutor-${i + 1}".toString(), sql: BOOKS)) }
        3.times { int i -> summary.add(new QueryTailRecord(page: "thread:pool-${i}-thread-${i * 7}".toString(), sql: BOOK)) }
        summary.add(new QueryTailRecord(page: 'thread:main', sql: BOOK))

        expect:
        summary.pages()*.page == ['thread:SimpleAsyncTaskExecutor-?', 'thread:pool-?-thread-?', 'thread:main']
        summary.pages()*.total == [50, 3, 1]
        summary.statements(0, 'thread:pool-7-thread-2')*.statement == ['select ... from book b1_0 where b1_0.id=?']
        summary.statements(0, 'thread:pool-?-thread-?')*.count == [3]
        summary.requestCount == 0
    }

    void "past the threads a summary keeps apart, the statements of any other thread are counted together"() {
        given: 'as many threads as a summary keeps apart, whose names differ in more than their numbers'
        QueryTailSummary.THREAD_PAGES.times { int i -> summary.add(new QueryTailRecord(page: threadNamed(i), sql: BOOKS)) }

        when: 'another thread runs a statement, and a thread that has a page runs one again'
        summary.add(new QueryTailRecord(page: 'thread:one too many', sql: BOOKS))
        summary.add(new QueryTailRecord(page: 'thread:another one', sql: BOOK))
        summary.add(new QueryTailRecord(page: threadNamed(0), sql: BOOK))
        List<QueryTailSummary.PageSummary> pages = summary.pages()

        then:
        pages.size() == QueryTailSummary.THREAD_PAGES + 1
        pages.find { it.page == QueryTailSummary.OTHER_THREADS }.total == 2
        pages.find { it.page == threadNamed(0) }.total == 2
        summary.recordCount == QueryTailSummary.THREAD_PAGES + 3
    }

    void "a count and the list of the rows it counts are two statements of a page, not a repeat"() {
        given: 'a paginated list, which counts its rows and lists a page of them'
        request('r1', 'book/index', 'GET /book/index', 'select count(*) from book b1_0 where b1_0.author_id=?',
                'select b1_0.id,b1_0.title from book b1_0 where b1_0.author_id=? limit ?')

        expect:
        summary.pages()[0].worstCount == 1
        summary.statements()*.statement as Set == ['select count(*) from book b1_0 where b1_0.author_id=?',
                                                   'select ... from book b1_0 where b1_0.author_id=? limit ?'] as Set
    }

    void "statements are ranked by how often they ran and say on how many pages"() {
        given:
        request('r1', 'book/show', 'GET /book/show/1', BOOK, AUTHOR, AUTHOR)
        request('r2', 'author/show', 'GET /author/show/1', AUTHOR)
        request('r3', 'book/index', 'GET /book/index', BOOKS)

        when:
        List<QueryTailSummary.StatementSummary> statements = summary.statements()

        then:
        statements*.statement == ['select ... from author a1_0 where a1_0.id=?', 'select ... from book b1_0 where b1_0.id=?',
                                  'select ... from book b1_0']
        statements*.count == [3, 1, 1]
        statements*.pages == [2, 1, 1]
        statements*.type.every { it == StatementType.SELECT }
    }

    void "a statement recorded again is counted as the statement it shortened to, and a command recorded as the same text as it was recorded"() {
        given: 'a select, recorded twice and with another column list, and a document recorded as the same text'
        String select = 'select b1_0.id,b1_0.title from book b1_0'
        summary.add(new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: 'r1', page: 'book/index', sql: select))
        summary.add(new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: 'r1', page: 'book/index', sql: select))
        summary.add(new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: 'r1', page: 'book/index',
                sql: 'select b1_0.id from book b1_0'))
        summary.add(new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: 'r1', page: 'book/index', sql: select,
                document: true))

        expect:
        counts(summary.statements()) == [['select ... from book b1_0', 3, 1], [select, 1, 1]]
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

    void "a summary of statements counts what a summary of pages does, without following requests"() {
        given:
        List<QueryTailRecord> records = [record('r1', 'book/show', BOOK), record('r1', 'book/show', AUTHOR),
                                         record('r1', 'book/show', AUTHOR), record('r2', 'author/show', AUTHOR),
                                         record('r3', 'book/index', BOOKS), record(null, 'thread:scheduler-1', BOOKS)]
        QueryTailSummary statementsOnly = new QueryTailSummary(QueryTailMode.STATEMENTS)
        records.each { QueryTailRecord each ->
            summary.add(each)
            statementsOnly.add(each)
        }

        expect:
        statementsOnly.recordCount == summary.recordCount
        statementsOnly.requestCount == summary.requestCount
        statementsOnly.requestCount == 3
        counts(statementsOnly.statements()) == counts(summary.statements())
        counts(statementsOnly.statements(0, 'book')) == counts(summary.statements(0, 'book'))
        counts(statementsOnly.statements(0, 'book')) == [['select ... from author a1_0 where a1_0.id=?', 2, 1],
                                                         ['select ... from book b1_0 where b1_0.id=?', 1, 1],
                                                         ['select ... from book b1_0', 1, 1]]
    }

    void "a request of a page that #others other requests of it ran statements after counts on from what it ran unless the page let it go, whose most is #most"() {
        given: 'a request that runs two statements, and other requests of its page after it'
        request('r0', 'book/show', 'GET /book/show/0', AUTHOR, AUTHOR)
        (1..others).each { int n -> request("r${n}".toString(), 'book/show', 'GET /book/show/1', BOOK) }

        when: 'the first request runs another'
        request('r0', 'book/show', 'GET /book/show/0', AUTHOR)
        QueryTailSummary.PageSummary page = summary.pages().first()

        then: 'it is one request of all, and of its page only while the page follows it'
        summary.requestCount == others + 1
        page.requests == others + pageCounts
        page.total == others + 3
        page.max == most
        page.worstCount == most
        page.worstStatement == 'select ... from author a1_0 where a1_0.id=?'

        where:
        others                                  || pageCounts | most
        QueryTailSummary.FOLLOWED_REQUESTS - 1  || 1          | 3
        QueryTailSummary.FOLLOWED_REQUESTS      || 2          | 2
    }

    void "a request that #others other requests ran statements after is counted #counted"() {
        given: 'a request that runs a statement, and other requests after it, which the summary remembers in its place'
        request('r0', 'book/show', 'GET /book/show/0', AUTHOR)
        (1..others).each { int n -> request("r${n}".toString(), "page${n % 10}/show".toString(), 'GET /page/show/1', BOOK) }

        when: 'the first request runs another'
        request('r0', 'book/show', 'GET /book/show/0', AUTHOR)

        then:
        summary.requestCount == others + requests
        summary.recordCount == others + 2

        where:
        others                                     || requests | counted
        QueryTailSummary.REMEMBERED_REQUESTS - 1   || 1        | 'once'
        QueryTailSummary.REMEMBERED_REQUESTS       || 2        | 'again, as the summary let it go'
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
        summary.add(new QueryTailRecord(requestId: 'r1', request: 'GET /health', sql: 'select 1'))
        summary.add(new QueryTailRecord(requestId: 'r2', sql: 'select 1'))

        expect:
        summary.pages()*.page == ['GET /health', QueryTailSummary.NO_PAGE]
    }

    void "a request that lands on two pages adds to each only what it ran there"() {
        given: 'a request that queries in a filter before its controller is chosen, then in its action'
        5.times { summary.add(new QueryTailRecord(requestId: 'r1', request: 'GET /book/show/1', sql: AUTHOR)) }
        request('r1', 'book/show', 'GET /book/show/1', BOOK)
        request('r2', 'book/show', 'GET /book/show/2', BOOK, AUTHOR, AUTHOR)

        when:
        Map<String, QueryTailSummary.PageSummary> pages = summary.pages().collectEntries { [it.page, it] }

        then: 'the requests are counted once in all'
        summary.requestCount == 2
        summary.recordCount == 9

        and: 'the page the request ended on counts only what it ran there'
        with(pages['book/show']) {
            requests == 2
            total == 4
            average == 2.0d
            max == 3
            worstStatement == 'select ... from author a1_0 where a1_0.id=?'
            worstCount == 2
        }

        and: 'what it ran before the controller was chosen is counted under its path'
        with(pages['GET /book/show/1']) {
            requests == 1
            total == 5
            max == 5
            worstCount == 5
        }
    }

    void "a page filter matches a page, a controller, or a namespaced controller"() {
        expect:
        QueryTailSummary.matchesPage('book/show', 'book/show')
        QueryTailSummary.matchesPage('book/show', 'book')
        QueryTailSummary.matchesPage('admin:book/show', 'book')
        QueryTailSummary.matchesPage('admin:book/show', 'admin:book/show')
        QueryTailSummary.matchesPage('admin:book/show', 'book/show')
        QueryTailSummary.matchesPage('thread:main', 'thread:main')
        !QueryTailSummary.matchesPage('book/show', 'book/index')
        !QueryTailSummary.matchesPage('bookmark/show', 'book')
        !QueryTailSummary.matchesPage('admin:book/show', 'admin')
        !QueryTailSummary.matchesPage('admin:book/show', 'book/index')
        !QueryTailSummary.matchesPage('admin:bookmark/show', 'mark/show')
        !QueryTailSummary.matchesPage('thread:main', 'main')
        !QueryTailSummary.matchesPage('thread:main', 'thread')
        QueryTailSummary.matchesPage('thread:pool-?-thread-?', 'thread:pool-1-thread-3')
        QueryTailSummary.matchesPage('thread:pool-1-thread-3', 'thread:pool-?-thread-?')
        QueryTailSummary.matchesPage('thread:pool-1-thread-3', 'thread:pool-2-thread-9')
        !QueryTailSummary.matchesPage('thread:pool-1-thread-3', 'pool-1-thread-3')
        !QueryTailSummary.matchesPage('thread:pool-1-thread-3', 'thread:pool-1-worker-3')
        QueryTailSummary.matchesPage(QueryTailSummary.OTHER_THREADS, QueryTailSummary.OTHER_THREADS)
    }

    void "the page of an operation a request ran matches what the page it ran on matches, and its own name"() {
        expect:
        QueryTailSummary.matchesPage('graphql/index (query BookList)', 'graphql/index (query BookList)')
        QueryTailSummary.matchesPage('graphql/index (query BookList)', 'graphql/index')
        QueryTailSummary.matchesPage('graphql/index (query BookList)', 'graphql')
        QueryTailSummary.matchesPage('api:graphql/index (mutation {bookCreate})', 'graphql/index')
        QueryTailSummary.matchesPage('POST /?/? (query BookList)', 'POST /?/?')
        !QueryTailSummary.matchesPage('graphql/index (query BookList)', 'graphql/index (query AuthorList)')
        !QueryTailSummary.matchesPage('graphql/index (query BookList)', 'book')
        !QueryTailSummary.matchesPage('(query BookList)', 'query')
        !QueryTailSummary.matchesPage('thread:worker (1)', 'thread:worker')
    }

    void "the requests of a JVM that joins the run of another JVM twice are counted apart"() {
        given: 'a capture file another JVM is writing, which holds the lock every writer holds while it writes'
        File file = new File(tempDir, 'capture.log')
        file.text = QueryTailRecord.header('other-jvm') + '\n'
        QueryTailOtherJvm writer = new QueryTailOtherJvm(null)
        writer.tell("share ${file.path}.lock")

        when: 'this JVM joins the run, serves a request for book/index and closes its capture, twice'
        2.times {
            QueryTailCapture capture = QueryTailCapture.open(file)
            inRequest('book', 'index') { capture.record('DEFAULT', 'select b1_0.id from book b1_0') }
            capture.close()
        }
        summarize(file)

        then: 'each request ran one statement'
        summary.recordCount == 2
        summary.requestCount == 2
        with(summary.pages().first()) {
            page == 'book/index'
            requests == 2
            average == 1.0d
            max == 1
            worstCount == 1
        }

        cleanup:
        writer?.close()
    }

    void "captured statements that read different tables stay apart, #what"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = QueryTailCapture.open(file)
        capture.syntax = QueryTailSyntax.POSTGRESQL
        inRequest('book', 'index') { statements.each { capture.record('DEFAULT', it) } }
        capture.close()

        when:
        summarize(file)

        then: 'neither ran twice'
        summary.statements()*.statement as Set == shown as Set
        summary.statements()*.count == [1, 1]
        summary.pages().first().worstCount == 1

        where:
        what                                        | statements                                                         | shown
        'through common table expressions of a name' | ['with selected as (select id from book) select id from selected',
                                                       'with selected as (select id from author) select id from selected'] | ['with selected as (select id from book) select ... from selected',
                                                                                                                               'with selected as (select id from author) select ... from selected']
        'when their quoted names differ in spaces'  | ['select id from "order  lines"', 'select id from "order lines"']    | ['select ... from "order  lines"', 'select ... from "order lines"']
    }

    void "captured statements that are withheld are counted, but never as a repeat of one another"() {
        given: 'a request that runs three different statements the capture withholds, and one statement twice'
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = QueryTailCapture.open(file)
        capture.syntax = QueryTailSyntax.UNKNOWN
        inRequest('book', 'show') {
            ['select "title" from book', 'select "name" from author', 'select "city" from address', AUTHOR, AUTHOR].each {
                capture.record('DEFAULT', it)
            }
        }
        capture.close()

        when:
        summarize(file)

        then: 'every statement counts toward the request, and only the one that ran twice repeats'
        with(summary.pages().first()) {
            total == 5
            max == 5
            worstStatement == 'select ... from author a1_0 where a1_0.id=?'
            worstCount == 2
        }

        and: 'the withheld statements are counted together, as statements whose text is not known'
        counts(summary.statements()) == [[QueryTailSummary.WITHHELD, 3, 1], ['select ... from author a1_0 where a1_0.id=?', 2, 1]]
        summary.statements().first().type == StatementType.OTHER
        counts(summary.statements(0, 'book')) == counts(summary.statements())
    }

    void "a page whose requests ran only withheld statements repeats none"() {
        given:
        request('r1', 'book/show', 'GET /book/show/1', QueryTailRedactor.WITHHELD, QueryTailRedactor.WITHHELD)

        expect:
        with(summary.pages().first()) {
            total == 2
            max == 2
            worstStatement == null
            worstCount == 0
        }
    }

    void "captured MongoDB commands are counted as they were recorded, whatever their names hold"() {
        given: 'commands whose names or collections differ only in their spaces, hold a clause or parentheses, or have no options left'
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = QueryTailCapture.open(file)
        List<String> commands = ['find db.book {"filter": {"a\\"  b": ?}}', 'find db.book {"filter": {"a\\" b": ?}}',
                                 'find db.book {"filter": {"a\\" from b": ?}}',
                                 'find db.book(archive) {"filter": {"a\\"  b": ?}}', 'find db.book(archive) {"filter": {"a\\" b": ?}}',
                                 'find db.wide  collection', 'find db.wide collection', 'insert db.book(archive) {"documents": [{"title": ?}]}']
        inRequest('book', 'index') { commands.each { String command -> capture.recordRedacted('DEFAULT', { command } as Supplier<String>) } }
        capture.close()

        when:
        summarize(file)

        then:
        summary.statements()*.statement as Set == commands as Set
        summary.statements()*.count == [1] * commands.size()
    }

    void "a capture counts requests apart even when another capture records the first request before it"() {
        given:
        QueryTailCapture first = QueryTailCapture.open(new File(tempDir, 'first.log'))
        File file = new File(tempDir, 'second.log')
        QueryTailCapture second = QueryTailCapture.open(file)

        when:
        inRequest('book', 'index') {
            first.record('DEFAULT', BOOK)
            second.record('reporting', BOOK)
        }
        inRequest('book', 'index') { second.record('reporting', BOOK) }
        summarize(file)

        then:
        summary.recordCount == 2
        summary.requestCount == 2
        with(summary.pages().first()) {
            page == 'book/index'
            requests == 2
            average == 1.0d
            max == 1
            worstCount == 1
        }

        cleanup:
        first?.close()
        second?.close()
    }

    /** Reads every record of a capture file the way the command does, and adds it to the summary. */
    private void summarize(File file) {
        QueryTailFileReader reader = new QueryTailFileReader(file, true)
        try {
            for (List<QueryTailRecord> records = reader.poll(); !records.isEmpty(); records = reader.poll()) {
                records.each { summary.add(it) }
            }
        }
        finally {
            reader.close()
        }
    }

    /**
     * Runs work on a thread serving a request that the reader the tests find on the class path resolves to a
     * controller and action, as Grails URL mapping resolves one.
     */
    private static void inRequest(String controller, String action, Closure work) {
        MockHttpServletRequest request = new MockHttpServletRequest('GET', "/${controller}/${action}".toString())
        QueryTailStubRequestReader.resolve(request, "${controller}/${action}".toString())
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))
        try {
            work.call()
        }
        finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    /** @return the page of a thread whose name no other of the first thousands shares, its numbers replaced or not */
    private static String threadNamed(int i) {
        // the digits of the number spelled as letters, a for 0 to j for 9
        'thread:worker-' + String.valueOf(i).collect { String digit -> (char) (('a' as char) + (digit as int)) }.join('')
    }

    private static QueryTailRecord record(String id, String page, String sql) {
        new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: id, page: page, request: id ? "GET /${page}".toString() : null, sql: sql)
    }

    private static List<List<Object>> counts(List<QueryTailSummary.StatementSummary> statements) {
        statements.collect { [it.statement, it.count, it.pages] }
    }

    private void request(String id, String page, String request, String... statements) {
        for (String sql : statements) {
            summary.add(new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: id, page: page, request: request, sql: sql))
        }
    }
}
