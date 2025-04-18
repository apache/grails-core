/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.neo4j

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.neo4j.driver.AccessMode
import org.neo4j.driver.Driver
import org.neo4j.driver.Session
import org.grails.datastore.mapping.transactions.Transaction
import org.neo4j.driver.SessionConfig
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition

/**
 * Represents a Neo4j transaction
 *
 * @author Stefan Armbruster <stefan@armbruster-it.de>
 * @author Graeme Rocher
 */
@CompileStatic
@Slf4j
class Neo4jTransaction implements Transaction<org.neo4j.driver.Transaction>, Closeable {

    public static final String DEFAULT_NAME = "Neo4j Transaction"

    boolean active = true
    final boolean sessionCreated
    boolean rollbackOnly = false

    Session boltSession
    org.neo4j.driver.Transaction transaction
    TransactionDefinition transactionDefinition

    Neo4jTransaction(Driver boltDriver, TransactionDefinition transactionDefinition = new DefaultTransactionDefinition(), boolean sessionCreated = false) {

        log.debug("TX START: Neo4J beginTx()")
        this.boltSession = boltDriver.session(SessionConfig.builder().withDefaultAccessMode(transactionDefinition.readOnly ? AccessMode.READ : AccessMode.WRITE).build())
        transaction = boltSession.beginTransaction()
        this.transactionDefinition = transactionDefinition
        this.sessionCreated = sessionCreated
    }

    void commit() {
        if(isActive() && !rollbackOnly) {
            log.debug("TX COMMIT: Neo4J commit()")
            transaction.commit()
            close()
        }
    }

    void rollback() {
        if(isActive()) {
            log.debug("TX ROLLBACK: Neo4J rollback()")
            transaction.rollback()
            close()
        }
    }

    void rollbackOnly() {
        if(active) {
            rollbackOnly = true
            log.debug("TX ROLLBACK ONLY: Neo4J rollback()")
            transaction.rollback()
            close()
        }
    }

    @Override
    void close() throws IOException {

        if(active) {
            log.debug("TX CLOSE: Neo4j tx.close()");
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
