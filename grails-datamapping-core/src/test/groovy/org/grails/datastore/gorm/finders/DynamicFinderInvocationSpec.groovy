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
package org.grails.datastore.gorm.finders

import grails.gorm.DetachedCriteria
import spock.lang.Specification

/**
 * DynamicFinderInvocation is an otherwise-immutable value object holding the pieces the
 * DynamicFinder classes need to build and invoke a query - this spec confirms the constructor
 * populates every getter, and that detachedCriteria (the one mutable field, wired in after
 * construction by {@code DynamicFinder.invoke(Class, String, DetachedCriteria, Object[])}) starts
 * out null and reflects whatever is later set on it.
 */
class DynamicFinderInvocationSpec extends Specification {

    void "constructor populates every getter"() {
        given:
        MethodExpression.Equal expression = new MethodExpression.Equal('name')
        Closure criteria = {}
        Object[] arguments = ['Bob'] as Object[]

        when:
        DynamicFinderInvocation invocation = new DynamicFinderInvocation(
                FinderTestEntity, 'findByName', arguments, [expression], criteria, 'And')

        then:
        invocation.javaClass == FinderTestEntity
        invocation.methodName == 'findByName'
        invocation.arguments.is(arguments)
        invocation.expressions == [expression]
        invocation.criteria.is(criteria)
        invocation.operator == 'And'
    }

    void "getDetachedCriteria is null until explicitly set"() {
        given:
        DynamicFinderInvocation invocation = new DynamicFinderInvocation(
                FinderTestEntity, 'findByName', [] as Object[], [], null, null)

        expect:
        invocation.detachedCriteria == null

        when:
        DetachedCriteria detachedCriteria = new DetachedCriteria(FinderTestEntity)
        invocation.setDetachedCriteria(detachedCriteria)

        then:
        invocation.detachedCriteria.is(detachedCriteria)
    }
}
