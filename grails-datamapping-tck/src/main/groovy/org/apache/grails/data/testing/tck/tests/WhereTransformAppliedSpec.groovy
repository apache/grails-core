/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.WqFoo
import spock.lang.Unroll

import org.grails.datastore.mapping.query.Query

/**
 * Proves that where-query closures in the TCK sources were rewritten into criteria at compile time.
 * Without the where-query AST transformation a closure such as {@code { bar == null }} compiles to a plain
 * comparison that builds no criterion, and a query asserted only by its result can pass without the
 * criteria translation being exercised. Reading the criteria the query holds fails by name instead.
 */
class WhereTransformAppliedSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.registerDomainClasses(WqFoo)
    }

    void "a comparison with null becomes an IsNull criterion"() {
        when:
        List<Query.Criterion> criteria = WqFoo.where { bar == null }.criteria

        then:
        criteria.size() == 1
        criteria[0] instanceof Query.IsNull
        (criteria[0] as Query.IsNull).property == 'bar'
    }

    void "an inequality with null becomes an IsNotNull criterion"() {
        when:
        List<Query.Criterion> criteria = WqFoo.where { bar != null }.criteria

        then:
        criteria.size() == 1
        criteria[0] instanceof Query.IsNotNull
        (criteria[0] as Query.IsNotNull).property == 'bar'
    }

    @Unroll
    void "a comparison with a value becomes a #type criterion"() {
        when:
        List<Query.Criterion> criteria = query.call().criteria

        then:
        criteria.size() == 1
        criteria[0].class == type
        (criteria[0] as Query.PropertyCriterion).property == 'bar'
        (criteria[0] as Query.PropertyCriterion).value == 'x'

        where:
        type              | query
        Query.Equals      | { WqFoo.where { bar == 'x' } }
        Query.NotEquals   | { WqFoo.where { bar != 'x' } }
        Query.GreaterThan | { WqFoo.where { bar > 'x' } }
        Query.LessThan    | { WqFoo.where { bar < 'x' } }
    }

    void "the criteria are built when the query is declared, before any datastore is touched"() {
        when:
        def declared = WqFoo.where { bar == null }

        then:
        declared.criteria.size() == 1
        WqFoo.count() == 0
    }
}
