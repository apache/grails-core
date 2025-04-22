/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.transactions;

import org.grails.datastore.mapping.core.Session;
import org.springframework.transaction.support.SmartTransactionObject;

/**
 * A transaction object returned when the transaction is created.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class TransactionObject implements SmartTransactionObject {
    private SessionHolder sessionHolder;
    private boolean newSessionHolder;
    private boolean newSession;

    public SessionHolder getSessionHolder() {
        return sessionHolder;
    }

    public Transaction<?> getTransaction() {
        return getSessionHolder().getTransaction();
    }

    /**
     * @deprecated Here for binary compatibility, doesn't actually do anything
     * @param transaction
     */
    @Deprecated
    public void setTransaction(Transaction<?> transaction) {
    }

    public void setSession(Session session) {
        this.sessionHolder = new SessionHolder(session);
        this.newSessionHolder = true;
        this.newSession = true;
    }

    public void setExistingSession(Session session) {
        this.sessionHolder = new SessionHolder(session);
        this.newSessionHolder = true;
        this.newSession = false;
    }

    public void setSessionHolder(SessionHolder sessionHolder) {
        this.sessionHolder = sessionHolder;
        this.newSessionHolder = false;
        this.newSession = false;
    }

    public boolean isNewSessionHolder() {
        return newSessionHolder;
    }

    public boolean isNewSession() {
        return newSession;
    }


    @Override
    public boolean isRollbackOnly() {
        return sessionHolder.isRollbackOnly();
    }

    @Override
    public void flush() {
        sessionHolder.getSession().flush();
    }
}
