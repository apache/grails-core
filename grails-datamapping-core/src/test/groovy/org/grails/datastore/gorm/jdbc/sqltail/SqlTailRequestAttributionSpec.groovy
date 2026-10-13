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

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

class SqlTailRequestAttributionSpec extends Specification {

    SqlTailRequestAttribution attribution = new SqlTailRequestAttribution()

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void "a statement outside a web request is attributed to its thread"() {
        when:
        SqlTailRecord record = attribution.attribute(new SqlTailRecord(sql: 'select 1'))

        then:
        record.requestId == null
        record.request == null
        record.page == "thread:${Thread.currentThread().name}"
        !record.webRequest
    }

    void "a statement in a web request is attributed to the controller and action serving it"() {
        given:
        bindRequest('GET', '/book/show/42', controller: 'book', action: 'show')

        when:
        SqlTailRecord record = attribution.attribute(new SqlTailRecord(sql: 'select 1'))

        then:
        record.requestId == 'r1'
        record.page == 'book/show'
        record.request == 'GET /book/show/42'
        record.webRequest
    }

    void "every statement of a request shares its id and the next request gets the next id"() {
        given:
        bindRequest('GET', '/book/index', controller: 'book', action: 'index')

        when:
        List<String> first = (1..3).collect { attribution.attribute(new SqlTailRecord(sql: 'select 1')).requestId }
        bindRequest('POST', '/book/save', controller: 'book', action: 'save')
        String second = attribution.attribute(new SqlTailRecord(sql: 'insert into book (id) values (?)')).requestId

        then:
        first == ['r1', 'r1', 'r1']
        second == 'r2'
    }

    void "the page carries the controller namespace and copes with a missing action"() {
        given:
        bindRequest('GET', '/admin/book', controller: 'book', namespace: 'admin')

        expect:
        attribution.attribute(new SqlTailRecord(sql: 'select 1')).page == 'admin:book'
    }

    void "a request that has not reached a controller yet has no page"() {
        given:
        bindRequest('GET', '/health')

        when:
        SqlTailRecord record = attribution.attribute(new SqlTailRecord(sql: 'select 1'))

        then:
        record.page == null
        record.requestId == 'r1'
        record.request == 'GET /health'
    }

    private static void bindRequest(Map<String, String> attributes = [:], String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri)
        if (attributes.controller) {
            request.setAttribute(SqlTailRequestAttribution.CONTROLLER_NAME_ATTRIBUTE, attributes.controller)
        }
        if (attributes.action) {
            request.setAttribute(SqlTailRequestAttribution.ACTION_NAME_ATTRIBUTE, attributes.action)
        }
        if (attributes.namespace) {
            request.setAttribute(SqlTailRequestAttribution.CONTROLLER_NAMESPACE_ATTRIBUTE, attributes.namespace)
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))
    }
}
