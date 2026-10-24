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

import groovy.transform.CompileStatic

import org.apache.grails.data.core.querytail.QueryTailRecord

/**
 * Counts the statements of a capture by the page that ran them and by statement. A page is a
 * controller and action; every request of a page is counted on its own, so a page shows how many
 * statements it runs per request on average and at most, and which statement repeats most within
 * one request, which is what an N+1 looks like. Work outside a web request is one page per thread.
 *
 * <p>A page counts only the statements a request ran while it served that page. A request whose
 * statements land on two pages, one forwarded to another action or one that queries in a filter
 * before a controller is chosen, is a request of each, with the statements it ran there.</p>
 *
 * @since 8.1
 */
@CompileStatic
class QueryTailSummary {

    /** The page of a request that never reached a controller and has no path either. */
    public static final String NO_PAGE = '(no page)'

    private final Map<String, PageStats> pages = new LinkedHashMap<>()
    private final Map<String, StatementStats> statements = new LinkedHashMap<>()
    private final Set<String> requestKeys = new HashSet<>()
    private int recordCount

    /**
     * Counts a record.
     *
     * @param record a captured statement
     */
    void add(QueryTailRecord record) {
        String statement = StatementCollapser.collapse(record.sql)
        String page = pageOf(record)
        String requestKey = record.requestId ?: page

        requestKeys.add(requestKey)

        PageStats pageStats = pages.get(page)
        if (pageStats == null) {
            pageStats = new PageStats(page: page)
            pages.put(page, pageStats)
        }
        RequestStats request = pageStats.requests.get(requestKey)
        if (request == null) {
            request = new RequestStats()
            pageStats.requests.put(requestKey, request)
        }
        request.counts.merge(statement, 1, Integer::sum)
        request.total++
        pageStats.counts.merge(statement, 1, Integer::sum)
        pageStats.total++

        StatementStats stats = statements.get(statement)
        if (stats == null) {
            stats = new StatementStats(statement: statement, type: StatementType.of(statement))
            statements.put(statement, stats)
        }
        stats.count++
        stats.pages.add(page)
        recordCount++
    }

    /**
     * @param record a captured statement
     * @return the page it is counted under
     */
    static String pageOf(QueryTailRecord record) {
        record.page ?: record.request ?: NO_PAGE
    }

    /**
     * @return how many statements have been counted
     */
    int getRecordCount() {
        recordCount
    }

    /**
     * @return how many requests have been seen, counting the work of each thread outside a request as one
     */
    int getRequestCount() {
        requestKeys.size()
    }

    /**
     * The pages, the ones running the most statements per request first.
     *
     * @param top how many to return, or 0 for all
     * @return the pages
     */
    List<PageSummary> pages(int top = 0) {
        List<PageSummary> summaries = pages.values().collect { PageStats stats -> summarize(stats) }
        summaries.sort { PageSummary a, PageSummary b ->
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
     * @param filter {@code controller/action}, a controller name, or a thread page
     * @return whether the page is the one the filter names
     */
    static boolean matchesPage(String page, String filter) {
        if (page == filter) {
            return true
        }
        if (filter.contains('/') || page.startsWith(QueryTailRecord.THREAD_PAGE_PREFIX)) {
            return false
        }
        String controller = page.contains('/') ? page.substring(0, page.indexOf('/')) : page
        controller == filter || controller.endsWith(':' + filter)
    }

    private PageSummary summarize(PageStats stats) {
        int max = 0
        String worstStatement = null
        int worstCount = 0
        for (RequestStats request : stats.requests.values()) {
            max = Math.max(max, request.total)
            request.counts.each { String statement, Integer count ->
                if (count > worstCount) {
                    worstCount = count
                    worstStatement = statement
                }
            }
        }
        int requestCount = stats.requests.size()
        new PageSummary(page: stats.page, requests: requestCount, total: stats.total,
                average: requestCount == 0 ? 0 : stats.total / (double) requestCount, max: max,
                worstStatement: worstStatement, worstCount: worstCount)
    }

    private static <T> List<T> limit(List<T> list, int top) {
        top > 0 && list.size() > top ? list.subList(0, top) : list
    }

    /** A page: how many statements its requests run. */
    @CompileStatic
    static class PageSummary {
        String page
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

    /** What one request ran on one page. */
    @CompileStatic
    private static class RequestStats {
        Map<String, Integer> counts = new LinkedHashMap<>()
        int total
    }

    @CompileStatic
    private static class PageStats {
        String page
        Map<String, RequestStats> requests = new LinkedHashMap<>()
        Map<String, Integer> counts = new LinkedHashMap<>()
        int total
    }

    @CompileStatic
    private static class StatementStats {
        String statement
        StatementType type
        int count
        Set<String> pages = new LinkedHashSet<>()
    }
}
