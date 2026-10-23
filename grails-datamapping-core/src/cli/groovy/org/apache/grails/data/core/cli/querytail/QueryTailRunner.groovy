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
 * Runs the {@code query-tail} command: waits for the capture file, follows it, and prints the
 * summary or the stream until stopped. The command script hands it the console and stops it when
 * the shell cancels the command.
 *
 * @since 8.1
 */
@CompileStatic
class QueryTailRunner {

    /** How to get an application to write the capture file. */
    public static final String START_HINT =
            "Start the application with 'grails run-app --query-tail' or './gradlew bootRun -Pgrails.gorm.queryTail.enabled' " +
            'to capture the statements it runs.'

    final QueryTailOptions options
    final QueryTailRenderer renderer

    /** How long to wait between looks at the file, in milliseconds. */
    long pollMillis = 250

    /** How long a summary stays on screen before it is printed again, in milliseconds. */
    long refreshMillis = 1000

    /** How much of the capture one look reads at most, in bytes; what is left is read without waiting. */
    int chunk = QueryTailFileReader.CHUNK

    private volatile boolean stopped

    QueryTailRunner(QueryTailOptions options, PrintStream out, boolean ansi) {
        this.options = options
        this.renderer = new QueryTailRenderer(out, ansi)
        this.renderer.width = options.width
    }

    /**
     * Ends the run; the summary on screen stays.
     */
    void stop() {
        stopped = true
    }

    /**
     * @return whether the run was stopped
     */
    boolean isStopped() {
        stopped
    }

    /**
     * Runs until stopped, or until the summary has been printed once with {@link QueryTailOptions#once}.
     *
     * @return 0 when it ran, 1 when there was nothing to read
     */
    int run() {
        File file = options.file
        boolean waited = !file.exists()
        if (waited && !awaitFile(file)) {
            return stopped ? 0 : 1
        }
        // a capture that appeared while waiting is a run that has just started: read it from its first statement
        QueryTailFileReader reader = new QueryTailFileReader(file, waited || options.all || options.once, chunk)
        try {
            follow(reader)
        }
        finally {
            reader.close()
        }
        0
    }

    private boolean awaitFile(File file) {
        if (options.once) {
            renderer.out.println("No query-tail capture at ${file}.")
            renderer.out.println(START_HINT)
            return false
        }
        renderer.out.println("Waiting for ${file}. ${START_HINT}")
        while (!file.exists()) {
            if (stopped || !pause(pollMillis)) {
                return false
            }
        }
        true
    }

    private void follow(QueryTailFileReader reader) {
        QueryTailSummary summary = new QueryTailSummary()
        QueryTailRecord previous = null
        long lastRender = 0
        boolean changed = true
        while (true) {
            List<QueryTailRecord> records = reader.poll()
            if (reader.restarted) {
                summary = new QueryTailSummary()
                renderer.reset()
                previous = null
                changed = true
            }
            for (QueryTailRecord record : records) {
                if (options.mode == QueryTailMode.STREAM) {
                    if (options.page == null || QueryTailSummary.matchesPage(QueryTailSummary.pageOf(record), options.page)) {
                        renderer.renderStream(record, previous)
                        previous = record
                    }
                }
                else {
                    summary.add(record)
                }
                changed = true
            }
            if (options.once) {
                if (stopped) {
                    return
                }
                if (reader.behind) {
                    continue
                }
                renderSummary(summary, false)
                return
            }
            long now = System.currentTimeMillis()
            if (options.mode != QueryTailMode.STREAM && changed && now - lastRender >= refreshMillis) {
                renderer.clearScreen()
                renderSummary(summary, true)
                lastRender = now
                changed = false
            }
            if (stopped || (!reader.behind && !pause(pollMillis))) {
                return
            }
        }
    }

    private void renderSummary(QueryTailSummary summary, boolean live) {
        if (options.mode == QueryTailMode.STREAM) {
            return
        }
        renderer.renderTitle(options.file, summary, live)
        if (options.mode == QueryTailMode.PAGES) {
            renderer.renderPages(summary, options.top)
        }
        else {
            renderer.renderStatements(summary, options.top, options.page)
        }
    }

    private boolean pause(long millis) {
        try {
            Thread.sleep(millis)
            true
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
            stopped = true
            false
        }
    }
}
