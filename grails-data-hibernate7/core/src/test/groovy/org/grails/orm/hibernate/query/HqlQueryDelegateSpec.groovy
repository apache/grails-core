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
package org.grails.orm.hibernate.query

import jakarta.persistence.LockModeType
import org.hibernate.query.QueryFlushMode
import spock.lang.Specification

/**
 * Covers the default no-op methods defined directly on the {@link HqlQueryDelegate} interface.
 * A minimal stub implementation inherits all defaults so that calling them exercises the
 * interface bytecode (rather than any override in concrete classes).
 */
class HqlQueryDelegateSpec extends Specification {

    private HqlQueryDelegate stub() {
        new HqlQueryDelegate() {
            @Override void setTimeout(int timeout) {}
            @Override void setQueryFlushMode(QueryFlushMode mode) {}
            @Override void setParameter(String name, Object value) {}
            @Override <T> void setParameter(String name, T value, Class<T> type) {}
            @Override void setParameter(int position, Object value) {}
            @Override <T> void setParameter(int position, T value, Class<T> type) {}
            @Override void setHint(String hintName, Object value) {}
            @Override List list() { [] }
            @Override int executeUpdate() { 0 }
            @Override org.hibernate.query.Query<?> selectQuery() { null }
        }
    }

    def 'default setMaxResults is a no-op'() {
        given:
        def delegate = stub()
        expect:
        delegate.setMaxResults(10) == null
    }

    def 'default setFirstResult is a no-op'() {
        given:
        def delegate = stub()
        expect:
        delegate.setFirstResult(5) == null
    }

    def 'default setCacheable is a no-op'() {
        given:
        def delegate = stub()
        expect:
        delegate.setCacheable(true) == null
    }

    def 'default setFetchSize is a no-op'() {
        given:
        def delegate = stub()
        expect:
        delegate.setFetchSize(50) == null
    }

    def 'default setReadOnly is a no-op'() {
        given:
        def delegate = stub()
        expect:
        delegate.setReadOnly(true) == null
    }

    def 'default setLockMode is a no-op'() {
        given:
        def delegate = stub()
        expect:
        delegate.setLockMode(LockModeType.READ) == null
    }

    def 'default setParameterList with Collection is a no-op'() {
        given:
        def delegate = stub()
        expect:
        delegate.setParameterList('names', ['a', 'b'] as Collection) == null
    }

    def 'default setParameterList with Object array is a no-op'() {
        given:
        def delegate = stub()
        expect:
        delegate.setParameterList('names', 'a', 'b') == null
    }

    def 'stub setHint is a no-op'() {
        given:
        def delegate = stub()
        expect:
        delegate.setHint('hint', 'value') == null
    }
}
