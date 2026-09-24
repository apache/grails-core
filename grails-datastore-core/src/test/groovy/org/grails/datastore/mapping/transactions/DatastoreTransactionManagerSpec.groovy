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
package org.grails.datastore.mapping.transactions

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.persistence.FlushModeType
import org.slf4j.LoggerFactory
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session

/**
 * Drives {@link DatastoreTransactionManager} through {@link TransactionTemplate}, the way GORM's
 * {@code @Transactional} and {@code withTransaction} do, and checks what a commit does to the
 * session for read-write and read-only transactions.
 */
class DatastoreTransactionManagerSpec extends Specification {

    Datastore datastore = Mock(Datastore)
    Session session = Mock(Session)
    Transaction transaction = Mock(Transaction)

    DatastoreTransactionManager transactionManager = new DatastoreTransactionManager(datastore: datastore)

    Logger managerLogger = LoggerFactory.getLogger(DatastoreTransactionManager) as Logger
    ListAppender<ILoggingEvent> appender = new ListAppender<>()

    void setup() {
        datastore.connect() >> session
        session.getDatastore() >> datastore
        session.beginTransaction(_ as TransactionDefinition) >> transaction
        session.hasTransaction() >> true
        session.getTransaction() >> transaction
        transaction.isActive() >> true

        appender.start()
        managerLogger.addAppender(appender)
    }

    void cleanup() {
        managerLogger.detachAppender(appender)
    }

    void "a read-write commit flushes the session and does not warn"() {
        given:
        session.hasPendingOperations() >> true

        when:
        new TransactionTemplate(transactionManager).execute {}

        then:
        1 * session.flush()
        1 * transaction.commit()
        0 * session.setFlushMode(_)
        readOnlyWarnings.empty
    }

    void "a read-only commit with pending operations leaves them unflushed and warns"() {
        given:
        session.hasPendingOperations() >> true

        when:
        readOnlyTemplate.execute {}

        then:
        0 * session.flush()
        1 * session.setFlushMode(FlushModeType.COMMIT)
        1 * transaction.commit()

        and: "the warning names the session and says what to do instead"
        readOnlyWarnings.size() == 1
        with(readOnlyWarnings.first().formattedMessage) {
            it.contains(session.toString())
            it.contains('save(flush: true)')
            it.contains('read-write transaction')
        }
    }

    void "a read-only commit with nothing pending is silent"() {
        given:
        session.hasPendingOperations() >> false

        when:
        readOnlyTemplate.execute {}

        then:
        0 * session.flush()
        1 * transaction.commit()
        readOnlyWarnings.empty
    }

    void "a read-only rollback clears the session without warning about the operations it discards"() {
        given:
        session.hasPendingOperations() >> true

        when:
        readOnlyTemplate.execute { status -> status.setRollbackOnly() }

        then:
        0 * session.flush()
        1 * transaction.rollback()
        1 * session.clear()
        readOnlyWarnings.empty
    }

    private TransactionTemplate getReadOnlyTemplate() {
        new TransactionTemplate(transactionManager).tap { readOnly = true }
    }

    private List<ILoggingEvent> getReadOnlyWarnings() {
        appender.list.findAll { ILoggingEvent event ->
            event.level == Level.WARN && event.formattedMessage.contains('Read-only transaction')
        }
    }
}
