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

import org.hibernate.query.QueryFlushMode
import spock.lang.Specification
import spock.lang.Unroll

class HqlQueryMethodsSpec extends Specification {

    // Simple implementation to test default methods
    class TestQueryMethods implements HqlQueryMethods {}
    
    def queryMethods = new TestQueryMethods()

    void "test convertValue handles CharSequence"() {
        expect:
        HqlQueryMethods.convertValue(new StringBuilder("test")) == "test"
        HqlQueryMethods.convertValue("plain string") == "plain string"
    }

    void "test convertValue handles Collections recursively"() {
        given:
        def input = [new StringBuilder("a"), [new StringBuilder("b"), "c"]]

        when:
        def result = HqlQueryMethods.convertValue(input)

        then:
        result == ["a", ["b", "c"]]
        result.every { it instanceof String || it instanceof List }
    }

    void "test convertValue handles Arrays"() {
        given:
        def input = [new StringBuilder("a"), new StringBuilder("b")] as CharSequence[]

        when:
        def result = HqlQueryMethods.convertValue(input)

        then:
        result instanceof String[]
        result == ["a", "b"] as String[]
    }

    void "test populateQuerySettings"() {
        given:
        def delegate = Mock(HqlQueryDelegate)
        def settings = [
            (HibernateQueryArgument.FLUSH_MODE.value()): "COMMIT",
            (HibernateQueryArgument.MAX.value()): 10,
            (HibernateQueryArgument.OFFSET.value()): 5,
            (HibernateQueryArgument.READ_ONLY.value()): true
        ]

        when:
        queryMethods.populateQuerySettings(delegate, settings)

        then:
        1 * delegate.setQueryFlushMode(QueryFlushMode.NO_FLUSH)
        1 * delegate.setMaxResults(10)
        1 * delegate.setFirstResult(5)
        1 * delegate.setReadOnly(true)
    }

    void "test populateParameters with named parameters"() {
        given:
        def delegate = Mock(HqlQueryDelegate)
        def ctx = new HqlQueryContext("hql", Object, [name: "Test", ages: [20, 30], tags: ["a", "b"] as String[]], null, [:], false, false)

        when:
        HqlQueryMethods.populateParameters(delegate, ctx)

        then:
        1 * delegate.setParameter("name", "Test")
        1 * delegate.setParameterList("ages", [20, 30])
        1 * delegate.setParameterList("tags", ["a", "b"] as Object[])
    }

    void "test populateParameters filters internal settings"() {
        given:
        def delegate = Mock(HqlQueryDelegate)
        def ctx = new HqlQueryContext("hql", Object, [(HibernateQueryArgument.MAX.value()): 10, title: "GORM"], null, [:], false, false)

        when:
        HqlQueryMethods.populateParameters(delegate, ctx)

        then:
        0 * delegate.setParameter(HibernateQueryArgument.MAX.value(), _)
        1 * delegate.setParameter("title", "GORM")
    }

    void "test populateParameters with positional parameters"() {
        given:
        def delegate = Mock(HqlQueryDelegate)
        def ctx = new HqlQueryContext("hql", Object, [:], ["First", 2], [:], false, false)

        when:
        HqlQueryMethods.populateParameters(delegate, ctx)

        then:
        1 * delegate.setParameter(1, "First")
        1 * delegate.setParameter(2, 2)
    }
}
