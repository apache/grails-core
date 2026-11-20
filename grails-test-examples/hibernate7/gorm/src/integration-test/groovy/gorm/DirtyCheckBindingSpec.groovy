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

package gorm

import org.hibernate.proxy.HibernateProxy

import grails.testing.mixin.integration.Integration
import grails.web.databinding.DataBindingUtils
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Tag

import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Functional test reproducing issue 15681 end-to-end: a real Grails application binds request parameters
 * (including {@code id} and {@code version}) to a domain class that extends an abstract {@code @DirtyCheck}
 * base. The framework must not bind {@code id} or {@code version} by default, including to a proxy of a persisted
 * record.
 */
@Integration
@Tag('http-client')
class DirtyCheckBindingSpec extends Specification implements HttpClientSupport {

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'bindData over HTTP does not bind id or version on a domain extending a @DirtyCheck base'() {
        when: 'a form submission posts id, version and a regular property'
        def response = httpPostForm(
                '/dirtyCheckBinding/bind',
                [
                        id: 99,
                        version: 5,
                        description: 'Opening balance'
                ]
        )

        then: 'the request succeeds'
        response.assertStatus(200)

        and: 'the regular property is bound'
        response.assertContains('description=Opening balance')

        and: 'id and version are not bound, matching the documented default behaviour'
        response.assertContains('id=null')
        response.assertContains('version=null')
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'binding only a regular property over HTTP leaves id and version null'() {
        when:
        def response = httpPostForm(
                '/dirtyCheckBinding/bind',
                [
                        description: 'Closing balance'
                ]
        )

        then:
        with(response) {
            assertStatus(200)
            assertContains('description=Closing balance')
            assertContains('id=null')
            assertContains('version=null')
        }
    }

    void 'bindData over HTTP does not bind id or version on a proxy of the domain class'() {
        given: 'a persisted record, which a new request loads as a proxy'
        Long recordId = persistRecord()

        when: 'a form submission posts id, version and a regular property to bind to the proxy'
        def response = httpPostForm(
                '/dirtyCheckBinding/bindProxy',
                [
                        recordId: recordId,
                        id: recordId + 100,
                        version: 5,
                        description: 'Rebound'
                ]
        )

        then: 'the proxy is bound as the domain class it stands for'
        with(response) {
            assertStatus(200)
            assertContains('proxy=true')
            assertContains("id=${recordId}|")
            assertContains('version=0|')
            assertContains('description=Rebound')
        }

        cleanup:
        deleteRecord(recordId)
    }

    void 'DataBindingUtils does not bind id or version on a proxy of the domain class'() {
        given:
        Long recordId = persistRecord()

        when: 'a session that holds nothing yet loads the record as a proxy, which is then bound'
        Map bound = DirtyCheckedRecord.withNewSession { session ->
            def record = DirtyCheckedRecord.load(recordId)
            boolean proxy = record instanceof HibernateProxy
            DataBindingUtils.bindObjectToInstance(record, [id: recordId + 100, version: 5, description: 'Rebound'])
            Map values = [proxy: proxy, id: record.id, version: record.version, description: record.description]
            session.clear()
            values
        }

        then:
        bound == [proxy: true, id: recordId, version: 0L, description: 'Rebound']

        cleanup:
        deleteRecord(recordId)
    }

    private static Long persistRecord() {
        DirtyCheckedRecord.withNewTransaction {
            new DirtyCheckedRecord(description: 'Persisted').save(flush: true, failOnError: true).id
        }
    }

    private static void deleteRecord(Long recordId) {
        DirtyCheckedRecord.withNewTransaction {
            DirtyCheckedRecord.get(recordId)?.delete(flush: true)
        }
    }
}
