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
package org.apache.grails.data.simple.querytail

import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.apache.grails.datamapping.core.querytail.QueryTailCapture
import org.apache.grails.datamapping.core.querytail.QueryTailRecorder
import org.apache.grails.datastore.core.querytail.QueryTailSettings

/**
 * The query-tail capture of a connection source of the simple datastore, which GORM creates when
 * capture is enabled. The simple datastore keeps its entries in memory and runs no statements, so it
 * records what GORM asks of it as statements, which {@link SimpleMapStatements} builds from the query
 * and the mapping without a value in them: a query, an entry read by its key, entries read by their
 * keys together, and each insert, update and delete.
 *
 * <p>A read is recorded once, however much it reads: the entries a query reads to find, sort and
 * load its rows, the associations an entry loads as it is read, and the entries a write reads, are
 * part of that read or that write, and are not recorded on their own. An association loaded later,
 * as a proxy or a lazy collection is when it is first used, is a read of its own. What a read is
 * part of is tracked per thread, as the reads of a session run on the thread that uses it.</p>
 *
 * <p>The capture opens when the datastore is built and closes with it. Nothing of this is created
 * unless {@link QueryTailSettings#SETTING_ENABLED} is set, so a datastore that does not capture pays
 * nothing per operation.</p>
 *
 * @since 9.0
 */
@CompileStatic
class SimpleMapQueryTail implements Closeable {

    /** What the reads running on a thread are part of. */
    private static final ThreadLocal<Reading> READING = new ThreadLocal<>()

    /** The connection source name operations are recorded under. */
    final String dataSourceName

    /** The file operations are recorded to. */
    final File file

    private final QueryTailRecorder recorder

    /**
     * @param dataSourceName the connection source the operations run against
     * @param file the capture file operations are recorded to
     */
    SimpleMapQueryTail(String dataSourceName, File file) {
        this.dataSourceName = dataSourceName
        this.file = file
        this.recorder = new QueryTailRecorder(dataSourceName, file)
    }

    /**
     * Creates the capture of a connection source when capture is enabled.
     *
     * @param dataSourceName the connection source
     * @param settings the query-tail settings of the connection source
     * @return the capture, or null when capture is not enabled
     */
    static SimpleMapQueryTail create(String dataSourceName, QueryTailSettings settings) {
        settings != null && settings.enabled ? new SimpleMapQueryTail(dataSourceName, settings.resolveFile()) : null
    }

    /**
     * Opens the capture, unless it is open already.
     *
     * @return the capture, or null once this is closed without one
     */
    QueryTailCapture open() {
        recorder.open()
    }

    /**
     * @return where operations are recorded, or null before the capture has opened
     */
    QueryTailCapture getCapture() {
        recorder.capture
    }

    /**
     * Starts a query, which is recorded unless it runs as part of another read: what it reads is part of
     * it. It ends with {@link #exit()}, which the caller runs however the query ends.
     *
     * @param statement builds the statement of the query
     */
    void enterQuery(Supplier<String> statement) {
        if (!isReading()) {
            recorder.recordRedacted(statement)
        }
        reading()
    }

    /**
     * Starts a read of entries by their keys together, which is recorded as one statement once it reads an
     * entry, unless it runs as part of another read; an entry found in the session is not read again. It
     * ends with {@link #exit()}, which the caller runs however the read ends.
     *
     * @param statement builds the statement of the read
     */
    void enterBatch(Supplier<String> statement) {
        boolean top = !isReading()
        Reading reading = reading()
        if (top) {
            reading.batch = new Batch(recorder, statement, reading.depth)
        }
    }

    /**
     * Records an entry read by its key: on its own, or as the first entry a batch reads, and not at all
     * when it is read as part of another read.
     *
     * @param statement builds the statement of the read
     */
    void read(Supplier<String> statement) {
        Reading reading = READING.get()
        if (reading == null) {
            recorder.recordRedacted(statement)
        }
        else {
            reading.batch?.read(reading.depth)
        }
    }

    /**
     * Records an insert, an update or a delete, which is recorded wherever it runs.
     *
     * @param statement builds the statement of the write
     */
    void write(Supplier<String> statement) {
        recorder.recordRedacted(statement)
    }

    /**
     * Starts work whose reads are part of what runs it, as those of a write or of the loading of an entry
     * are; it ends with {@link #exit()}, which the caller runs however the work ends.
     */
    static void enter() {
        reading()
    }

    /**
     * Ends the work, query or read the last {@link #enter()}, {@link #enterQuery} or {@link #enterBatch}
     * on this thread started.
     */
    static void exit() {
        Reading reading = READING.get()
        if (reading == null) {
            return
        }
        if (reading.batch?.depth == reading.depth) {
            reading.batch = null
        }
        if (--reading.depth <= 0) {
            READING.remove()
        }
    }

    /**
     * Closes the capture; one closed before it opened never opens.
     */
    @Override
    void close() {
        recorder.close()
    }

    private static boolean isReading() {
        READING.get() != null
    }

    /** Goes one read deeper on this thread. */
    private static Reading reading() {
        Reading reading = READING.get()
        if (reading == null) {
            reading = new Reading()
            READING.set(reading)
        }
        reading.depth++
        reading
    }

    /**
     * What the reads running on a thread are part of: how deep they run, and the batch read, if any,
     * that is recorded once an entry is read at its own depth.
     */
    @CompileStatic
    private static final class Reading {

        int depth

        Batch batch
    }

    /**
     * A read of entries by their keys together, recorded once, when the first entry it reads is read.
     */
    @CompileStatic
    private static final class Batch {

        /** The depth the batch reads its entries at; an entry read deeper is part of loading one of them. */
        final int depth

        private final QueryTailRecorder recorder
        private final Supplier<String> statement
        private boolean recorded

        Batch(QueryTailRecorder recorder, Supplier<String> statement, int depth) {
            this.recorder = recorder
            this.statement = statement
            this.depth = depth
        }

        void read(int at) {
            if (at == depth && !recorded) {
                recorded = true
                recorder.recordRedacted(statement)
            }
        }
    }
}
