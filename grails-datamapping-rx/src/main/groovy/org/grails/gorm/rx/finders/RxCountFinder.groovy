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

package org.grails.gorm.rx.finders

import java.util.regex.Pattern

import groovy.transform.CompileStatic

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.finders.CountFinder
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.DynamicFinderInvocation
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.RxDatastoreClient

/**
 * Implements {@code countBy*} for RxGORM - the rx-module mirror of {@link
 * org.grails.datastore.gorm.finders.CountFinder}, reusing that class's {@link
 * CountFinder#applyCriteriaAndCount} helper for the same independent (non-{@code getJunction})
 * And/Or criteria handling rather than duplicating it.
 */
@CompileStatic
class RxCountFinder implements FinderMethod {

    private static final Pattern METHOD_PATTERN = Pattern.compile('(countBy)(\\w+)')
    private static final String[] OPERATORS = ['And', 'Or'] as String[]

    private final RxDatastoreClient datastoreClient
    private final DynamicFinder grammar

    private RxCountFinder(RxDatastoreClient datastoreClient, DynamicFinder grammar) {
        this.datastoreClient = datastoreClient
        this.grammar = grammar
    }

    static RxCountFinder countBy(RxDatastoreClient datastoreClient) {
        new RxCountFinder(datastoreClient, new DynamicFinder(METHOD_PATTERN, OPERATORS, datastoreClient.mappingContext, false))
    }

    @Override
    void setPattern(String pattern) {
        grammar.setPattern(pattern)
    }

    @Override
    boolean isMethodMatch(String methodName) {
        grammar.isMethodMatch(methodName)
    }

    @Override
    Object invoke(Class clazz, String methodName, Object[] arguments) {
        invoke(clazz, methodName, (Closure) null, arguments)
    }

    @Override
    Object invoke(Class clazz, String methodName, Closure additionalCriteria, Object[] arguments) {
        DynamicFinderInvocation invocation = grammar.createFinderInvocation(clazz, methodName, additionalCriteria, arguments)
        doInvoke(clazz, invocation)
    }

    /**
     * Not part of {@link FinderMethod} - called reflectively (dynamic Groovy dispatch) by {@link
     * org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria#methodMissing}. See {@link
     * RxSingleResultFinder#invoke(Class, String, DetachedCriteria, Object[])} for the full
     * rationale, including why this is typed against core's {@link DetachedCriteria}.
     */
    Object invoke(Class clazz, String methodName, DetachedCriteria detachedCriteria, Object[] arguments) {
        DynamicFinderInvocation invocation = grammar.createFinderInvocation(clazz, methodName, null, arguments)
        if (detachedCriteria != null) {
            invocation.setDetachedCriteria(detachedCriteria)
        }
        doInvoke(clazz, invocation)
    }

    private Object doInvoke(Class clazz, DynamicFinderInvocation invocation) {
        Query query = datastoreClient.createQuery(clazz)
        query = CountFinder.applyCriteriaAndCount(invocation, clazz, query)
        query.singleResult()
    }
}
