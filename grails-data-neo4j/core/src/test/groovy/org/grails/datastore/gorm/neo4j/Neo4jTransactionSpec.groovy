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

import org.neo4j.driver.Driver
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.Transaction
import spock.lang.Specification

class Neo4jTransactionSpec extends Specification {

    Transaction nativeTransaction = Mock(Transaction)
    Session boltSession = Mock(Session) {
        beginTransaction() >> nativeTransaction
    }
    Driver driver = Mock(Driver) {
        session(_ as SessionConfig) >> boltSession
    }

    void 'marking a transaction rollback-only leaves it open for the surrounding code\'s later writes'() {
        given:
        Neo4jTransaction transaction = new Neo4jTransaction(driver)

        when:
        transaction.rollbackOnly()

        then:
        transaction.active
        transaction.rollbackOnly
        0 * nativeTransaction.rollback()
        0 * nativeTransaction.close()
    }

    void 'a rollback-only transaction rolls back when committed, rather than staying open'() {
        given:
        Neo4jTransaction transaction = new Neo4jTransaction(driver)
        transaction.rollbackOnly()

        when:
        transaction.commit()

        then:
        0 * nativeTransaction.commit()
        1 * nativeTransaction.rollback()
        1 * boltSession.close()
        !transaction.active
    }

    void 'a transaction that is not rollback-only commits'() {
        given:
        Neo4jTransaction transaction = new Neo4jTransaction(driver)

        when:
        transaction.commit()

        then:
        1 * nativeTransaction.commit()
        0 * nativeTransaction.rollback()
        !transaction.active
    }
}
