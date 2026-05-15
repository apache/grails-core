/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.exceptions

import org.grails.datastore.mapping.core.DatastoreException
import spock.lang.Specification

class GrailsQueryExceptionSpec extends Specification {

    def 'constructor with message stores the message'() {
        when:
        def ex = new GrailsQueryException('invalid query')

        then:
        ex.message == 'invalid query'
        ex.cause == null
    }

    def 'constructor with message and cause stores both'() {
        given:
        def cause = new IllegalArgumentException('bad arg')

        when:
        def ex = new GrailsQueryException('query failed', cause)

        then:
        ex.message == 'query failed'
        ex.cause.is(cause)
    }

    def 'GrailsQueryException is a DatastoreException'() {
        expect:
        new GrailsQueryException('msg') instanceof DatastoreException
    }

    def 'GrailsQueryException is a RuntimeException'() {
        expect:
        new GrailsQueryException('msg') instanceof RuntimeException
    }

    def 'can be thrown and caught as DatastoreException'() {
        when:
        try {
            throw new GrailsQueryException('fail')
        } catch (DatastoreException e) {
            assert e.message == 'fail'
        }

        then:
        noExceptionThrown()
    }

    def 'cause constructor preserves the full cause chain'() {
        given:
        def root = new IOException('disk full')
        def mid = new RuntimeException('wrapped', root)

        when:
        def ex = new GrailsQueryException('top level', mid)

        then:
        ex.cause.is(mid)
        ex.cause.cause.is(root)
    }
}
