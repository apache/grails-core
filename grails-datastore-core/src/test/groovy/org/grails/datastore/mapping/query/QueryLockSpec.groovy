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

package org.grails.datastore.mapping.query

import jakarta.persistence.LockModeType

import spock.lang.Issue
import spock.lang.Specification

import org.grails.datastore.mapping.model.PersistentEntity

/**
 * {@code lock(boolean)} asks for a write lock for true and for none for false, and the last call wins.
 */
@Issue('https://github.com/apache/grails-core/issues/14501')
class QueryLockSpec extends Specification {

    private static class RecordingQuery extends Query {

        RecordingQuery() {
            super(null, null)
        }

        LockModeType lockMode() {
            lockResult
        }

        @Override
        protected List executeQuery(PersistentEntity entity, Query.Junction criteria) {
            []
        }
    }

    void 'a query that is not asked to lock has no lock mode'() {
        expect:
        new RecordingQuery().lockMode() == null
    }

    void 'lock(true) asks for a write lock'() {
        given:
        RecordingQuery query = new RecordingQuery()

        when:
        Query returned = query.lock(true)

        then:
        returned.is(query)
        query.lockMode() == LockModeType.PESSIMISTIC_WRITE
    }

    void 'lock(false) asks for no lock'() {
        given:
        RecordingQuery query = new RecordingQuery()

        when:
        query.lock(false)

        then:
        query.lockMode() == LockModeType.NONE
    }

    void 'lock(false) after lock(true) asks for no lock'() {
        given:
        RecordingQuery query = new RecordingQuery()

        when:
        query.lock(true).lock(false)

        then:
        query.lockMode() == LockModeType.NONE
    }

    void 'lock(true) after lock(false) asks for a write lock'() {
        given:
        RecordingQuery query = new RecordingQuery()

        when:
        query.lock(false).lock(true)

        then:
        query.lockMode() == LockModeType.PESSIMISTIC_WRITE
    }

    void 'lock with a lock mode asks for that mode'() {
        given:
        RecordingQuery query = new RecordingQuery()

        when:
        query.lock(LockModeType.PESSIMISTIC_READ)

        then:
        query.lockMode() == LockModeType.PESSIMISTIC_READ
    }
}
