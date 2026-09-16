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
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Restrictions
import org.grails.datastore.rx.RxDatastoreClient
import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Specification

class FindByFinderSpec extends Specification {

    RxDatastoreClient datastoreClient = Mock()
    MappingContext mappingContext = Mock()
    PersistentEntity entity = Mock()
    Query query = Mock()

    def setup() {
        entity.getMappingContext() >> mappingContext
        entity.getJavaClass() >> Book
        mappingContext.getConversionService() >> new DefaultConversionService()
        query.getEntity() >> entity
    }

    def "obtains its mapping context from the datastore client when constructed"() {
        when:
        def finder = new FindByFinder(datastoreClient)

        then:
        1 * datastoreClient.getMappingContext() >> mappingContext
        finder.datastoreClient.is(datastoreClient)
    }

    def "finds by building and executing a query via the RX datastore client instead of a session"() {
        given:
        def finder = new FindByFinder(datastoreClient)
        def titleExpression = new MethodExpression.Equal(Book, 'title')
        titleExpression.setArguments(['Shogun'] as Object[])
        def invocation = new DynamicFinderInvocation(Book, 'findByTitle', [] as Object[], [titleExpression], null, null)

        when:
        def result = finder.doInvokeInternal(invocation)

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.Junction })
        1 * query.singleResult() >> new Book(title: 'Shogun')
        result.title == 'Shogun'
    }

    def "applies detached criteria to the query when present"() {
        given:
        def finder = new FindByFinder(datastoreClient)
        def invocation = new DynamicFinderInvocation(Book, 'findByTitle', [] as Object[], [], null, null)
        def detachedCriteria = Stub(grails.gorm.DetachedCriteria) {
            getFetchStrategies() >> [:]
            getCriteria() >> [Restrictions.eq('author', 'Clavell')]
            getProjections() >> []
            getOrders() >> []
        }
        invocation.setDetachedCriteria(detachedCriteria)

        when:
        finder.doInvokeInternal(invocation)

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.add({ Query.Criterion it -> it instanceof Query.PropertyCriterion })
        1 * query.add({ Query.Criterion it -> it instanceof Query.Junction })
        1 * query.singleResult()
    }

    def "applies additional criteria to the query when present"() {
        given:
        def finder = new FindByFinder(datastoreClient)
        def invocation = new DynamicFinderInvocation(Book, 'findByTitle', [] as Object[], [], { -> }, null)
        def session = Stub(Session) {
            getMappingContext() >> mappingContext
        }
        mappingContext.getPersistentEntity(_) >> entity
        query.getSession() >> session

        when:
        finder.doInvokeInternal(invocation)

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.getSession() >> session
        1 * query.singleResult()
        noExceptionThrown()
    }

    def "applies query arguments to the query when present"() {
        given:
        def finder = new FindByFinder(datastoreClient)
        def invocation = new DynamicFinderInvocation(Book, 'findByTitle', [[max: 5]] as Object[], [], null, null)

        when:
        finder.doInvokeInternal(invocation)

        then:
        1 * datastoreClient.createQuery(Book) >> query
        1 * query.max(5)
        1 * query.singleResult()
    }

    private static class Book {
        String title
        String author
    }
}
