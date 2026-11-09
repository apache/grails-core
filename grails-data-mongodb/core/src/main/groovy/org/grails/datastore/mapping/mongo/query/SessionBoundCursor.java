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
package org.grails.datastore.mapping.mongo.query;

import java.util.function.Supplier;

import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.client.MongoCursor;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.SessionCallback;

/**
 * Fetches each batch after a query's first as the query fetched the first: in the session bound to
 * the thread, or in a session of its own when none is.
 *
 * <p>MongoDB returns the first batch of results with the query, and the driver decodes a batch when
 * it arrives. A query run with no session bound decodes its first batch in the session it opens for
 * itself, which is closed by the time the cursor fetches a later batch, and decoding an association
 * needs a session.
 *
 * <p>Like the first batch's, the instances a later batch holds belong to no session the caller can
 * see: the session they are decoded in closes when the call that fetched them returns.
 */
final class SessionBoundCursor<T> implements MongoCursor<T> {

    private final MongoCursor<T> cursor;
    private final Datastore datastore;

    SessionBoundCursor(MongoCursor<T> cursor, Datastore datastore) {
        this.cursor = cursor;
        this.datastore = datastore;
    }

    @Override
    public boolean hasNext() {
        return fetchesBatch() ? inSession(cursor::hasNext) : cursor.hasNext();
    }

    @Override
    public T next() {
        return fetchesBatch() ? inSession(cursor::next) : cursor.next();
    }

    @Override
    public T tryNext() {
        return fetchesBatch() ? inSession(cursor::tryNext) : cursor.tryNext();
    }

    /**
     * Whether this call fetches a batch with no session bound to decode it in. It fetches one only
     * when none of the current batch is left and the server has more results; once it has sent the
     * last, the cursor answers without it.
     */
    private boolean fetchesBatch() {
        return cursor.available() == 0 && !datastore.hasCurrentSession() && cursor.getServerCursor() != null;
    }

    private <R> R inSession(Supplier<R> call) {
        SessionCallback<R> callback = session -> call.get();
        return DatastoreUtils.execute(datastore, callback);
    }

    @Override
    public int available() {
        return cursor.available();
    }

    @Override
    public ServerCursor getServerCursor() {
        return cursor.getServerCursor();
    }

    @Override
    public ServerAddress getServerAddress() {
        return cursor.getServerAddress();
    }

    @Override
    public void close() {
        cursor.close();
    }
}
