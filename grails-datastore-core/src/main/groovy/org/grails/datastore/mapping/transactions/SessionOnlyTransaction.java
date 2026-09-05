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
package org.grails.datastore.mapping.transactions;

import org.grails.datastore.mapping.core.Session;

/**
 * <p>An implementation that provides Session only transaction management. Essentially when {@link #rollback()} is called
 * the {@link Session}'s clear() method is called and when {@link #commit()} is called the flush() method is called.
 * </p>
 *
 * <p>
 * A read-only transaction commits without flushing. It has no pending operations of its own, so the
 * only thing a flush could write is whatever the surrounding session already had queued.
 * </p>
 *
 * <p>
 * No other resource level transaction management is provided.
 * </p>
 *
 * @author graemerocher
 *
 * @param <T>
 */
public class SessionOnlyTransaction<T> implements Transaction<T> {

    private T nativeInterface;
    private Session session;
    private boolean readOnly;
    private boolean active = true;

    /**
     * Creates a read-write transaction, which flushes the session on commit.
     *
     * @param nativeInterface the native transaction resource
     * @param session the session the transaction is bound to
     * @deprecated use {@link #SessionOnlyTransaction(Object, Session, boolean)}, which states
     * whether the transaction is read-only rather than assuming it is not
     */
    @Deprecated(since = "8.0", forRemoval = true)
    public SessionOnlyTransaction(T nativeInterface, Session session) {
        this(nativeInterface, session, false);
    }

    public SessionOnlyTransaction(T nativeInterface, Session session, boolean readOnly) {
        this.nativeInterface = nativeInterface;
        this.session = session;
        this.readOnly = readOnly;
    }

    public void commit() {
        if (active) {
            try {
                if (!readOnly) {
                    session.flush();
                }
            }
            finally {
                active = false;
            }
        }
    }

    public void rollback() {
        if (active) {
            try {
                session.clear();
            }
            finally {
                active = false;
            }
        }
    }

    public T getNativeTransaction() {
        return nativeInterface;
    }

    public boolean isActive() {
        return active;
    }

    public void setTimeout(int timeout) {
        // do nothing
    }
}
