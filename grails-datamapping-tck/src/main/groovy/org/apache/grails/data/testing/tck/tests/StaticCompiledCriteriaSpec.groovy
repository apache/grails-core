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
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.apache.grails.data.testing.tck.support.StaticCompiledCriteriaQueries

/**
 * Executes the criteria queries that {@link StaticCompiledCriteriaQueries} compiled with
 * {@code @GrailsCompileStatic} against the GORM implementation under test, verifying that the
 * dispatch produced by the {@code CriteriaTypeCheckingExtension} behaves identically to the
 * dynamically compiled queries covered by {@code CriteriaBuilderSpec}.
 */
class StaticCompiledCriteriaSpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        manager.registerDomainClasses(TestEntity, ChildEntity)
    }

    private static void seedPeople() {
        def age = 40
        ['Bob', 'Fred', 'Barney', 'Frank'].each {
            new TestEntity(name: it, age: age++, child: new ChildEntity(name: "$it Child")).save()
        }
    }

    void 'Test statically compiled list with a criteria closure on a chained terminal'() {
        given:
        seedPeople()

        when:
        def results = StaticCompiledCriteriaQueries.listByNameLike('B%')

        then:
        2 == results.size()
        results.every { it instanceof TestEntity }
    }

    void 'Test statically compiled list with a dynamically dispatched maxResults call'() {
        given:
        seedPeople()

        when:
        def results = StaticCompiledCriteriaQueries.listByNameLikeLimited('B%', 1)

        then:
        1 == results.size()
    }

    void 'Test statically compiled paginated list with named arguments and a chained terminal'() {
        given:
        seedPeople()

        when:
        def results = StaticCompiledCriteriaQueries.listPaginatedOrderedByAge(2, 1)

        then:
        2 == results.size()
        'Fred' == results[0].name
        'Barney' == results[1].name
    }

    void 'Test statically compiled count terminal chained on createCriteria()'() {
        given:
        seedPeople()

        when:
        def result = StaticCompiledCriteriaQueries.countByNameLike('B%')

        then:
        2 == result
    }

    void 'Test statically compiled get with an equals criterion'() {
        given:
        seedPeople()

        when:
        TestEntity result = StaticCompiledCriteriaQueries.getByName('Bob')

        then:
        result != null
        'Bob' == result.name
        40 == result.age
    }

    void 'Test statically compiled min projection'() {
        given:
        seedPeople()

        when:
        def result = StaticCompiledCriteriaQueries.minAge()

        then:
        40 == result
    }

    void 'Test statically compiled count distinct projection'() {
        given:
        seedPeople()
        new TestEntity(name: 'Chuck', age: 43, child: new ChildEntity(name: 'Chuckie')).save()

        when:
        def result = StaticCompiledCriteriaQueries.countDistinctAges()

        then:
        4 == result
    }

    void 'Test statically compiled property projection inside withCriteria'() {
        given:
        seedPeople()

        when:
        def results = StaticCompiledCriteriaQueries.namesMatching('B%')

        then:
        ['Barney', 'Bob'] == results.sort()
    }

    void 'Test statically compiled disjunction with ordering'() {
        given:
        seedPeople()

        when:
        def results = StaticCompiledCriteriaQueries.listByNameLikeOrAge('B%', 41)

        then:
        3 == results.size()
        'Bob' == results[0].name
        'Fred' == results[1].name
        'Barney' == results[2].name
    }
}
