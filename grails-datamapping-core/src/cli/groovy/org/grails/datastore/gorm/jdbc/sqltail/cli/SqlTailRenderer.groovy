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

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.jdbc.sqltail.SqlTailRecord

/**
 * Prints what the {@code sql-tail} command shows: the page and statement summaries as tables, and
 * the stream of statements with a header for each request. Statements are coloured by kind when
 * the console takes ANSI escapes.
 *
 * @since 8.1
 */
@CompileStatic
class SqlTailRenderer {

    /** The width used when the terminal's is unknown. */
    public static final int DEFAULT_WIDTH = 180

    private static final String RESET = '\u001B[0m'
    private static final String BOLD = '\u001B[1m'
    private static final String DIM = '\u001B[2m'
    private static final String RED = '\u001B[31m'
    private static final String GREEN = '\u001B[32m'
    private static final String YELLOW = '\u001B[33m'
    private static final String CYAN = '\u001B[36m'
    private static final String CLEAR_SCREEN = '\u001B[H\u001B[2J'
    private static final String ELLIPSIS = '…'

    final PrintStream out
    final boolean ansi
    int width = DEFAULT_WIDTH

    private final Map<String, Integer> streamedRequestCounts = [:]

    /**
     * @param out where to print
     * @param ansi whether the output takes ANSI escapes
     */
    SqlTailRenderer(PrintStream out, boolean ansi) {
        this.out = out
        this.ansi = ansi
    }

    /**
     * Clears the screen before a summary is printed again; a plain console gets a separator.
     */
    void clearScreen() {
        if (ansi) {
            out.print(CLEAR_SCREEN)
        }
        else {
            out.println()
            out.println('=' * Math.min(width, 80))
        }
    }

    /**
     * Prints the line above a summary.
     *
     * @param file the capture file
     * @param summary what has been counted
     * @param live whether the summary keeps refreshing
     */
    void renderTitle(File file, SqlTailSummary summary, boolean live) {
        String counts = "${summary.recordCount} statements in ${summary.requestCount} requests"
        String hint = live ? '  (refreshes as statements run; Ctrl-C to stop)' : ''
        out.println(bold("sql-tail ${file}") + "  ${counts}${hint}")
    }

    /**
     * Prints the pages, the ones running the most statements per request first.
     *
     * @param summary what has been counted
     * @param top how many pages, or 0 for all
     */
    void renderPages(SqlTailSummary summary, int top) {
        List<SqlTailSummary.PageSummary> pages = summary.pages(top)
        if (pages.isEmpty()) {
            out.println(dim('No statements yet.'))
            return
        }
        int pageWidth = Math.max('PAGE'.length(), pages.collect { it.page.length() }.max())
        String format = "%-${pageWidth}s  %8s  %7s  %5s  %6s  %s"
        out.println(bold(String.format(format, 'PAGE', 'REQUESTS', 'AVG/REQ', 'MAX', 'TOTAL', 'MOST REPEATED IN ONE REQUEST')))
        int statementWidth = width - (pageWidth + 2 + 8 + 2 + 7 + 2 + 5 + 2 + 6 + 2)
        for (SqlTailSummary.PageSummary page : pages) {
            String worst = page.worstStatement == null ? '' : "${page.worstCount}× ${page.worstStatement}"
            String line = String.format(format, page.page, page.requests, String.format('%.1f', page.average),
                    page.max, page.total, fit(worst, statementWidth))
            out.println(page.worstStatement == null ? line : colour(SqlStatementType.of(page.worstStatement), line))
        }
    }

    /**
     * Prints the statements, the most run first.
     *
     * @param summary what has been counted
     * @param top how many statements, or 0 for all
     * @param page only the statements of this page, or null
     */
    void renderStatements(SqlTailSummary summary, int top, String page) {
        List<SqlTailSummary.StatementSummary> statements = summary.statements(top, page)
        if (page) {
            out.println(bold("Statements of ${page}"))
        }
        if (statements.isEmpty()) {
            out.println(dim(page ? "No statements for ${page} yet.".toString() : 'No statements yet.'))
            return
        }
        String format = '%7s  %5s  %s'
        out.println(bold(String.format(format, 'COUNT', 'PAGES', 'STATEMENT')))
        int statementWidth = width - (7 + 2 + 5 + 2)
        for (SqlTailSummary.StatementSummary statement : statements) {
            out.println(colour(statement.type,
                    String.format(format, statement.count, statement.pages, fit(statement.statement, statementWidth))))
        }
    }

    /**
     * Prints a statement as it runs, with a header when it starts a new request and the count of
     * the request it ends.
     *
     * @param record the statement
     * @param previous the statement printed before it, or null
     */
    void renderStream(SqlTailRecord record, SqlTailRecord previous) {
        String requestKey = record.requestId ?: SqlTailSummary.pageOf(record)
        String previousKey = previous == null ? null : (previous.requestId ?: SqlTailSummary.pageOf(previous))
        if (requestKey != previousKey) {
            if (previousKey != null && streamedRequestCounts.containsKey(previousKey)) {
                out.println(dim("   ${previousKey}: ${streamedRequestCounts.remove(previousKey)} statements"))
            }
            String header = record.webRequest ?
                    "── ${record.requestId}  ${record.request ?: ''}  ${record.page ?: ''}".toString() :
                    "── ${SqlTailSummary.pageOf(record)}".toString()
            out.println(bold(header.trim()))
        }
        streamedRequestCounts.merge(requestKey, 1, Integer::sum)
        out.println(colour(SqlStatementType.of(record.sql), '   ' + fit(SqlStatementCollapser.collapse(record.sql), width - 3)))
    }

    /**
     * @param type a statement kind
     * @param text a line
     * @return the line in the kind's colour when the console takes ANSI escapes
     */
    String colour(SqlStatementType type, String text) {
        if (!ansi) {
            return text
        }
        switch (type) {
            case SqlStatementType.SELECT:
                return CYAN + text + RESET
            case SqlStatementType.INSERT:
                return GREEN + text + RESET
            case SqlStatementType.UPDATE:
                return YELLOW + text + RESET
            case SqlStatementType.DELETE:
                return RED + text + RESET
            default:
                return text
        }
    }

    private String bold(String text) {
        ansi ? BOLD + text + RESET : text
    }

    private String dim(String text) {
        ansi ? DIM + text + RESET : text
    }

    private static String fit(String text, int width) {
        if (width <= 0 || text.length() <= width) {
            return text
        }
        width == 1 ? ELLIPSIS : text.substring(0, width - 1) + ELLIPSIS
    }
}
