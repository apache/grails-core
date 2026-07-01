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
package org.grails.datastore.mapping.mongo;

import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionUsageException;

import org.grails.datastore.mapping.transactions.Transaction;

/**
 * A {@link Transaction} backed by a real MongoDB multi-document transaction on a
 * {@link ClientSession}. Unlike the legacy
 * {@link org.grails.datastore.mapping.transactions.SessionOnlyTransaction} (which only flushes the
 * GORM session), this commits or aborts a server-side transaction so multiple writes are atomic.
 *
 * <p>The {@link ClientSession} is started with an active transaction before this object is
 * constructed; {@link #commit()} flushes the GORM session (a no-op when the
 * {@link org.grails.datastore.mapping.transactions.DatastoreTransactionManager} already flushed)
 * and commits the server transaction, while {@link #rollback()} aborts it. Both close the
 * {@link ClientSession} and detach it from the owning session.</p>
 *
 * <p>Commit is retried on an {@code UnknownTransactionCommitResult} error label. Whole-transaction
 * retry on a {@code TransientTransactionError} is intentionally not handled here: it requires
 * re-executing the transaction body, which the
 * {@link org.grails.datastore.mapping.transactions.DatastoreTransactionManager} — a Spring
 * {@code PlatformTransactionManager} — cannot do (it only begins, commits and rolls back; the
 * application code between those is not re-runnable at this layer). Such retry is left to the
 * application (as Spring Data MongoDB's own {@code MongoTransactionManager} does), since re-running
 * the body would repeat its side effects.</p>
 *
 * @since 8.0
 */
public class MongoTransaction implements Transaction<ClientSession> {

    /**
     * Maximum number of times {@link ClientSession#commitTransaction()} is retried when the server
     * reports an {@code UnknownTransactionCommitResult} (i.e. the commit outcome is unknown and the
     * operation is safe to retry).
     */
    private static final int MAX_COMMIT_RETRIES = 3;

    private static final Logger LOG = LoggerFactory.getLogger(MongoTransaction.class);

    private final AbstractMongoSession session;
    private final ClientSession clientSession;
    private boolean active = true;

    public MongoTransaction(AbstractMongoSession session, ClientSession clientSession) {
        this.session = session;
        this.clientSession = clientSession;
    }

    @Override
    public void commit() {
        if (!active) {
            return;
        }
        boolean committed = false;
        try {
            // Flush pending GORM operations into the active transaction. When driven by the
            // DatastoreTransactionManager the session was already flushed, so this clears nothing
            // and is a no-op; it covers callers that commit the transaction directly.
            session.flush();
            commitWithRetry();
            committed = true;
        } finally {
            if (!committed) {
                // The commit (or the flush before it) failed. Explicitly abort the server transaction
                // rather than relying on close() to do so implicitly, then discard the GORM session's
                // pending operations and first-level cache so a reused session cannot return entities
                // that were never committed.
                if (clientSession.hasActiveTransaction()) {
                    try {
                        clientSession.abortTransaction();
                    }
                    catch (RuntimeException e) {
                        LOG.debug("Error aborting transaction after failed commit: {}", e.getMessage(), e);
                    }
                }
                try {
                    session.clear();
                }
                catch (RuntimeException e) {
                    LOG.debug("Error clearing session after failed transaction commit: {}", e.getMessage(), e);
                }
            }
            close();
        }
    }

    @Override
    public void rollback() {
        if (!active) {
            return;
        }
        try {
            if (clientSession.hasActiveTransaction()) {
                clientSession.abortTransaction();
            }
        } finally {
            close();
        }
    }

    @Override
    public ClientSession getNativeTransaction() {
        return clientSession;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setTimeout(int timeout) {
        if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
            // The server-side transaction is started before the manager applies a timeout, so a
            // per-transaction timeout cannot be honored here; the server's transactionLifetimeLimitSeconds
            // governs the maximum transaction duration. Fail rather than silently ignore the request.
            throw new TransactionUsageException("A per-transaction timeout (" + timeout + "s) is not supported by " +
                    "GORM for MongoDB transactions; the server's transactionLifetimeLimitSeconds governs transaction " +
                    "duration. Remove the timeout from the transaction definition.");
        }
    }

    private void commitWithRetry() {
        int attempts = 0;
        while (true) {
            try {
                clientSession.commitTransaction();
                return;
            }
            catch (MongoException e) {
                if (attempts++ < MAX_COMMIT_RETRIES &&
                        e.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                    continue;
                }
                throw e;
            }
        }
    }

    private void close() {
        active = false;
        try {
            clientSession.close();
        }
        finally {
            session.clearClientSession();
        }
    }
}
