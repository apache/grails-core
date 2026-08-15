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

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Exercises {@link ListResultFinder} - the composed replacement for the deleted
 * FindAllByFinder/FindAllByBooleanFinder pair.
 */
class ListResultFinderSpec extends Specification {

    MappingContext mappingContext = Stub(MappingContext)
    PersistentEntity persistentEntity = Stub(PersistentEntity)
    Datastore datastore = Stub(Datastore) {
        getMappingContext() >> mappingContext
    }

    void setup() {
        mappingContext.getConversionService() >> new DefaultConversionService()
        mappingContext.getPersistentEntity(FinderTestEntity.name) >> persistentEntity
        persistentEntity.getMappingContext() >> mappingContext
        persistentEntity.getPropertyByName('name') >> Stub(org.grails.datastore.mapping.model.PersistentProperty) {
            getName() >> 'name'
            getType() >> String
        }
    }

    @Unroll
    void "findAllBy isMethodMatch('#methodName') == #matches"() {
        expect:
        ListResultFinder.findAllBy(datastore).isMethodMatch(methodName) == matches

        where:
        methodName            | matches
        'findAllByName'       | true
        'findAllByNameAndAge' | true
        'somethingElse'       | false
    }

    @Unroll
    void "findAllByBoolean isMethodMatch('#methodName') == #matches"() {
        expect:
        ListResultFinder.findAllByBoolean(datastore).isMethodMatch(methodName) == matches

        where:
        methodName            | matches
        'findAllActiveByName' | true
        'findAllActive'       | true
        'somethingElse'       | false
    }

    void "findAllBy runs the full round trip, applies distinct and returns the full list"() {
        given:
        List<FinderTestEntity> expected = [new FinderTestEntity(name: 'Bob')]
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            projections() >> projectionList
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session

        when:
        Object result = ListResultFinder.findAllBy(datastore).invoke(FinderTestEntity, 'findAllByName', ['Bob'] as Object[])

        then:
        result.is(expected)
        1 * projectionList.distinct()
        1 * query.list() >> expected
        0 * query.singleResult()
    }

    void "invoke(Class, methodName, DetachedCriteria, Object[]) merges the detached criteria onto the built query"() {
        given:
        // Called reflectively (dynamic Groovy dispatch, not part of FinderMethod) by
        // AbstractDetachedCriteria#methodMissing.
        List<FinderTestEntity> expected = [new FinderTestEntity(name: 'Bob')]
        Query.ProjectionList projectionList = Mock(Query.ProjectionList)
        Query query = Mock(Query) {
            getEntity() >> persistentEntity
            projections() >> projectionList
        }
        Session session = Stub(Session) {
            createQuery(FinderTestEntity) >> query
        }
        datastore.hasCurrentSession() >> true
        datastore.getCurrentSession() >> session
        grails.gorm.DetachedCriteria detachedCriteria = Stub(grails.gorm.DetachedCriteria) {
            getFetchStrategies() >> [:]
            getCriteria() >> [org.grails.datastore.mapping.query.Restrictions.eq('age', 42)]
            getProjections() >> []
            getOrders() >> []
        }

        when:
        Object result = ListResultFinder.findAllBy(datastore).invoke(FinderTestEntity, 'findAllByName', detachedCriteria, ['Bob'] as Object[])

        then:
        result.is(expected)
        1 * query.add({ it instanceof Query.PropertyCriterion })
        1 * projectionList.distinct()
        1 * query.list() >> expected
    }

    void "invoke throws IllegalStateException when constructed in stateless mode"() {
        when:
        ListResultFinder.findAllBy(mappingContext).invoke(FinderTestEntity, 'findAllByName', ['Bob'] as Object[])

        then:
        thrown(IllegalStateException)
    }
}
