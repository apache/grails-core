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

import org.grails.datastore.gorm.finders.DynamicFinderInvocation
import org.grails.datastore.gorm.finders.MethodExpression
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.query.RxQuery
import org.springframework.core.convert.support.DefaultConversionService
import rx.Observable
import spock.lang.Specification

class FindAllByBooleanFinderSpec extends Specification {

    RxDatastoreClient datastoreClient = Mock()
    MappingContext mappingContext = Mock()
    PersistentEntity entity = Mock()
    RxCapableQuery query = Mock()

    def setup() {
        entity.getMappingContext() >> mappingContext
        entity.getJavaClass() >> Person
        mappingContext.getConversionService() >> new DefaultConversionService()
        query.getEntity() >> entity
    }

    def "overrides the inherited pattern to match boolean style method names"() {
        when:
        def finder = new FindAllByBooleanFinder(datastoreClient)

        then:
        1 * datastoreClient.getMappingContext() >> mappingContext
        finder.isMethodMatch('findAllActive')
    }

    def "firstExpressionIsRequiredBoolean always reports true"() {
        given:
        def finder = new FindAllByBooleanFinder(datastoreClient)

        expect:
        finder.firstExpressionIsRequiredBoolean()
    }

    def "queries all results by the boolean property, combining it as a required expression with the rest via Or"() {
        given:
        def finder = new FindAllByBooleanFinder(datastoreClient)
        def activeExpression = new MethodExpression.Equal(Person, 'active')
        activeExpression.setArguments([true] as Object[])
        def nameExpression = new MethodExpression.Equal(Person, 'name')
        nameExpression.setArguments(['Fred'] as Object[])
        def invocation = new DynamicFinderInvocation(Person, 'findAllActiveByNameOrCity', [] as Object[],
                [activeExpression, nameExpression], null, 'Or')
        def observable = Observable.just(new Person(name: 'Fred', active: true))

        when:
        def result = finder.doInvokeInternal(invocation)

        then:
        1 * datastoreClient.createQuery(Person) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.Conjunction && ((Query.Junction) it).criteria.size() == 2 })
        1 * query.findAll() >> observable
        0 * query.findAll(_)
        result.is(observable)
    }

    private static class Person {
        String name
        boolean active
    }

    private static abstract class RxCapableQuery extends Query implements RxQuery {
        protected RxCapableQuery() {
            super(null, null)
        }
    }
}
