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

import java.util.regex.Pattern

import groovy.transform.CompileStatic

import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datamapping.core.querytail.QueryTailRedactor

/**
 * Counts the statements of a capture by the page that ran them and by statement. A page is a
 * controller and action; every request of a page is counted on its own, so a page shows how many
 * statements it runs per request on average and at most, and which statement repeats most within
 * one request, which is what an N+1 looks like. Work outside a web request is one page per thread,
 * which serves no request: it is ranked after the pages of requests, by how many statements it ran,
 * so that the startup of an application or a test worker does not hide the pages a request runs. The
 * numbers in the name of a thread are replaced by {@code ?}, so that the threads of one pool, which
 * an executor numbers as it starts them, are one page.
 *
 * <p>A page counts only the statements a request ran while it served that page. A request whose
 * statements land on two pages, one forwarded to another action or one that queries in a filter
 * before a controller is chosen, is a request of each, with the statements it ran there.</p>
 *
 * <p>A statement recorded as withheld, whose values could not be taken out, is counted as one of
 * {@link #WITHHELD} rather than as the statement it was, which is not known, so it is never a
 * repeat of another: two withheld statements may have been any two statements.</p>
 *
 * <p>A summary keeps what it counts rather than the statements it counted, so that it can follow a
 * capture however long it grows. It keeps the {@link #REMEMBERED_TEXTS} texts SQL statements were last
 * recorded as, with the statement each shortens to, so that a statement recorded again is not
 * shortened again, and the keys of the {@link #REMEMBERED_REQUESTS} requests that ran a statement
 * last, so that it counts each request once. A summary of pages folds each statement into the most a
 * request of its page has run and repeated as it comes, and follows the counts of the
 * {@link #FOLLOWED_REQUESTS} requests of each page that ran a statement last. A request that runs
 * another statement only after that many other requests of its page have is counted as a request of
 * its page again, and counts on from that statement; one that runs another only after
 * {@link #REMEMBERED_REQUESTS} other requests have is counted again among all requests. Only that many
 * requests in flight at once tell the counts apart from exact ones. A summary of statements follows no
 * request. It keeps the pages of {@link #THREAD_PAGES} threads apart, and counts the statements of
 * any other thread under {@link #OTHER_THREADS}.</p>
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailSummary {

    /** The page of a request that never reached a controller and has no path either. */
    public static final String NO_PAGE = '(no page)'

    /** Where the operation a request ran starts in its page: a page and a request line hold no space before it. */
    private static final String OPERATION_START = ' ('

    /** What the statements recorded as withheld are counted as, all together. */
    public static final String WITHHELD = '(statements withheld: their values could not be redacted)'

    /** How many requests of each page a summary of pages follows the counts of at once. */
    public static final int FOLLOWED_REQUESTS = 1000

    /** How many of the texts SQL statements were last recorded as a summary keeps the shortened statement of. */
    public static final int REMEMBERED_TEXTS = 1000

    /** How many of the requests that ran a statement last a summary remembers, so that it counts each once. */
    public static final int REMEMBERED_REQUESTS = 10000

    /** How many pages of threads a summary keeps apart, each the threads of one name once its numbers are replaced. */
    public static final int THREAD_PAGES = 1000

    /** The page the statements of every thread past {@link #THREAD_PAGES} are counted under. */
    public static final String OTHER_THREADS = QueryTailRecord.THREAD_PAGE_PREFIX + '(other threads)'

    private static final Pattern NUMBER = Pattern.compile('\\d+')

    private final boolean byRequest
    private final Map<String, PageStats> pages = new LinkedHashMap<>()
    private final Map<String, StatementStats> statements = new LinkedHashMap<>()

    /** The requests that ran a statement last, the one that ran one last at the end. */
    private final Map<String, Boolean> requestKeys = new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            size() > QueryTailSummary.REMEMBERED_REQUESTS
        }
    }
    private int requestCount
    private int threadPages

    /** The statement each SQL statement is counted as, by the text it was recorded as, of the texts recorded last. */
    private final Map<String, String> shortened = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            size() > QueryTailSummary.REMEMBERED_TEXTS
        }
    }
    private int recordCount

    /**
     * A summary of pages.
     */
    QueryTailSummary() {
        this(QueryTailMode.PAGES)
    }

    /**
     * @param mode what the summary is shown as: a summary of {@link QueryTailMode#PAGES} follows the
     * requests of each page, which no other needs
     */
    QueryTailSummary(QueryTailMode mode) {
        this.byRequest = mode == QueryTailMode.PAGES
    }

    /**
     * Counts a record.
     *
     * @param record a captured statement
     */
    void add(QueryTailRecord record) {
        boolean withheld = !record.document && record.sql == QueryTailRedactor.WITHHELD
        String collapsed = withheld ? WITHHELD : statementOf(record)
        String page = countedPage(record)
        String requestKey = record.requestId ?: page

        if (record.requestId != null && requestKeys.put(requestKey, Boolean.TRUE) == null) {
            requestCount++
        }

        // every count keys a statement by the one copy of it the summary keeps
        StatementStats stats = statements.get(collapsed)
        if (stats == null) {
            stats = new StatementStats(statement: collapsed, type: StatementType.of(collapsed))
            statements.put(collapsed, stats)
        }
        String statement = stats.statement

        PageStats pageStats = pages.get(page)
        if (pageStats == null) {
            pageStats = new PageStats(page)
            pages.put(page, pageStats)
        }
        pageStats.counts.merge(statement, 1, Integer::sum)
        pageStats.total++
        if (byRequest) {
            pageStats.count(requestKey, statement, !withheld)
        }

        stats.count++
        stats.pages.add(pageStats.page)
        recordCount++
    }

    /**
     * @return the statement a record is counted as; a document is counted as it was recorded, which takes no work
     */
    private String statementOf(QueryTailRecord record) {
        if (record.document || record.sql == null) {
            return StatementCollapser.statementOf(record)
        }
        String statement = shortened.get(record.sql)
        if (statement == null) {
            statement = StatementCollapser.statementOf(record)
            shortened.put(record.sql, statement)
        }
        statement
    }

    /**
     * @param record a captured statement
     * @return the page it was recorded under, as a stream of statements shows it
     */
    static String pageOf(QueryTailRecord record) {
        record.page ?: record.request ?: NO_PAGE
    }

    /**
     * @return the page a record is counted under: its page, but for one of a thread, whose numbers are replaced,
     * and which is {@link #OTHER_THREADS} once {@link #THREAD_PAGES} threads have a page
     */
    private String countedPage(QueryTailRecord record) {
        String page = pageOf(record)
        if (!page.startsWith(QueryTailRecord.THREAD_PAGE_PREFIX)) {
            return page
        }
        String threads = threadPage(page)
        if (pages.containsKey(threads)) {
            return threads
        }
        if (threadPages >= THREAD_PAGES) {
            return OTHER_THREADS
        }
        threadPages++
        threads
    }

    /**
     * @param page the page of a thread
     * @return the page of every thread of its name with the numbers in it replaced, as an executor numbers the
     * threads of a pool
     */
    static String threadPage(String page) {
        page == OTHER_THREADS ? page : NUMBER.matcher(page).replaceAll(QueryTailRedactor.PLACEHOLDER)
    }

    /**
     * @return how many statements have been counted
     */
    int getRecordCount() {
        recordCount
    }

    /**
     * @return how many web requests have been seen; the work of a thread outside a request is none
     */
    int getRequestCount() {
        requestCount
    }

    /**
     * The pages, the ones running the most statements per request first, then the threads that ran
     * statements outside a request, the busiest first. Only a summary of pages knows the requests of
     * a page, and how many statements one runs and repeats at most.
     *
     * @param top how many to return, or 0 for all
     * @return the pages
     */
    List<PageSummary> pages(int top = 0) {
        List<PageSummary> summaries = pages.values().collect { PageStats stats -> summarize(stats) }
        summaries.sort { PageSummary a, PageSummary b ->
            if (a.thread != b.thread) {
                return a.thread ? 1 : -1
            }
            int byAverage = Double.compare(b.average, a.average)
            byAverage != 0 ? byAverage : Integer.compare(b.total, a.total)
        }
        limit(summaries, top)
    }

    /**
     * The statements, the most run first.
     *
     * @param top how many to return, or 0 for all
     * @param page only the statements of this page, given as {@code controller/action} or as a
     * controller name, or null for every page
     * @return the statements
     */
    List<StatementSummary> statements(int top = 0, String page = null) {
        List<StatementSummary> summaries
        if (page) {
            Map<String, Integer> counts = new LinkedHashMap<>()
            Map<String, Set<String>> statementPages = new LinkedHashMap<>()
            for (PageStats stats : pages.values()) {
                if (matchesPage(stats.page, page)) {
                    stats.counts.each { String statement, Integer count ->
                        counts.merge(statement, count, Integer::sum)
                        statementPages.computeIfAbsent(statement) { new LinkedHashSet<String>() }.add(stats.page)
                    }
                }
            }
            summaries = counts.collect { String statement, Integer count ->
                new StatementSummary(statement: statement, type: StatementType.of(statement), count: count,
                        pages: statementPages.get(statement).size())
            }
        }
        else {
            summaries = statements.values().collect { StatementStats stats ->
                new StatementSummary(statement: stats.statement, type: stats.type, count: stats.count, pages: stats.pages.size())
            }
        }
        summaries.sort { StatementSummary a, StatementSummary b -> Integer.compare(b.count, a.count) }
        limit(summaries, top)
    }

    /**
     * @param page a page label
     * @param filter {@code controller/action}, a controller name, or a thread page; a namespaced
     * controller's page matches its name and its {@code controller/action} as well as its own, the
     * page of an operation a request ran, such as {@code graphql/index (query BookList)}, matches what
     * the page it ran on matches as well as its own, and the page of a thread matches the name of any
     * thread of its pool, its numbers replaced or not
     * @return whether the page is the one the filter names
     */
    static boolean matchesPage(String page, String filter) {
        if (page == filter) {
            return true
        }
        if (page.startsWith(QueryTailRecord.THREAD_PAGE_PREFIX)) {
            return filter.startsWith(QueryTailRecord.THREAD_PAGE_PREFIX) && threadPage(page) == threadPage(filter)
        }
        int operation = page.indexOf(OPERATION_START)
        if (operation > 0 && page.endsWith(')')) {
            return matchesPage(page.substring(0, operation), filter)
        }
        if (filter.contains('/')) {
            // a page of a namespaced controller is the same controller and action under its namespace
            return page.endsWith(':' + filter)
        }
        String controller = page.contains('/') ? page.substring(0, page.indexOf('/')) : page
        controller == filter || controller.endsWith(':' + filter)
    }

    private static PageSummary summarize(PageStats stats) {
        boolean thread = stats.page.startsWith(QueryTailRecord.THREAD_PAGE_PREFIX)
        int requestCount = thread ? 0 : stats.requests
        new PageSummary(page: stats.page, thread: thread, requests: requestCount, total: stats.total,
                average: requestCount == 0 ? 0 : stats.total / (double) requestCount, max: thread ? 0 : stats.max,
                worstStatement: stats.worstStatement, worstCount: stats.worstCount)
    }

    private static <T> List<T> limit(List<T> list, int top) {
        top > 0 && list.size() > top ? list.subList(0, top) : list
    }

    /** A page: how many statements its requests run. */
    @CompileStatic
    static class PageSummary {
        String page
        /** Whether the page is the work of a thread outside a request, which has no requests to count by. */
        boolean thread
        int requests
        int total
        double average
        int max
        String worstStatement
        int worstCount
    }

    /** A statement: how often it ran and on how many pages. */
    @CompileStatic
    static class StatementSummary {
        String statement
        StatementType type
        int count
        int pages
    }

    /** What one request has run on one page. */
    @CompileStatic
    private static class RequestStats {
        Map<String, Integer> counts = new HashMap<>()
        int total
    }

    @CompileStatic
    private static class PageStats {
        final String page
        final Map<String, Integer> counts = new LinkedHashMap<>()
        int total

        /** How many requests of the page have been counted; by a summary of pages only. */
        int requests

        /** The requests of the page whose counts are followed, the one that ran a statement last at the end. */
        final Map<String, RequestStats> followed = new LinkedHashMap<String, RequestStats>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RequestStats> eldest) {
                // qualified, as a bare name in a map is one of its keys
                size() > QueryTailSummary.FOLLOWED_REQUESTS
            }
        }

        /** The most statements a request of the page has run. */
        int max

        /** The statement a request of the page has repeated most, and how often it has. */
        String worstStatement
        int worstCount

        PageStats(String page) {
            this.page = page
        }

        /**
         * Counts a statement a request of the page ran.
         *
         * @param known whether the statement is known, and so may repeat another; a withheld one is not
         */
        void count(String requestKey, String statement, boolean known) {
            RequestStats request = followed.get(requestKey)
            if (request == null) {
                request = new RequestStats()
                followed.put(requestKey, request)
                requests++
            }
            request.total++
            max = Math.max(max, request.total)
            if (!known) {
                return
            }
            int repeated = request.counts.merge(statement, 1, Integer::sum)
            if (repeated > worstCount) {
                worstCount = repeated
                worstStatement = statement
            }
        }
    }

    @CompileStatic
    private static class StatementStats {
        String statement
        StatementType type
        int count
        Set<String> pages = new LinkedHashSet<>()
    }
}
