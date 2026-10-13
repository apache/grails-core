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
package org.grails.datastore.gorm.jdbc.sqltail

import java.util.concurrent.atomic.AtomicLong

import groovy.transform.CompileStatic

import org.springframework.util.ClassUtils
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Attributes a captured statement to the web request on the current thread. Grails keeps the
 * request, and the controller and action it resolved, on the thread that serves it, so a statement
 * run by an interceptor, the action or the view all land on the same page. The first statement of
 * a request gives it an id that every later statement of that request repeats, which is how the
 * {@code sql-tail} command counts statements per request. Work on any other thread is attributed to
 * the thread by name.
 *
 * <p>Spring Web is optional: without it on the classpath every statement is attributed to its
 * thread.</p>
 *
 * @since 8.1
 */
@CompileStatic
class SqlTailRequestAttribution {

    /** The request attribute holding the id this attribution gave a request. */
    public static final String REQUEST_ID_ATTRIBUTE = 'org.grails.datastore.gorm.jdbc.sqltail.REQUEST_ID'

    /** The request attribute Grails sets the controller name in. */
    public static final String CONTROLLER_NAME_ATTRIBUTE = 'org.grails.CONTROLLER_NAME_ATTRIBUTE'

    /** The request attribute Grails sets the controller namespace in. */
    public static final String CONTROLLER_NAMESPACE_ATTRIBUTE = 'org.grails.CONTROLLER_NAMESPACE_ATTRIBUTE'

    /** The request attribute Grails sets the action name in. */
    public static final String ACTION_NAME_ATTRIBUTE = 'org.grails.ACTION_NAME_ATTRIBUTE'

    private static final String REQUEST_ID_PREFIX = 'r'

    private static final boolean SPRING_WEB_PRESENT = ClassUtils.isPresent(
            'org.springframework.web.context.request.RequestContextHolder', SqlTailRequestAttribution.classLoader)

    private static final boolean SERVLET_PRESENT = SPRING_WEB_PRESENT && ClassUtils.isPresent(
            'jakarta.servlet.http.HttpServletRequest', SqlTailRequestAttribution.classLoader)

    private final AtomicLong requestCounter = new AtomicLong()

    /**
     * Fills in the request id, page and request of a record from the current thread.
     *
     * @param record the record of a statement that just ran on this thread
     * @return the same record
     */
    SqlTailRecord attribute(SqlTailRecord record) {
        RequestAttributes attributes = SPRING_WEB_PRESENT ? WebRequestLookup.current() : null
        if (attributes == null) {
            record.requestId = null
            record.request = null
            record.page = SqlTailRecord.THREAD_PAGE_PREFIX + Thread.currentThread().name
            return record
        }
        record.requestId = requestId(attributes)
        record.page = page(attributes)
        record.request = SERVLET_PRESENT ? WebRequestLookup.describe(attributes) : null
        record
    }

    private String requestId(RequestAttributes attributes) {
        synchronized (attributes) {
            Object existing = attributes.getAttribute(REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            if (existing != null) {
                return existing.toString()
            }
            String id = REQUEST_ID_PREFIX + requestCounter.incrementAndGet()
            attributes.setAttribute(REQUEST_ID_ATTRIBUTE, id, RequestAttributes.SCOPE_REQUEST)
            return id
        }
    }

    private static String page(RequestAttributes attributes) {
        Object controller = attributes.getAttribute(CONTROLLER_NAME_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
        if (controller == null) {
            return null
        }
        Object namespace = attributes.getAttribute(CONTROLLER_NAMESPACE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
        Object action = attributes.getAttribute(ACTION_NAME_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
        StringBuilder page = new StringBuilder()
        if (namespace != null) {
            page.append(namespace).append(':')
        }
        page.append(controller)
        if (action != null) {
            page.append('/').append(action)
        }
        page.toString()
    }

    /**
     * Keeps every reference to Spring Web in a class that is only loaded once Spring Web is known
     * to be present.
     */
    @CompileStatic
    private static class WebRequestLookup {

        static RequestAttributes current() {
            RequestContextHolder.getRequestAttributes()
        }

        static String describe(RequestAttributes attributes) {
            if (!(attributes instanceof ServletRequestAttributes)) {
                return null
            }
            def request = ((ServletRequestAttributes) attributes).request
            "${request.method} ${request.requestURI}".toString()
        }
    }
}
