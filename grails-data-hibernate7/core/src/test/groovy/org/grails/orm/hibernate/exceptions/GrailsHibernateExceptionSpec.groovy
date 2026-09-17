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
package org.grails.orm.hibernate.exceptions

import org.grails.datastore.mapping.core.DatastoreException
import spock.lang.Specification

class GrailsHibernateExceptionSpec extends Specification {

    private static class TestHibernateException extends GrailsHibernateException {
        TestHibernateException(String message) {
            super(message)
        }

        TestHibernateException(String message, Throwable cause) {
            super(message, cause)
        }
    }

    def "constructor with message stores the message"() {
        when:
        def ex = new TestHibernateException("invalid configuration")

        then:
        ex.message == "invalid configuration"
        ex.cause == null
    }

    def "constructor with message and cause stores both"() {
        given:
        def cause = new IllegalStateException("bad state")

        when:
        def ex = new TestHibernateException("configuration failed", cause)

        then:
        ex.message == "configuration failed"
        ex.cause.is(cause)
    }

    def "GrailsHibernateException is a DatastoreException"() {
        expect:
        new TestHibernateException("msg") instanceof DatastoreException
    }

    def "GrailsHibernateException is a RuntimeException"() {
        expect:
        new TestHibernateException("msg") instanceof RuntimeException
    }

    def "can be thrown and caught as DatastoreException"() {
        when:
        try {
            throw new TestHibernateException("fail")
        } catch (DatastoreException e) {
            assert e.message == "fail"
        }

        then:
        noExceptionThrown()
    }
}
