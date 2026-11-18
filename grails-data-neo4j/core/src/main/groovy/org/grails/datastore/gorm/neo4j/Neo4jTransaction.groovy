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
package org.grails.datastore.gorm.neo4j

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.neo4j.driver.AccessMode
import org.neo4j.driver.Driver
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition

import org.grails.datastore.mapping.transactions.Transaction

/**
 * Represents a Neo4j transaction
 *
 * @author Stefan Armbruster <stefan@armbruster-it.de>
 * @author Graeme Rocher
 */
@CompileStatic
@Slf4j
class Neo4jTransaction implements Transaction<org.neo4j.driver.Transaction>, Closeable {

    public static final String DEFAULT_NAME = 'Neo4j Transaction'

    boolean active = true
    final boolean sessionCreated
    boolean rollbackOnly = false

    Session boltSession
    org.neo4j.driver.Transaction transaction
    TransactionDefinition transactionDefinition

    Neo4jTransaction(Driver boltDriver, TransactionDefinition transactionDefinition = new DefaultTransactionDefinition(), boolean sessionCreated = false) {

        log.debug('TX START: Neo4J beginTx()')
        this.boltSession = boltDriver.session(SessionConfig.builder().withDefaultAccessMode(transactionDefinition.readOnly ? AccessMode.READ : AccessMode.WRITE).build())
        transaction = boltSession.beginTransaction()
        this.transactionDefinition = transactionDefinition
        this.sessionCreated = sessionCreated
    }

    void commit() {
        if (!isActive()) {
            return
        }
        if (rollbackOnly) {
            // Left open, the native transaction would hold its writes until the session closed
            rollback()
            return
        }
        log.debug('TX COMMIT: Neo4J commit()')
        transaction.commit()
        close()
    }

    void rollback() {
        if (isActive()) {
            log.debug('TX ROLLBACK: Neo4J rollback()')
            transaction.rollback()
            close()
        }
    }

    /**
     * Marks the transaction rollback-only, as a transaction that joined it and failed does. It stays
     * open, so the surrounding code's later writes run in it and are rolled back with it. Rolled back
     * here, the session would have no transaction left, and would run those writes on its own
     * connection, which commits each as it runs.
     */
    void rollbackOnly() {
        if (active) {
            log.debug('TX ROLLBACK ONLY: Neo4J transaction marked rollback-only')
            rollbackOnly = true
        }
    }

    @Override
    void close() throws IOException {

        if (active) {
            log.debug('TX CLOSE: Neo4j tx.close()')
            transaction.close()
            boltSession.close()
            active = false
        }
    }

    org.neo4j.driver.Transaction getNativeTransaction() {
        transaction
    }

    boolean isActive() {
        active
    }

    void setTimeout(int timeout) {
        throw new UnsupportedOperationException()
    }
}
