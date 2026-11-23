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
package org.grails.plugin.hibernate.support;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ui.ModelMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;

import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.orm.hibernate.AbstractHibernateDatastore;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.support.hibernate5.SessionFactoryUtils;
import org.grails.orm.hibernate.support.hibernate5.SessionHolder;
import org.grails.orm.hibernate.support.hibernate5.support.OpenSessionInViewInterceptor;

/**
 * Extends the default Spring OSIV to support multiple datasources.
 * <p>
 * The default datasource's SessionFactory is managed by the parent class.
 * Additional (non-default) datasource SessionFactories are managed by this
 * subclass, which opens and closes sessions for each one alongside the
 * default session. As the parent class does for the default session, a request
 * that finds a session already bound, such as an include within a request that
 * opened one, uses that session and leaves it to the one that opened it. A
 * request that goes asynchronous takes the sessions it opened off the thread, so
 * that they do not stay bound to it for the next request that thread serves, and
 * they are closed when the asynchronous request completes.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public class GrailsOpenSessionInViewInterceptor extends OpenSessionInViewInterceptor {

    /** The value of {@code GrailsApplicationAttributes.ASYNC_STARTED}, which is in a module this plugin does not use. */
    private static final String GRAILS_ASYNC_STARTED = "org.grails.ASYNC_STARTED";

    protected FlushMode hibernateFlushMode = FlushMode.MANUAL;

    private final List<AdditionalSessionFactoryConfig> additionalSessionFactories = new ArrayList<>();

    /**
     * Holds configuration for an additional (non-default) SessionFactory
     * that needs OSIV session management.
     */
    private static class AdditionalSessionFactoryConfig {
        final String connectionName;
        final SessionFactory sessionFactory;
        final FlushMode flushMode;

        AdditionalSessionFactoryConfig(String connectionName, SessionFactory sessionFactory, FlushMode flushMode) {
            this.connectionName = connectionName;
            this.sessionFactory = sessionFactory;
            this.flushMode = flushMode;
        }
    }

    @Override
    protected Session openSession() throws DataAccessResourceFailureException {
        Session session = super.openSession();
        applyFlushMode(session);
        return session;
    }

    protected void applyFlushMode(Session session) {
        session.setHibernateFlushMode(hibernateFlushMode);
    }

    @Override
    public void preHandle(WebRequest request) throws DataAccessException {
        super.preHandle(request);

        for (AdditionalSessionFactoryConfig config : additionalSessionFactories) {
            SessionFactory sf = config.sessionFactory;
            if (TransactionSynchronizationManager.hasResource(sf)) {
                // a session is already open for this request: use it, and leave it to the one that opened it
                incrementParticipateCount(request, sf);
                continue;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Opening additional Hibernate Session for datasource '" + config.connectionName + "' in OpenSessionInViewInterceptor");
            }
            Session session = sf.openSession();
            session.setHibernateFlushMode(config.flushMode);
            SessionHolder sessionHolder = new SessionHolder(session);
            TransactionSynchronizationManager.bindResource(sf, sessionHolder);
        }
    }

    @Override
    public void postHandle(WebRequest request, ModelMap model) throws DataAccessException {
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(getSessionFactory());
        Session session = sessionHolder != null ? sessionHolder.getSession() : null;
        try {
            super.postHandle(request, model);
            FlushMode flushMode = session != null ? session.getHibernateFlushMode() : null;
            boolean isNotManual = flushMode != FlushMode.MANUAL && flushMode != FlushMode.COMMIT;
            if (session != null && isNotManual) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Eagerly flushing Hibernate session");
                }
                session.flush();
            }
        }
        finally {
            if (session != null) {
                session.setHibernateFlushMode(FlushMode.MANUAL);
            }
        }

        RuntimeException firstFlushException = null;
        for (AdditionalSessionFactoryConfig config : additionalSessionFactories) {
            SessionFactory sf = config.sessionFactory;
            SessionHolder additionalHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sf);
            if (additionalHolder != null) {
                Session additionalSession = additionalHolder.getSession();
                try {
                    try {
                        FlushMode additionalFlushMode = additionalSession.getHibernateFlushMode();
                        boolean additionalIsNotManual = additionalFlushMode != FlushMode.MANUAL && additionalFlushMode != FlushMode.COMMIT;
                        if (additionalIsNotManual) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Eagerly flushing additional Hibernate session for datasource '" + config.connectionName + "'");
                            }
                            additionalSession.flush();
                        }
                    }
                    catch (RuntimeException ex) {
                        if (firstFlushException == null) {
                            firstFlushException = ex;
                        }
                        else {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Additional flush exception for datasource '" + config.connectionName + "'", ex);
                            }
                            firstFlushException.addSuppressed(ex);
                        }
                    }
                }
                finally {
                    additionalSession.setHibernateFlushMode(FlushMode.MANUAL);
                }
            }
        }
        if (firstFlushException != null) {
            throw firstFlushException;
        }
    }

    @Override
    public void afterCompletion(WebRequest request, Exception ex) throws DataAccessException {
        try {
            closeAdditionalSessions(request, false);
        }
        finally {
            super.afterCompletion(request, ex);
        }
    }

    /**
     * Called instead of {@link #postHandle} and {@link #afterCompletion} on the thread that started asynchronous
     * handling. The sessions this interceptor opened for the other datasources are unbound here, so that the thread
     * goes back to its pool without them, and closed when the asynchronous request completes, whether a promise,
     * {@code startAsync()}, a {@code Callable} or a {@code DeferredResult} started it. Each is flushed first
     * when {@link #postHandle} would flush it and Hibernate allows the flush: inside a transaction, or with
     * {@code hibernate.allow_update_outside_transaction}.
     *
     * <p>The parent class only unbinds the default session, and leaves closing it to the interceptors it registers for
     * a {@code Callable} or {@code DeferredResult}, which close it in the dispatch that the result starts. A promise or
     * {@code startAsync()} in a Grails controller starts asynchronous handling without them, so the default session
     * this interceptor opened is unbound here too, and closed when the asynchronous request completes. So is the
     * default session the parent class unbinds, unless it is closed by then: a request can complete without that
     * dispatch, such as one that a task the action started, and did not return, completes.
     */
    @Override
    public void afterConcurrentHandlingStarted(WebRequest request) {
        List<Session> released = new ArrayList<>();
        try {
            releaseAdditionalSessions(request, true, released);
        }
        finally {
            try {
                if (isGrailsAsync(request) && !isParticipating(request) &&
                        TransactionSynchronizationManager.hasResource(getSessionFactory())) {
                    SessionHolder sessionHolder =
                            (SessionHolder) TransactionSynchronizationManager.unbindResource(getSessionFactory());
                    released.add(sessionHolder.getSession());
                }
                else {
                    SessionHolder sessionHolder = isParticipating(request) ? null :
                            (SessionHolder) TransactionSynchronizationManager.getResource(getSessionFactory());
                    super.afterConcurrentHandlingStarted(request);
                    if (sessionHolder != null) {
                        closeIfLeftOpenWhenAsynchronousRequestCompletes(request, sessionHolder.getSession());
                    }
                }
            }
            finally {
                closeWhenAsynchronousRequestCompletes(request, released);
            }
        }
    }

    /**
     * Closes the default session that the parent class took off the thread when the asynchronous request completes,
     * unless it is closed by then. Without an asynchronous context to listen to, it is left to the parent class.
     */
    private void closeIfLeftOpenWhenAsynchronousRequestCompletes(WebRequest request, Session session) {
        AsynchronousCompletion.onComplete(request, () -> {
            if (session.isOpen()) {
                closeSessions(List.of(session));
            }
        });
    }

    /**
     * Closes the sessions taken off the thread when the asynchronous request completes, so that the work the request
     * continues with, such as a task that lazily loads an association of an entity the action loaded, can still use
     * them. Without an asynchronous context to listen to, they are closed now.
     */
    private void closeWhenAsynchronousRequestCompletes(WebRequest request, List<Session> sessions) {
        if (sessions.isEmpty()) {
            return;
        }
        if (AsynchronousCompletion.onComplete(request, () -> closeSessions(sessions))) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing " + sessions.size() + " Hibernate Session(s) in OpenSessionInViewInterceptor when the asynchronous request completes");
            }
            return;
        }
        closeSessions(sessions);
    }

    private void closeSessions(List<Session> sessions) {
        for (Session session : sessions) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing Hibernate Session in OpenSessionInViewInterceptor after asynchronous handling");
            }
            try {
                SessionFactoryUtils.closeSession(session);
            }
            catch (RuntimeException closeEx) {
                logger.error("Unexpected exception on closing Hibernate Session after asynchronous handling", closeEx);
            }
        }
    }

    /**
     * Uses the Servlet API, which this module only needs where a request goes asynchronous.
     */
    private static final class AsynchronousCompletion {

        /**
         * @return whether the action runs when the asynchronous request of the given web request completes, including
         * after a timeout or an error
         */
        static boolean onComplete(WebRequest request, Runnable action) {
            if (!(request instanceof NativeWebRequest nativeRequest)) {
                return false;
            }
            HttpServletRequest servletRequest = nativeRequest.getNativeRequest(HttpServletRequest.class);
            if (servletRequest == null || !servletRequest.isAsyncStarted()) {
                return false;
            }
            try {
                servletRequest.getAsyncContext().addListener(new AsyncListener() {

                    @Override
                    public void onComplete(AsyncEvent event) {
                        action.run();
                    }

                    @Override
                    public void onTimeout(AsyncEvent event) {
                    }

                    @Override
                    public void onError(AsyncEvent event) {
                    }

                    @Override
                    public void onStartAsync(AsyncEvent event) {
                        event.getAsyncContext().addListener(this);
                    }
                });
                return true;
            }
            catch (IllegalStateException ex) {
                return false;
            }
        }
    }

    /**
     * @return whether Grails started asynchronous handling, for a promise or {@code startAsync()}: both set
     * {@code GrailsApplicationAttributes.ASYNC_STARTED}
     */
    private static boolean isGrailsAsync(WebRequest request) {
        return request.getAttribute(GRAILS_ASYNC_STARTED, WebRequest.SCOPE_REQUEST) != null;
    }

    /**
     * @return whether the default session was already bound when the request started, so that it belongs to
     * whoever bound it
     */
    private boolean isParticipating(WebRequest request) {
        return request.getAttribute(getParticipateAttributeName(), WebRequest.SCOPE_REQUEST) != null;
    }

    private void closeAdditionalSessions(WebRequest request, boolean flushFirst) {
        releaseAdditionalSessions(request, flushFirst, null);
    }

    /**
     * Unbinds the sessions this interceptor opened for the other datasources, and closes them, or adds them to the
     * given list for the caller to close.
     */
    private void releaseAdditionalSessions(WebRequest request, boolean flushFirst, List<Session> released) {
        Throwable firstFlushFailure = null;
        for (int i = additionalSessionFactories.size() - 1; i >= 0; i--) {
            AdditionalSessionFactoryConfig config = additionalSessionFactories.get(i);
            SessionFactory sf = config.sessionFactory;
            if (decrementParticipateCount(request, sf)) {
                continue;
            }
            SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sf);
            if (sessionHolder != null) {
                Session session = sessionHolder.getSession();
                try {
                    if (flushFirst && isFlushAllowed(session)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Flushing additional Hibernate session for datasource '" + config.connectionName + "' as asynchronous handling starts");
                        }
                        session.flush();
                    }
                }
                catch (Throwable flushFailure) {
                    // an Error too, such as a failed assert in a domain's beforeUpdate: every session is released first
                    if (firstFlushFailure == null) {
                        firstFlushFailure = flushFailure;
                    }
                    else {
                        firstFlushFailure.addSuppressed(flushFailure);
                    }
                }
                TransactionSynchronizationManager.unbindResource(sf);
                if (released != null) {
                    released.add(session);
                    continue;
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Closing additional Hibernate Session for datasource '" + config.connectionName + "' in OpenSessionInViewInterceptor");
                }
                try {
                    SessionFactoryUtils.closeSession(session);
                }
                catch (RuntimeException closeEx) {
                    logger.error("Unexpected exception on closing additional Hibernate Session for datasource '" + config.connectionName + "'", closeEx);
                }
            }
        }
        if (firstFlushFailure != null) {
            ReflectionUtils.rethrowRuntimeException(firstFlushFailure);
        }
    }

    /**
     * @return whether {@link #postHandle} would flush the session, and Hibernate would let it: a session it would not
     * flush (MANUAL or COMMIT) is not flushed here either, and one outside a transaction only when
     * {@code hibernate.allow_update_outside_transaction} is set, since Hibernate refuses that flush otherwise
     */
    private static boolean isFlushAllowed(Session session) {
        FlushMode flushMode = session.getHibernateFlushMode();
        if (flushMode == FlushMode.MANUAL || flushMode == FlushMode.COMMIT) {
            return false;
        }
        return ((SharedSessionContractImplementor) session).isTransactionInProgress() ||
                session.getSessionFactory().getSessionFactoryOptions().isAllowOutOfTransactionUpdateOperations();
    }

    private static void incrementParticipateCount(WebRequest request, SessionFactory sf) {
        String participateAttributeName = participateAttributeName(sf);
        Integer count = (Integer) request.getAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
        request.setAttribute(participateAttributeName, count != null ? count + 1 : 1, WebRequest.SCOPE_REQUEST);
    }

    /**
     * @return {@code true} when this request used a session of the given factory that was already open, which is
     * then left as it is
     */
    private static boolean decrementParticipateCount(WebRequest request, SessionFactory sf) {
        String participateAttributeName = participateAttributeName(sf);
        Integer count = (Integer) request.getAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
        if (count == null) {
            return false;
        }
        if (count > 1) {
            request.setAttribute(participateAttributeName, count - 1, WebRequest.SCOPE_REQUEST);
        }
        else {
            request.removeAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
        }
        return true;
    }

    private static String participateAttributeName(SessionFactory sf) {
        return sf.toString() + PARTICIPATE_SUFFIX;
    }

    public void setHibernateDatastore(AbstractHibernateDatastore hibernateDatastore) {
        String defaultFlushModeName = hibernateDatastore.getDefaultFlushModeName();
        if (hibernateDatastore.isOsivReadOnly()) {
            this.hibernateFlushMode = FlushMode.MANUAL;
        }
        else {
            this.hibernateFlushMode = FlushMode.valueOf(defaultFlushModeName);
        }
        setSessionFactory(hibernateDatastore.getSessionFactory());

        if (hibernateDatastore instanceof HibernateDatastore) {
            HibernateDatastore hibernateDs = (HibernateDatastore) hibernateDatastore;
            for (ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource : hibernateDs.getConnectionSources().getAllConnectionSources()) {
                String connectionName = connectionSource.getName();
                if (!ConnectionSource.DEFAULT.equals(connectionName)) {
                    AbstractHibernateDatastore childDatastore = hibernateDs.getDatastoreForConnection(connectionName);
                    FlushMode childFlushMode;
                    if (childDatastore.isOsivReadOnly()) {
                        childFlushMode = FlushMode.MANUAL;
                    }
                    else {
                        childFlushMode = FlushMode.valueOf(childDatastore.getDefaultFlushModeName());
                    }
                    additionalSessionFactories.add(
                            new AdditionalSessionFactoryConfig(connectionName, childDatastore.getSessionFactory(), childFlushMode)
                    );
                }
            }
        }
    }
}
