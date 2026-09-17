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
import org.hibernate.query.MutationQuery
import org.hibernate.query.QueryFlushMode

/**
 * {@link HqlQueryDelegate} for HQL UPDATE/DELETE queries backed by
 * {@link org.hibernate.query.MutationQuery}.
 *
 * <p>Select-only methods (setMaxResults, setCacheable, etc.) are inherited as no-ops since
 * {@link MutationQuery} does not support them. {@link #setParameterList} falls back to
 * {@link #setParameter} with the collection value as best-effort support for IN clauses.
 */
@CompileStatic
@PackageScope
final class MutationQueryDelegate implements HqlQueryDelegate {

    private final transient MutationQuery mutationQuery

    MutationQueryDelegate(MutationQuery mutationQuery) {
        this.mutationQuery = mutationQuery
    }

    @Override
    void setTimeout(int timeout) {
        mutationQuery.setTimeout(timeout)
    }

    @Override
    void setQueryFlushMode(QueryFlushMode mode) {
        mutationQuery.setQueryFlushMode(mode)
    }

    @Override
    void setParameter(String name, Object value) {
        mutationQuery.setParameter(name, value)
    }

    @Override
    <T> void setParameter(String name, T value, Class<T> type) {
        mutationQuery.setParameter(name, value, type)
    }

    @Override
    void setParameter(int position, Object value) {
        mutationQuery.setParameter(position, value)
    }

    @Override
    <T> void setParameter(int position, T value, Class<T> type) {
        mutationQuery.setParameter(position, value, type)
    }

    @Override
    void setHint(String hintName, Object value) {
        mutationQuery.setHint(hintName, value)
    }

    @Override
    void setParameterList(String name, Collection<?> values) {
        // MutationQuery has no setParameterList; pass collection directly as parameter value
        mutationQuery.setParameter(name, values)
    }

    @Override
    void setParameterList(String name, Object[] values) {
        mutationQuery.setParameter(name, values)
    }

    @Override
    @SuppressWarnings('rawtypes')
    List list() {
        throw new UnsupportedOperationException(
                'Mutation query (UPDATE/DELETE) cannot be used for list(); use executeUpdate() instead')
    }

    @Override
    int executeUpdate() {
        return mutationQuery.executeUpdate()
    }

    @Override
    org.hibernate.query.Query<?> selectQuery() {
        return null
    }

}
