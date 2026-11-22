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

package org.grails.datastore.mapping.web.support;

import jakarta.persistence.FlushModeType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.AsyncWebRequestInterceptor;
import org.springframework.web.context.request.WebRequest;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.transactions.SessionHolder;

/**
 * A {@link org.springframework.web.context.request.WebRequestInterceptor} instance that
 * handles opening a Datastore session within the scope of a request.
 *
 * <p>A request that finds a session already bound, such as an include within a request that opened one, uses that
 * session and leaves it to the one that opened it. When a request goes asynchronous, the session it opened is
 * flushed and closed as when the request completes, so that it does not stay bound to the thread for the next
 * request that thread serves.</p>
 */
public class OpenSessionInViewInterceptor implements AsyncWebRequestInterceptor {

    private static final Log LOG = LogFactory.getLog(OpenSessionInViewInterceptor.class);

    private static final String PARTICIPATE_SUFFIX = ".PARTICIPATE";

    Datastore datastore;
    FlushModeType flushMode = FlushModeType.AUTO;

    public void setDatastore(Datastore datastore) {
        this.datastore = datastore;
    }

    public Datastore getDatastore() {
        return datastore;
    }

    public void preHandle(WebRequest webRequest) throws Exception {
        if (hasSessionBound()) {
            // a session is already open for this request: use it, and leave it to the one that opened it
            String participateAttributeName = getParticipateAttributeName();
            Integer count = (Integer) webRequest.getAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
            webRequest.setAttribute(participateAttributeName, count != null ? count + 1 : 1, WebRequest.SCOPE_REQUEST);
            return;
        }

        // single session mode
        LOG.debug("Opening single Datastore Session in OpenSessionInViewInterceptor");

        Session session = DatastoreUtils.getSession(datastore, true);
        session.setFlushMode(flushMode);
        if (!hasSessionBound()) {
            DatastoreUtils.bindSession(session);
        }
    }

    public void postHandle(WebRequest webRequest, ModelMap modelMap) throws Exception {
        // Only potentially flush in single session mode.
        if (!hasSessionBound()) {
            return;
        }

        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.getResource(getDatastore());
        LOG.debug("Flushing single Datastore Session in OpenSessionInViewInterceptor");
        flushIfNecessary(sessionHolder.getSession());
    }

    protected boolean hasSessionBound() {
        return TransactionSynchronizationManager.getResource(getDatastore()) != null;
    }

    public void afterCompletion(WebRequest webRequest, Exception e) throws Exception {
        if (decrementParticipateCount(webRequest) || !hasSessionBound()) {
            return;
        }

        // single session mode
        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.unbindResource(getDatastore());
        LOG.debug("Closing single Datastore Session in OpenSessionInViewInterceptor");
        DatastoreUtils.closeSession(sessionHolder.getSession());
    }

    /**
     * Called instead of {@link #postHandle} and {@link #afterCompletion} on the thread that started asynchronous
     * handling. Flushes the session this interceptor opened, as {@link #postHandle} would, and closes it, so that the
     * thread goes back to its pool without a session bound.
     */
    public void afterConcurrentHandlingStarted(WebRequest webRequest) {
        if (decrementParticipateCount(webRequest) || !hasSessionBound()) {
            return;
        }

        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.getResource(getDatastore());
        Session session = sessionHolder.getSession();
        try {
            LOG.debug("Flushing single Datastore Session in OpenSessionInViewInterceptor as asynchronous handling starts");
            flushIfNecessary(session);
        }
        finally {
            TransactionSynchronizationManager.unbindResource(getDatastore());
            LOG.debug("Closing single Datastore Session in OpenSessionInViewInterceptor as asynchronous handling starts");
            DatastoreUtils.closeSession(session);
        }
    }

    private void flushIfNecessary(Session session) {
        if (session.getFlushMode() == FlushModeType.AUTO && session.hasTransaction()) {
            session.flush();
        }
    }

    /**
     * @return {@code true} when this request used a session that was already open, which is then left as it is
     */
    private boolean decrementParticipateCount(WebRequest webRequest) {
        String participateAttributeName = getParticipateAttributeName();
        Integer count = (Integer) webRequest.getAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
        if (count == null) {
            return false;
        }
        if (count > 1) {
            webRequest.setAttribute(participateAttributeName, count - 1, WebRequest.SCOPE_REQUEST);
        }
        else {
            webRequest.removeAttribute(participateAttributeName, WebRequest.SCOPE_REQUEST);
        }
        return true;
    }

    private String getParticipateAttributeName() {
        return OpenSessionInViewInterceptor.class.getName() + '.' + System.identityHashCode(getDatastore()) + PARTICIPATE_SUFFIX;
    }
}
