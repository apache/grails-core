/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.transactions

import groovy.transform.CompileStatic
import org.springframework.transaction.support.SmartTransactionObject

import org.grails.datastore.mapping.core.Session

/**
 * A transaction object returned when the transaction is created.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class TransactionObject implements SmartTransactionObject {

    private SessionHolder sessionHolder
    private boolean newSessionHolder
    private boolean newSession

    SessionHolder getSessionHolder() {
        return sessionHolder
    }

    Transaction<?> getTransaction() {
        return getSessionHolder().getTransaction()
    }

    /**
     * @deprecated Here for binary compatibility, doesn't actually do anything
     * @param transaction
     */
    @Deprecated
    void setTransaction(Transaction<?> transaction) {
    }

    void setSession(Session session) {
        this.sessionHolder = new SessionHolder(session)
        this.newSessionHolder = true
        this.newSession = true
    }

    void setExistingSession(Session session) {
        this.sessionHolder = new SessionHolder(session)
        this.newSessionHolder = true
        this.newSession = false
    }

    void setSessionHolder(SessionHolder sessionHolder) {
        this.sessionHolder = sessionHolder
        this.newSessionHolder = false
        this.newSession = false
    }

    boolean isNewSessionHolder() {
        return newSessionHolder
    }

    boolean isNewSession() {
        return newSession
    }

    @Override
    boolean isRollbackOnly() {
        return sessionHolder.isRollbackOnly()
    }

    @Override
    void flush() {
        sessionHolder.getSession().flush()
    }

}
