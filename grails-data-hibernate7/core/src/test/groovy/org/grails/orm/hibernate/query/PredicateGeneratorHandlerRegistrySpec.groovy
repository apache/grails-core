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

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.query.Query

/**
 * Pins which handler {@code PredicateGenerator} resolves for each builtin criterion: a criterion class is served by
 * the nearest registered class on its superclass chain.
 */
class PredicateGeneratorHandlerRegistrySpec extends Specification {

    @Shared
    Method findHandler

    void setupSpec() {
        findHandler = PredicateGenerator.getDeclaredMethod('findHandler', Class)
        findHandler.accessible = true
    }

    private Object handlerFor(Class type) {
        findHandler.invoke(null, type)
    }

    private static List<Class> criterionClasses() {
        Query.declaredClasses.findAll {
            Query.Criterion.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers)
        }
    }

    @Unroll
    void 'every builtin criterion #type.simpleName has a handler'() {
        expect:
        handlerFor(type) != null

        where:
        type << criterionClasses()
    }

    @Unroll
    void 'the handler of #type.simpleName comes from #expected.simpleName'() {
        expect:
        handlerFor(type).getClass() == expected

        where:
        type                                    | expected
        Query.Conjunction                       | JunctionCriterionHandler
        Query.Disjunction                       | JunctionCriterionHandler
        Query.Negation                          | JunctionCriterionHandler
        Query.EqualsAll                         | SubqueryCriterionHandler
        Query.NotIn                             | SubqueryCriterionHandler
        Query.IsNull                            | PropertyNameCriterionHandler
        Query.IsNotEmpty                        | PropertyNameCriterionHandler
        Query.EqualsProperty                    | PropertyComparisonCriterionHandler
        Query.LessThanEqualsProperty            | PropertyComparisonCriterionHandler
        Query.Equals                            | PropertyCriterionHandler
        Query.ILike                             | PropertyCriterionHandler
        Query.Between                           | PropertyCriterionHandler
        Query.SizeLessThanEquals                | PropertyCriterionHandler
        EqualsIgnoreCase                        | PropertyCriterionHandler
    }
}
