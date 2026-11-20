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
package org.springframework.data.mongodb;

import com.mongodb.client.ClientSession;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Binds a GORM-owned {@link ClientSession} into Spring Data MongoDB's thread-bound resources so that
 * a {@code MongoTemplate} executes within the same MongoDB transaction GORM started.
 *
 * <p>This lives in {@code org.springframework.data.mongodb} because Spring Data's
 * {@link MongoResourceHolder} (the resource a {@code MongoTemplate} looks up to discover an active
 * session) is package-private. Only the holder construction needs that access; binding, unbinding
 * and rebinding go through the public {@link TransactionSynchronizationManager} keyed by the
 * {@link MongoDatabaseFactory}, so callers can treat the holder as an opaque object.</p>
 *
 * <p><strong>Invariant:</strong> GORM owns the {@link ClientSession} lifecycle (it commits/aborts and
 * closes it). The holder bound here is effectively read-only for Spring Data — {@code MongoTemplate}
 * only reads the session from it; nothing in this module asks Spring Data to commit or close the
 * session, so there is no double-close.</p>
 *
 * <p><strong>Compatibility:</strong> this depends on the package-private
 * {@code MongoResourceHolder(ClientSession, MongoDatabaseFactory)} constructor of Spring Data MongoDB
 * (verified against the 5.x line shipped with Spring Boot 4). A change to that internal type would
 * break compilation, which the dedicated coupling smoke test surfaces explicitly. Because this is a
 * deliberate split package with Spring Data, this module is supported on the class path only and is
 * not compatible with the Java Platform Module System (JPMS) module path.</p>
 *
 * @since 8.0
 */
public final class GormSpringDataSessionSupport {

    private GormSpringDataSessionSupport() {
    }

    /**
     * Binds the given session to the current thread for the given factory, if nothing is bound yet.
     */
    public static void bindClientSession(MongoDatabaseFactory databaseFactory, ClientSession clientSession) {
        if (!TransactionSynchronizationManager.hasResource(databaseFactory)) {
            MongoResourceHolder holder = new MongoResourceHolder(clientSession, databaseFactory);
            holder.setSynchronizedWithTransaction(true);
            TransactionSynchronizationManager.bindResource(databaseFactory, holder);
        }
    }
}
