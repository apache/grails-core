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
package org.apache.grails.datamapping.core.querytail

import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

import groovy.transform.CompileStatic

/**
 * The capture of one connection source whose owner cannot open it up front, such as a session
 * factory that is told it was created only after its first statements may have run, or a MongoDB
 * client built before it is known whether a datastore will use it: the capture is opened by
 * whichever comes first, the first statement recorded or {@link #open()}, and closed once, with what
 * owns it. A recorder closed before it opened never opens, so one that is built and let go unused
 * leaves the capture file as it found it.
 *
 * <p>The recorder travels with what owns it, which may be serialized, as a Hibernate session
 * factory observer is; the capture does not. A recorder read back in the JVM that wrote it is the recorder
 * written, so that what is recorded through either goes to the one capture its owner closes; read
 * back anywhere else, or once the recorder written has closed, it records nothing, since nothing
 * would close a capture it opened.</p>
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailRecorder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L

    /** The recorders of this JVM that have not closed, by id, which a recorder read back resolves to. */
    private static final Map<String, Registration> RECORDERS = new ConcurrentHashMap<>()

    /** The registrations of recorders let go without closing, whose recorders have been collected. */
    private static final ReferenceQueue<QueryTailRecorder> COLLECTED = new ReferenceQueue<>()

    /** The connection source name statements are recorded under. */
    final String dataSourceName

    /** The file statements are recorded to. */
    final File file

    /** Tells this recorder from every other, in any JVM, once it is read back. */
    private final String id = UUID.randomUUID().toString()

    /** The syntax of the database the statements run on, known up front, or null when the owner tells it on opening. */
    private final transient QueryTailSyntax syntax

    private transient volatile QueryTailCapture capture
    private transient boolean closed

    /**
     * @param dataSourceName the connection source the statements run against
     * @param file the capture file statements are recorded to
     */
    QueryTailRecorder(String dataSourceName, File file) {
        this(dataSourceName, file, null)
    }

    /**
     * @param dataSourceName the connection source the statements run against
     * @param file the capture file statements are recorded to
     * @param syntax the syntax of the database the statements run on, which the capture redacts them in from the
     * first, or null when it is told on {@linkplain #open(QueryTailSyntax) opening}
     */
    QueryTailRecorder(String dataSourceName, File file, QueryTailSyntax syntax) {
        this.dataSourceName = dataSourceName
        this.file = file
        this.syntax = syntax
        // one that is let go without closing leaves an entry whose recorder is collected
        forgetCollected()
        RECORDERS.put(id, new Registration(this, id, COLLECTED))
    }

    /**
     * Records a statement that is about to run on the current thread, without its values, opening the
     * capture first if nothing has.
     *
     * @param statement the statement
     */
    void record(String statement) {
        open()?.record(dataSourceName, statement)
    }

    /**
     * Records a statement that holds no value any more, as {@link QueryTailCapture#recordRedacted} does,
     * opening the capture first if nothing has.
     *
     * @param statement takes the values out of the statement
     */
    void recordRedacted(Supplier<String> statement) {
        open()?.recordRedacted(dataSourceName, statement)
    }

    /**
     * Opens the capture, unless it is open already.
     *
     * @return the capture, or null once the recorder is closed without one
     */
    QueryTailCapture open() {
        QueryTailCapture current = capture
        if (current != null) {
            return current
        }
        synchronized (this) {
            if (capture == null && !closed) {
                QueryTailCapture opened = QueryTailCapture.open(file)
                if (syntax != null) {
                    opened.syntax = syntax
                }
                capture = opened
            }
            return capture
        }
    }

    /**
     * Opens the capture, unless it is open already, and records the statements of the connection source
     * in the syntax of its database from now on, as a session factory opens it once it knows its dialect.
     *
     * @param syntax the syntax of the database the statements run on
     * @return the capture, or null once the recorder is closed without one
     */
    QueryTailCapture open(QueryTailSyntax syntax) {
        QueryTailCapture current = open()
        if (current != null) {
            current.syntax = syntax
        }
        current
    }

    /**
     * @return the capture, or null before anything has opened it
     */
    QueryTailCapture getCapture() {
        capture
    }

    /**
     * Stops recording and closes the capture; a capture that was never opened stays unopened.
     */
    synchronized void close() {
        closed = true
        RECORDERS.remove(id)
        capture?.close()
    }

    /**
     * @return the recorder written, when it is of this JVM and has not closed, else this one, closed
     */
    @Serial
    private Object readResolve() {
        QueryTailRecorder written = RECORDERS.get(id)?.get()
        if (written != null) {
            return written
        }
        closed = true
        this
    }

    private static void forgetCollected() {
        for (Reference<? extends QueryTailRecorder> collected = COLLECTED.poll(); collected != null; collected = COLLECTED.poll()) {
            Registration registration = (Registration) collected
            RECORDERS.remove(registration.id, registration)
        }
    }

    /**
     * The entry of a recorder in {@link #RECORDERS}, which names the key it is under, so that once the recorder is
     * collected the entry is removed by its key rather than looked for among all of them.
     */
    @CompileStatic
    private static final class Registration extends WeakReference<QueryTailRecorder> {

        final String id

        Registration(QueryTailRecorder recorder, String id, ReferenceQueue<QueryTailRecorder> collected) {
            super(recorder, collected)
            this.id = id
        }
    }
}
