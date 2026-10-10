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
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import jakarta.persistence.LockModeType
import org.hibernate.query.QueryFlushMode

/** {@link HqlQueryDelegate} for HQL SELECT queries backed by {@link org.hibernate.query.Query}. */
@CompileStatic
@PackageScope
final class SelectQueryDelegate implements HqlQueryDelegate {

    private final transient org.hibernate.query.Query<?> query

    SelectQueryDelegate(org.hibernate.query.Query<?> query) {
        this.query = query
    }

    @Override
    void setTimeout(int timeout) {
        query.setTimeout(timeout)
    }

    @Override
    void setQueryFlushMode(QueryFlushMode mode) {
        query.setQueryFlushMode(mode)
    }

    @Override
    void setParameter(String name, Object value) {
        query.setParameter(name, value)
    }

    @Override
    <T> void setParameter(String name, T value, Class<T> type) {
        query.setParameter(name, value, type)
    }

    @Override
    void setParameter(int position, Object value) {
        query.setParameter(position, value)
    }

    @Override
    <T> void setParameter(int position, T value, Class<T> type) {
        query.setParameter(position, value, type)
    }

    @Override
    void setHint(String hintName, Object value) {
        query.setHint(hintName, value)
    }

    @Override
    void setMaxResults(int n) {
        query.setMaxResults(n)
    }

    @Override
    void setFirstResult(int n) {
        query.setFirstResult(n)
    }

    @Override
    void setCacheable(boolean b) {
        query.setCacheable(b)
    }

    @Override
    void setFetchSize(int n) {
        query.setFetchSize(n)
    }

    @Override
    void setReadOnly(boolean b) {
        query.setReadOnly(b)
    }

    @Override
    void setLockMode(LockModeType lockModeType) {
        query.setLockMode(lockModeType)
    }

    @Override
    void setParameterList(String name, Collection<?> values) {
        query.setParameterList(name, values)
    }

    @Override
    void setParameterList(String name, Object[] values) {
        query.setParameterList(name, values)
    }

    @Override
    @SuppressWarnings('rawtypes')
    List list() {
        return query.list()
    }

    @Override
    int executeUpdate() {
        throw new UnsupportedOperationException(
                'SELECT query cannot be used for executeUpdate(); use a MutationQuery instead')
    }

    @Override
    org.hibernate.query.Query<?> selectQuery() {
        return query
    }

}
