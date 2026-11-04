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

import spock.lang.Specification

import org.grails.datastore.mapping.core.Session

class SessionOnlyTransactionSpec extends Specification {

    Session session = Mock(Session)

    void "a read-write transaction flushes the session on commit"() {
        given:
        def transaction = new SessionOnlyTransaction<Object>(null, session, false)

        when:
        transaction.commit()

        then:
        1 * session.flush()
        !transaction.active
    }

    void "a read-only transaction commits without flushing the session"() {
        given:
        def transaction = new SessionOnlyTransaction<Object>(null, session, true)

        when:
        transaction.commit()

        then:
        0 * session.flush()
        !transaction.active
    }

    void "rollback clears the session without flushing it, whether or not the transaction is read-only"() {
        given:
        def transaction = new SessionOnlyTransaction<Object>(null, session, readOnly)

        when:
        transaction.rollback()

        then:
        1 * session.clear()
        0 * session.flush()
        !transaction.active

        where:
        readOnly << [false, true]
    }

    void "a completed transaction does not flush again on a second commit"() {
        given:
        def transaction = new SessionOnlyTransaction<Object>(null, session, false)
        transaction.commit()

        when:
        transaction.commit()

        then:
        0 * session.flush()
    }

    @SuppressWarnings("deprecation")
    void "the deprecated constructor creates a read-write transaction"() {
        given:
        def transaction = new SessionOnlyTransaction<Object>(null, session)

        when:
        transaction.commit()

        then:
        1 * session.flush()
    }
}
