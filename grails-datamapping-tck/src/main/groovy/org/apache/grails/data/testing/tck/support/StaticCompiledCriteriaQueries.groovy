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
package org.apache.grails.data.testing.tck.support

import grails.compiler.GrailsCompileStatic

import org.apache.grails.data.testing.tck.domains.TestEntity

/**
 * Criteria queries compiled with {@code @GrailsCompileStatic} so that the bytecode produced by
 * the {@code CriteriaTypeCheckingExtension} is executed by the TCK against every GORM
 * implementation. Calls declared on the {@code Criteria} API (eq, like, or, order) compile to
 * static delegate dispatch, while calls that are not on the API (projections, min, countDistinct,
 * property, maxResults and the {@code count} terminal chained on {@code createCriteria()}) only
 * compile because the extension falls back to dynamic dispatch — executing them here proves that
 * dispatch resolves against the criteria builder of the implementation under test.
 */
@GrailsCompileStatic
class StaticCompiledCriteriaQueries {

    static List listByNameLike(String pattern) {
        (List) TestEntity.createCriteria().list {
            like('name', pattern)
        }
    }

    static List listByNameLikeLimited(String pattern, int limit) {
        (List) TestEntity.createCriteria().list {
            like('name', pattern)
            maxResults(limit)
        }
    }

    static List listPaginatedOrderedByAge(int max, int offset) {
        (List) TestEntity.createCriteria().list(max: max, offset: offset) {
            order('age')
        }
    }

    static Number countByNameLike(String pattern) {
        (Number) TestEntity.createCriteria().count {
            like('name', pattern)
        }
    }

    static TestEntity getByName(String name) {
        (TestEntity) TestEntity.createCriteria().get {
            eq('name', name)
        }
    }

    static Number minAge() {
        (Number) TestEntity.createCriteria().get {
            projections {
                min('age')
            }
        }
    }

    static Number countDistinctAges() {
        (Number) TestEntity.createCriteria().get {
            projections {
                countDistinct('age')
            }
        }
    }

    static List namesMatching(String pattern) {
        (List) TestEntity.withCriteria {
            like('name', pattern)
            projections {
                property('name')
            }
        }
    }

    static List listByNameLikeOrAge(String pattern, int age) {
        (List) TestEntity.withCriteria {
            or {
                like('name', pattern)
                eq('age', age)
            }
            order('age', 'asc')
        }
    }
}
