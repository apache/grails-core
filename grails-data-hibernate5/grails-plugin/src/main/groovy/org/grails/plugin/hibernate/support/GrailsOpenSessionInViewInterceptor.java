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

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;

import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
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
 * they are closed when the asynchronous request completes. Which sessions a
 * request found bound is recorded by the identity of their factory, taken as
 * Spring's TransactionSynchronizationManager takes it, the target of an
 * InfrastructureProxy, not by how the factory prints: {@code Object.toString()}
 * is not unique.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public class GrailsOpenSessionInViewInterceptor extends OpenSessionInViewInterceptor {

    /** The value of {@code GrailsApplicationAttributes.ASYNC_STARTED}, which is in a module this plugin does not use. */
    private static final String GRAILS_ASYNC_STARTED = "org.grails.ASYNC_STARTED";

    /**
     * The request attribute that holds, for each factory of another datasource whose session the request found bound,
     * how many times it did, keyed by the identity of the factory as {@link #resourceKey} takes it.
     */
    private static final String PARTICIPATE_COUNTS =
            GrailsOpenSessionInViewInterceptor.class.getName() + ".PARTICIPATE_COUNTS";

    /**
     * For each default factory, the name of the request attribute that records whether the request found its session
     * bound, which the parent class also registers the request's asynchronous interceptors under. It is shared by every
     * interceptor using that factory, as the parent's name is, and unique to the factory, which Hibernate's compares by
     * identity, keyed as {@link #resourceKey} keys it, so that a proxy and its target share it. The keys are weak, so
     * that a factory that is closed and dropped is not kept.
     */
    private static final Map<Object, String> PARTICIPATE_ATTRIBUTE_NAMES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final AtomicLong FACTORY_COUNT = new AtomicLong();

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
        }
        catch (HibernateException ex) {
            throw new DataAccessResourceFailureException("Could not open Hibernate Session", ex);
        }
        try {
            session.setHibernateFlushMode(FlushMode.MANUAL);
        }
        catch (HibernateException ex) {
            DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Could not open Hibernate Session", ex);
            // not bound yet, so afterCompletion would not find it
            closeSession(session, failure);
            throw failure;
        }
        catch (Throwable ex) {
            closeSession(session, ex);
            throw ex;
        }
        try {
            applyFlushMode(session);
            checkNotBoundDuringSetup(sf, "the default datasource");
        }
        catch (Throwable ex) {
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
                }
                else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Opening additional Hibernate Session for datasource '" + config.connectionName + "' in OpenSessionInViewInterceptor");
                    }
                    Session session = sf.openSession();
                    try {
                        session.setHibernateFlushMode(config.flushMode);
                        checkNotBoundDuringSetup(sf, "datasource '" + config.connectionName + "'");
                        TransactionSynchronizationManager.bindResource(sf, new SessionHolder(session));
                    }
                    catch (Throwable ex) {
                        // not bound yet, so the release below would not find it
                        closeSession(session, ex);
                        throw ex;
                    }
                }
                acquired++;
            }
        }
        catch (Throwable ex) {
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
            closeAdditionalSessions(request, acquired, false, failure, null);
        }
        catch (Throwable releaseEx) {
            attach(failure, releaseEx);
        }
        try {
            releaseDefaultSession(request, failure);
        }
        catch (Throwable releaseEx) {
            attach(failure, releaseEx);
        }
    }

    /**
     * Releases the default session as the parent's {@link #afterCompletion} does: counts it down when the request found
     * it bound, and otherwise unbinds and closes it. A failure to close it is attached to {@code failure} when there
     * is one, and logged otherwise, as the parent class does.
     */
    private void releaseDefaultSession(WebRequest request, Throwable failure) {
        String participateAttributeName = getParticipateAttributeName();
        Integer count = (Integer) request.getAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
        if (count != null) {
            if (count > 1) {
                request.setAttribute(participateAttributeName, count - 1, WebRequest.SCOPE_REQUEST);
            }
            else {
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
        }
        catch (Throwable closeEx) {
            attach(failure, closeEx);
        }
    }

    /**
     * Attaches {@code other} to {@code failure} as suppressed, unless it is that very failure. When {@code failure}
     * does not keep suppressed exceptions, {@code other} is logged instead, so that it is not lost.
     */
    private void attach(Throwable failure, Throwable other) {
        if (other == failure) {
            return;
        }
        failure.addSuppressed(other);
        Throwable[] suppressed = failure.getSuppressed();
        if (suppressed.length == 0 || suppressed[suppressed.length - 1] != other) {
            logger.error("Unexpected exception on releasing a Hibernate Session in OpenSessionInViewInterceptor", other);
        }
    }

    /** @return {@code failure}, or an {@link UndeclaredThrowableException} around it when it is a checked exception */
    private static Throwable asUnchecked(Throwable failure) {
        return failure instanceof RuntimeException || failure instanceof Error ? failure : new UndeclaredThrowableException(failure);
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
                            attach(firstFlushException, ex);
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

    /**
     * Closes the sessions this request opened, and counts down those it found bound. When the request failed, Spring
     * passes the failure here and throws it on afterwards, so a failure to release a session is attached to it; without
     * one, a failure to close a session is logged, as the parent class does.
     */
    @Override
    public void afterCompletion(WebRequest request, Exception ex) throws DataAccessException {
        Throwable releaseFailure = null;
        try {
            closeAdditionalSessions(request, additionalSessionFactories.size(), false, ex, null);
        }
        catch (Throwable releaseEx) {
            releaseFailure = releaseEx;
        }
        try {
            releaseDefaultSession(request, ex);
        }
        catch (Throwable releaseEx) {
            if (releaseFailure == null) {
                releaseFailure = releaseEx;
            }
            else {
                attach(releaseFailure, releaseEx);
            }
        }
        if (releaseFailure != null) {
            if (ex != null) {
                attach(ex, releaseFailure);
            }
            else {
                ReflectionUtils.rethrowRuntimeException(releaseFailure);
            }
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
     * a {@code Callable} or {@code DeferredResult}. A promise or {@code startAsync()} in a Grails controller starts
     * asynchronous handling without them, so the default session this interceptor opened is unbound here too, and
     * closed when the asynchronous request completes.
     *
     * <p>When a flush fails, that failure is thrown once every session is taken off the thread, and a failure to
     * release one is attached to it. Without an asynchronous request to wait for, the sessions are closed here, and a
     * failure to close one is attached to that failure too.
     */
    @Override
    public void afterConcurrentHandlingStarted(WebRequest request) {
        List<Session> released = new ArrayList<>();
        Throwable failure = null;
        try {
            closeAdditionalSessions(request, additionalSessionFactories.size(), true, null, released);
        }
        catch (Throwable ex) {
            failure = asUnchecked(ex);
        }
        try {
            releaseDefaultSessionAsAsyncStarts(request, released);
        }
        catch (Throwable releaseEx) {
            // a flush failure is on its way out: a release failure is attached to it, not thrown in its place
            if (failure == null) {
                failure = asUnchecked(releaseEx);
            }
            else {
                attach(failure, releaseEx);
            }
        }
        closeWhenAsynchronousRequestCompletes(request, released, failure);
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure != null) {
            throw (Error) failure;
        }
    }

    /**
     * Takes the default session off the thread as asynchronous handling starts. One this interceptor opened, for a
     * request Grails made asynchronous, is added to {@code released}, to be closed when the asynchronous request
     * completes. Otherwise the parent class unbinds it, and leaves closing it to its asynchronous interceptors, which
     * close it in the dispatch that a {@code Callable} or {@code DeferredResult} starts; one this interceptor opened is
     * also closed when the asynchronous request completes, unless it is closed by then, since a request can complete
     * without that dispatch, such as one that a task the action started, and did not return, completes.
     */
    private void releaseDefaultSessionAsAsyncStarts(WebRequest request, List<Session> released) {
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

    /**
     * Closes the default session that the parent class took off the thread when the asynchronous request completes,
     * unless it is closed by then. Without an asynchronous context to listen to, it is left to the parent class.
     */
    private void closeIfLeftOpenWhenAsynchronousRequestCompletes(WebRequest request, Session session) {
        AsynchronousCompletion.onComplete(request, () -> {
            if (session.isOpen()) {
                closeSessions(List.of(session), null);
            }
        });
    }

    /**
     * Closes the sessions taken off the thread when the asynchronous request completes, so that the work the request
     * continues with, such as a task that lazily loads an association of an entity the action loaded, can still use
     * them. Without an asynchronous context to listen to, they are closed now, and a failure to close one is attached
     * to {@code failure}, the failure the caller is about to throw, when there is one.
     */
    private void closeWhenAsynchronousRequestCompletes(WebRequest request, List<Session> sessions, Throwable failure) {
        if (sessions.isEmpty()) {
            return;
        }
        if (AsynchronousCompletion.onComplete(request, () -> closeSessions(sessions, null))) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing " + sessions.size() + " Hibernate Session(s) in OpenSessionInViewInterceptor when the asynchronous request completes");
            }
            return;
        }
        closeSessions(sessions, failure);
    }

    /**
     * Closes the sessions taken off the thread. A failure to close one is attached to {@code failure} when there is
     * one, and logged otherwise, as the parent class does, so that every session is closed.
     */
    private void closeSessions(List<Session> sessions, Throwable failure) {
        for (Session session : sessions) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing Hibernate Session in OpenSessionInViewInterceptor after asynchronous handling");
            }
            closeSession(session, failure);
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

    /**
     * Closes the sessions this request opened for the first {@code count} other datasources, and counts down those it
     * joined. With {@code flushFirst}, flushes each it opened first when Hibernate allows it, and throws the first
     * flush that failed once every session is released, in an {@link UndeclaredThrowableException} when it is a
     * checked exception.
     *
     * <p>A failure to release one session does not keep the others from being released. It is attached to
     * {@code failure}, the failure the caller is about to throw, or else to the exception this throws, as are the flush
     * failures after the first; with neither, it is logged, as the parent class does for a failure to close.
     *
     * <p>With {@code released}, the sessions are taken off the thread and added to it for the caller to close, instead
     * of being closed here.
     */
    private void closeAdditionalSessions(WebRequest request, int count, boolean flushFirst, Throwable failure,
            List<Session> released) {
        List<Throwable> flushFailures = new ArrayList<>();
        List<Map.Entry<String, Throwable>> releaseFailures = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            AdditionalSessionFactoryConfig config = additionalSessionFactories.get(i);
            try {
                releaseAdditionalSession(request, config, flushFirst, released, flushFailures, releaseFailures);
            }
            catch (Throwable releaseEx) {
                releaseFailures.add(Map.entry(config.connectionName, releaseEx));
            }
        }
        Throwable outgoing = failure == null && !flushFailures.isEmpty() ? asUnchecked(flushFailures.remove(0)) : null;
        Throwable reportTo = failure != null ? failure : outgoing;
        for (Throwable flushFailure : flushFailures) {
            attach(reportTo, flushFailure);
        }
        for (Map.Entry<String, Throwable> releaseFailure : releaseFailures) {
            if (reportTo != null) {
                attach(reportTo, releaseFailure.getValue());
            }
            else {
                logger.error("Unexpected exception on closing additional Hibernate Session for datasource '" + releaseFailure.getKey() + "'", releaseFailure.getValue());
            }
        }
        if (outgoing instanceof RuntimeException) {
            throw (RuntimeException) outgoing;
        }
        if (outgoing != null) {
            throw (Error) outgoing;
        }
    }

    /**
     * Releases the session of one other datasource: counts it down when the request found it bound, and otherwise
     * flushes it first when asked to, unbinds the holder this request bound, unless something has already taken it
     * off, and closes the session, or adds it to {@code released} for the caller to close. A flush failure, an Error
     * too, such as a failed assert in a domain's beforeUpdate, and a failure to unbind or close are collected, so that
     * every step still runs.
     */
    private void releaseAdditionalSession(WebRequest request, AdditionalSessionFactoryConfig config, boolean flushFirst,
            List<Session> released, List<Throwable> flushFailures, List<Map.Entry<String, Throwable>> releaseFailures) {
        SessionFactory sf = config.sessionFactory;
        if (decrementParticipateCount(request, sf)) {
            return;
        }
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sf);
        if (sessionHolder == null) {
            return;
        }
        Session session = sessionHolder.getSession();
        if (flushFirst) {
            try {
                if (isFlushAllowed(session)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Flushing additional Hibernate session for datasource '" + config.connectionName + "' as asynchronous handling starts");
                    }
                    session.flush();
                }
            }
            catch (Throwable flushFailure) {
                flushFailures.add(flushFailure);
            }
        }
        try {
            if (TransactionSynchronizationManager.getResource(sf) == sessionHolder) {
                TransactionSynchronizationManager.unbindResource(sf);
            }
        }
        catch (Throwable unbindEx) {
            releaseFailures.add(Map.entry(config.connectionName, unbindEx));
        }
        if (released != null) {
            released.add(session);
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Closing additional Hibernate Session for datasource '" + config.connectionName + "' in OpenSessionInViewInterceptor");
        }
        try {
            if (session.isOpen()) {
                session.close();
            }
        }
        catch (Throwable closeEx) {
            releaseFailures.add(Map.entry(config.connectionName, closeEx));
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
     * @return the name of the request attribute that records whether the request found the default session bound, and
     * that the parent class registers the request's asynchronous interceptors under: shared by the interceptors using
     * the same factory, as the parent's name is, and not by those whose factory only prints the same way
     */
    @Override
    protected String getParticipateAttributeName() {
        SessionFactory sf = getSessionFactory();
        Assert.state(sf != null, "No SessionFactory set");
        return PARTICIPATE_ATTRIBUTE_NAMES.computeIfAbsent(resourceKey(sf), factory ->
                GrailsOpenSessionInViewInterceptor.class.getName() + '.' + FACTORY_COUNT.incrementAndGet() + PARTICIPATE_SUFFIX);
    }

    private static void incrementParticipateCount(WebRequest request, SessionFactory sf) {
        Map<Object, Integer> counts = participateCounts(request);
        if (counts == null) {
            counts = new IdentityHashMap<>();
            request.setAttribute(PARTICIPATE_COUNTS, counts, WebRequest.SCOPE_REQUEST);
        }
        counts.merge(resourceKey(sf), 1, Integer::sum);
    }

    /**
     * @return {@code true} when this request used a session of the given factory that was already open, which is
     * then left as it is
     */
    private static boolean decrementParticipateCount(WebRequest request, SessionFactory sf) {
        Map<Object, Integer> counts = participateCounts(request);
        Object key = resourceKey(sf);
        Integer count = counts != null ? counts.get(key) : null;
        if (count == null) {
            return false;
        }
        if (count > 1) {
            counts.put(key, count - 1);
        }
        else {
            counts.remove(key);
            if (counts.isEmpty()) {
                request.removeAttribute(PARTICIPATE_COUNTS, WebRequest.SCOPE_REQUEST);
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Integer> participateCounts(WebRequest request) {
        return (Map<Object, Integer>) request.getAttribute(PARTICIPATE_COUNTS, WebRequest.SCOPE_REQUEST);
    }

    /**
     * @return the factory as Spring's {@code TransactionSynchronizationManager} keys the sessions bound for it: the
     * target of an {@code InfrastructureProxy}, such as the proxy {@code LocalSessionFactoryBuilder} returns for a
     * factory bootstrapped in the background, so that the proxy and its target count as one factory here too
     */
    private static Object resourceKey(SessionFactory sf) {
        return TransactionSynchronizationUtils.unwrapResourceIfNecessary(sf);
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
