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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.request.WebRequest;

import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.support.hibernate7.SessionFactoryUtils;
import org.grails.orm.hibernate.support.hibernate7.SessionHolder;
import org.grails.orm.hibernate.support.hibernate7.support.OpenSessionInViewInterceptor;

/**
 * Extends the default Spring OSIV to support multiple datasources.
 * <p>
 * The default datasource's SessionFactory is managed by the parent class.
 * Additional (non-default) datasource SessionFactories are managed by this
 * subclass, which opens and closes sessions for each one alongside the
 * default session. As the parent class does for the default session, a request
 * that finds a session already bound, such as an include within a request that
 * opened one, uses that session and leaves it to the one that opened it, and a
 * request that goes asynchronous closes the sessions it opened, so that they do
 * not stay bound to the thread for the next request that thread serves. Which
 * sessions a request found bound is recorded by the identity of their factory,
 * and for the default factory by this interceptor, not by how the factory prints:
 * {@code Object.toString()} is not unique, and a proxy prints as its target.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public class GrailsOpenSessionInViewInterceptor extends OpenSessionInViewInterceptor {

    /** The value of {@code GrailsApplicationAttributes.ASYNC_STARTED}, which is in a module this plugin does not use. */
    private static final String GRAILS_ASYNC_STARTED = "org.grails.ASYNC_STARTED";

    /**
     * The request attribute that holds, for each factory of another datasource whose session the request found bound,
     * how many times it did, keyed by the identity of the factory.
     */
    private static final String PARTICIPATE_COUNTS =
            GrailsOpenSessionInViewInterceptor.class.getName() + ".PARTICIPATE_COUNTS";

    private static final AtomicLong INSTANCE_COUNT = new AtomicLong();

    /** Unique to this interceptor, where the parent class uses how its factory prints. */
    private final String participateAttributeName =
            GrailsOpenSessionInViewInterceptor.class.getName() + '.' + INSTANCE_COUNT.incrementAndGet() + PARTICIPATE_SUFFIX;

    protected FlushMode hibernateFlushMode = FlushMode.MANUAL;

    private final List<AdditionalSessionFactoryConfig> additionalSessionFactories = new ArrayList<>();

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

    /**
     * Opens the default session as the parent class does, setting its flush mode to MANUAL, then applies
     * {@link #applyFlushMode its flush mode}. A {@link HibernateException} from opening it or setting MANUAL is
     * wrapped, as the parent class does; a failure of {@link #applyFlushMode} is thrown on as it is.
     *
     * <p>The session is not bound yet, so when a step fails, it is closed here, before the failure is thrown on: any
     * failure, as code written in Groovy can throw a checked exception it does not declare. A failure to close it is
     * attached to the failure that is thrown. A session bound for the factory while this one was set up is left as
     * it is, and this one is closed with an {@link IllegalStateException}, instead of being bound over it.
     */
    @Override
    protected Session openSession() throws DataAccessResourceFailureException {
        SessionFactory sf = getSessionFactory();
        Assert.state(sf != null, "No SessionFactory set");
        Session session;
        try {
            session = sf.openSession();
        } catch (HibernateException ex) {
            throw new DataAccessResourceFailureException("Could not open Hibernate Session", ex);
        }
        try {
            session.setHibernateFlushMode(FlushMode.MANUAL);
        } catch (HibernateException ex) {
            DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Could not open Hibernate Session", ex);
            // not bound yet, so afterCompletion would not find it
            closeSession(session, failure);
            throw failure;
        } catch (Throwable ex) {
            closeSession(session, ex);
            throw ex;
        }
        try {
            applyFlushMode(session);
            checkNotBoundDuringSetup(sf, "the default datasource");
        } catch (Throwable ex) {
            closeSession(session, ex);
            throw ex;
        }
        return session;
    }

    /**
     * Spring's {@code bindResource} replaces a holder it finds bound before it throws, so a holder bound for the
     * factory while a new session was set up for it, by code that setup ran, is looked for before the new one is
     * bound, and left as it is.
     */
    private static void checkNotBoundDuringSetup(SessionFactory sf, String datasource) {
        if (TransactionSynchronizationManager.hasResource(sf)) {
            throw new IllegalStateException("A Hibernate Session was bound for " + datasource +
                    " while a new one was opened for it: the new one is closed, and the bound one is left as it is");
        }
    }

    protected void applyFlushMode(Session session) {
        session.setHibernateFlushMode(hibernateFlushMode);
    }

    @Override
    public void preHandle(WebRequest request) throws DataAccessException {
        super.preHandle(request);

        int acquired = 0;
        try {
            for (AdditionalSessionFactoryConfig config : additionalSessionFactories) {
                SessionFactory sf = config.sessionFactory;
                if (TransactionSynchronizationManager.hasResource(sf)) {
                    // a session is already open for this request: use it, and leave it to the one that opened it
                    incrementParticipateCount(request, sf);
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Opening additional Hibernate Session for datasource '" + config.connectionName + "' in OpenSessionInViewInterceptor");
                    }
                    Session session = sf.openSession();
                    try {
                        session.setHibernateFlushMode(config.flushMode);
                        checkNotBoundDuringSetup(sf, "datasource '" + config.connectionName + "'");
                        TransactionSynchronizationManager.bindResource(sf, new SessionHolder(session));
                    } catch (Throwable ex) {
                        // not bound yet, so the release below would not find it
                        closeSession(session, ex);
                        throw ex;
                    }
                }
                acquired++;
            }
        } catch (Throwable ex) {
            // any failure: code written in Groovy can throw a checked exception it does not declare
            releaseAfterFailedPreHandle(request, acquired, ex);
            throw ex;
        }
    }

    /**
     * Releases what a {@link #preHandle} that failed partway acquired, since Spring calls {@link #afterCompletion}
     * only on interceptors whose preHandle completed: the sessions of the other datasources it opened or joined, then
     * the default session. A failure to release one, closing a session too, is attached to the original failure,
     * which preHandle rethrows.
     */
    private void releaseAfterFailedPreHandle(WebRequest request, int acquired, Throwable failure) {
        try {
            closeAdditionalSessions(request, acquired, false, failure);
        } catch (Throwable releaseEx) {
            attach(failure, releaseEx);
        }
        try {
            releaseDefaultSession(request, failure);
        } catch (Throwable releaseEx) {
            attach(failure, releaseEx);
        }
    }

    /**
     * Releases the default session as the parent's {@link #afterCompletion} does, except that a failure to close it is
     * attached to {@code failure} rather than logged: counts it down when the request found it bound, and otherwise
     * unbinds and closes it.
     */
    private void releaseDefaultSession(WebRequest request, Throwable failure) {
        String participateAttributeName = getParticipateAttributeName();
        Integer count = (Integer) request.getAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
        if (count != null) {
            if (count > 1) {
                request.setAttribute(participateAttributeName, count - 1, WebRequest.SCOPE_REQUEST);
            } else {
                request.removeAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
            }
            return;
        }
        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.unbindResourceIfPossible(getSessionFactory());
        if (sessionHolder != null) {
            closeSession(sessionHolder.getSession(), failure);
        }
    }

    /**
     * Closes a session this interceptor opened. A failure to close it is attached to {@code failure}, the failure the
     * caller is about to throw, so that it is not lost; without one, it is logged, as the parent class does.
     */
    private void closeSession(Session session, Throwable failure) {
        if (failure == null) {
            SessionFactoryUtils.closeSession(session);
            return;
        }
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Throwable closeEx) {
            attach(failure, closeEx);
        }
    }

    /** Attaches {@code other} to {@code failure} as suppressed, unless it is that very failure. */
    private static void attach(Throwable failure, Throwable other) {
        if (other != failure) {
            failure.addSuppressed(other);
        }
    }

    @Override
    public void postHandle(WebRequest request, ModelMap model) throws DataAccessException {
        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.getResource(getSessionFactory());
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
        } finally {
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
                    } catch (RuntimeException ex) {
                        if (firstFlushException == null) {
                            firstFlushException = ex;
                        } else {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Additional flush exception for datasource '" + config.connectionName + "'", ex);
                            }
                            firstFlushException.addSuppressed(ex);
                        }
                    }
                } finally {
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
            closeAdditionalSessions(request, additionalSessionFactories.size(), false, null);
        } finally {
            super.afterCompletion(request, ex);
        }
    }

    /**
     * Called instead of {@link #postHandle} and {@link #afterCompletion} on the thread that started asynchronous
     * handling. The sessions this interceptor opened for the other datasources are closed here, so that the thread
     * goes back to its pool without them. Each is flushed first when {@link #postHandle} would flush it and Hibernate
     * allows the flush: inside a transaction, or with {@code hibernate.allow_update_outside_transaction}.
     *
     * <p>The parent class only unbinds the default session, and leaves closing it to the interceptors it registers for
     * a {@code Callable} or {@code DeferredResult}. A promise or {@code startAsync()} in a Grails controller starts
     * asynchronous handling without them, so the default session this interceptor opened is closed here too.
     *
     * <p>When a flush fails, that failure is thrown once every session is released, and a failure to close one is
     * attached to it.
     */
    @Override
    public void afterConcurrentHandlingStarted(WebRequest request) {
        Throwable flushFailure = null;
        try {
            closeAdditionalSessions(request, additionalSessionFactories.size(), true, null);
        } catch (Throwable ex) {
            flushFailure = ex;
            throw ex;
        } finally {
            if (isGrailsAsync(request) && !isParticipating(request) &&
                    TransactionSynchronizationManager.hasResource(getSessionFactory())) {
                SessionHolder sessionHolder =
                        (SessionHolder) TransactionSynchronizationManager.unbindResource(getSessionFactory());
                if (logger.isDebugEnabled()) {
                    logger.debug("Closing Hibernate Session in OpenSessionInViewInterceptor as asynchronous handling starts");
                }
                closeSession(sessionHolder.getSession(), flushFailure);
            } else {
                super.afterConcurrentHandlingStarted(request);
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

    /**
     * Closes the sessions this request opened for the first {@code count} other datasources, and counts down those it
     * joined. With {@code flushFirst}, flushes each it opened first when Hibernate allows it, and throws the first
     * flush that failed once every session is released.
     *
     * <p>A failure to close a session is attached to {@code failure}, the failure the caller is about to throw, or
     * else to the flush failure this throws; with neither, it is logged, as the parent class does.
     */
    private void closeAdditionalSessions(WebRequest request, int count, boolean flushFirst, Throwable failure) {
        Throwable firstFlushFailure = null;
        List<Map.Entry<String, Throwable>> closeFailures = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
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
                } catch (Throwable flushFailure) {
                    // an Error too, such as a failed assert in a domain's beforeUpdate: every session is released first
                    if (firstFlushFailure == null) {
                        firstFlushFailure = flushFailure;
                    } else {
                        firstFlushFailure.addSuppressed(flushFailure);
                    }
                }
                TransactionSynchronizationManager.unbindResource(sf);
                if (logger.isDebugEnabled()) {
                    logger.debug("Closing additional Hibernate Session for datasource '" + config.connectionName + "' in OpenSessionInViewInterceptor");
                }
                try {
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (Throwable closeEx) {
                    closeFailures.add(Map.entry(config.connectionName, closeEx));
                }
            }
        }
        Throwable reportTo = failure != null ? failure : firstFlushFailure;
        for (Map.Entry<String, Throwable> closeFailure : closeFailures) {
            if (reportTo != null) {
                attach(reportTo, closeFailure.getValue());
            } else {
                logger.error("Unexpected exception on closing additional Hibernate Session for datasource '" + closeFailure.getKey() + "'", closeFailure.getValue());
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

    /**
     * @return the name of the request attribute that records whether the request found the default session bound,
     * unique to this interceptor, so that another one whose factory prints the same way does not share it
     */
    @Override
    protected String getParticipateAttributeName() {
        return participateAttributeName;
    }

    private static void incrementParticipateCount(WebRequest request, SessionFactory sf) {
        Map<SessionFactory, Integer> counts = participateCounts(request);
        if (counts == null) {
            counts = new IdentityHashMap<>();
            request.setAttribute(PARTICIPATE_COUNTS, counts, WebRequest.SCOPE_REQUEST);
        }
        counts.merge(sf, 1, Integer::sum);
    }

    /**
     * @return {@code true} when this request used a session of the given factory that was already open, which is
     * then left as it is
     */
    private static boolean decrementParticipateCount(WebRequest request, SessionFactory sf) {
        Map<SessionFactory, Integer> counts = participateCounts(request);
        Integer count = counts != null ? counts.get(sf) : null;
        if (count == null) {
            return false;
        }
        if (count > 1) {
            counts.put(sf, count - 1);
        } else {
            counts.remove(sf);
            if (counts.isEmpty()) {
                request.removeAttribute(PARTICIPATE_COUNTS, WebRequest.SCOPE_REQUEST);
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<SessionFactory, Integer> participateCounts(WebRequest request) {
        return (Map<SessionFactory, Integer>) request.getAttribute(PARTICIPATE_COUNTS, WebRequest.SCOPE_REQUEST);
    }

    public void setHibernateDatastore(HibernateDatastore hibernateDatastore) {
        String defaultFlushModeName = hibernateDatastore.getDefaultFlushModeName();
        if (hibernateDatastore.isOsivReadOnly()) {
            this.hibernateFlushMode = FlushMode.MANUAL;
        } else {
            this.hibernateFlushMode = FlushMode.valueOf(defaultFlushModeName);
        }
        setSessionFactory(hibernateDatastore.getSessionFactory());

        if (hibernateDatastore instanceof HibernateDatastore) {
            HibernateDatastore hibernateDs = (HibernateDatastore) hibernateDatastore;
            for (ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource : hibernateDs.getConnectionSources().getAllConnectionSources()) {
                String connectionName = connectionSource.getName();
                if (!ConnectionSource.DEFAULT.equals(connectionName)) {
                    HibernateDatastore childDatastore = hibernateDs.getDatastoreForConnection(connectionName);
                    FlushMode childFlushMode;
                    if (childDatastore.isOsivReadOnly()) {
                        childFlushMode = FlushMode.MANUAL;
                    } else {
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
