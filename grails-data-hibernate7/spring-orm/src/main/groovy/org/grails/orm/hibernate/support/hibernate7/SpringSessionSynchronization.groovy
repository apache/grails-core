/*
 * Copyright 2002-present the original author or authors.
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
package org.grails.orm.hibernate.support.hibernate7

import groovy.transform.CompileStatic
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionImplementor
import org.springframework.core.Ordered
import org.springframework.dao.DataAccessException
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Callback for resource cleanup at the end of a Spring-managed transaction
 * for a pre-bound Hibernate Session.
 *
 * @author Juergen Hoeller
 * @since 4.2
 */
@CompileStatic
class SpringSessionSynchronization implements TransactionSynchronization, Ordered {

    private final SessionHolder sessionHolder

    private final SessionFactory sessionFactory

    private final boolean newSession

    private boolean holderActive = true

    SpringSessionSynchronization(SessionHolder sessionHolder, SessionFactory sessionFactory) {
        this(sessionHolder, sessionFactory, false)
    }

    SpringSessionSynchronization(SessionHolder sessionHolder, SessionFactory sessionFactory, boolean newSession) {
        this.sessionHolder = sessionHolder
        this.sessionFactory = sessionFactory
        this.newSession = newSession
    }

    private Session getCurrentSession() {
        return this.sessionHolder.getSession()
    }

    @Override
    int getOrder() {
        return SessionFactoryUtils.SESSION_SYNCHRONIZATION_ORDER
    }

    @Override
    void suspend() {
        if (this.holderActive) {
            TransactionSynchronizationManager.unbindResource(this.sessionFactory)
            // Eagerly disconnect the Session here, to make release mode "on_close" work on JBoss.
            Session session = getCurrentSession()
            if (session instanceof SessionImplementor) {
                ((SessionImplementor) session).getJdbcCoordinator().getLogicalConnection().manualDisconnect()
            }
        }
    }

    @Override
    void resume() {
        if (this.holderActive) {
            TransactionSynchronizationManager.bindResource(this.sessionFactory, this.sessionHolder)
        }
    }

    @Override
    void flush() {
        SessionFactoryUtils.flush(getCurrentSession(), false)
    }

    @Override
    void beforeCommit(boolean readOnly) throws DataAccessException {
        if (!readOnly) {
            Session session = getCurrentSession()
            // Read-write transaction -> flush the Hibernate Session.
            // Further check: only flush when not FlushMode.MANUAL.
            if (session.getHibernateFlushMode() != FlushMode.MANUAL) {
                SessionFactoryUtils.flush(getCurrentSession(), true)
            }
        }
    }

    @Override
    void beforeCompletion() {
        try {
            Session session = this.sessionHolder.getSession()
            if (this.sessionHolder.getPreviousFlushMode() != null) {
                // In case of pre-bound Session, restore previous flush mode.
                session.setHibernateFlushMode(this.sessionHolder.getPreviousFlushMode())
            }
            // Eagerly disconnect the Session here, to make release mode "on_close" work nicely.
            if (session instanceof SessionImplementor) {
                ((SessionImplementor) session).getJdbcCoordinator().getLogicalConnection().manualDisconnect()
            }
        } finally {
            // Unbind at this point if it's a new Session...
            if (this.newSession) {
                TransactionSynchronizationManager.unbindResource(this.sessionFactory)
                this.holderActive = false
            }
        }
    }

    @Override
    void afterCommit() {
        // no-op
    }

    @Override
    void afterCompletion(int status) {
        try {
            if (status != STATUS_COMMITTED) {
                // Clear all pending inserts/updates/deletes in the Session.
                // Necessary for pre-bound Sessions, to avoid inconsistent state.
                this.sessionHolder.getSession().clear()
            }
        } finally {
            this.sessionHolder.setSynchronizedWithTransaction(false)
            // Call close() at this point if it's a new Session...
            if (this.newSession) {
                SessionFactoryUtils.closeSession(this.sessionHolder.getSession())
            }
        }
    }

}
