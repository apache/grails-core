/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.query.criteria

import grails.gorm.DetachedCriteria
import org.grails.datastore.mapping.core.connections.ConnectionSource
import spock.lang.Specification

class DetachedCriteriaCloneSpec extends Specification {

    void 'clone preserves connectionName'() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@connectionName = 'secondary'

        when:
        def cloned = criteria.clone()

        then:
        cloned.@connectionName == 'secondary'
    }

    void 'clone preserves lazyQuery'() {
        given:
        def lazy = { -> }
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@lazyQuery = lazy

        when:
        def cloned = criteria.clone()

        then:
        cloned.@lazyQuery.is(lazy)
    }

    void 'clone preserves associationCriteriaMap'() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        def placeholder = 'marker'
        criteria.@associationCriteriaMap['books'] = placeholder

        when:
        def cloned = criteria.clone()

        then:
        cloned.@associationCriteriaMap.size() == 1
        cloned.@associationCriteriaMap['books'].is(placeholder)
    }

    void 'clone creates independent copy of associationCriteriaMap'() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@associationCriteriaMap['books'] = 'marker'

        when:
        def cloned = criteria.clone()
        cloned.@associationCriteriaMap['authors'] = 'another'

        then:
        criteria.@associationCriteriaMap.size() == 1
        cloned.@associationCriteriaMap.size() == 2
    }

    void 'withConnection followed by max preserves connectionName'() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        def withConn = criteria.withConnection('secondary')
        def withMax = withConn.max(10)

        then:
        withConn.@connectionName == 'secondary'
        withMax.@connectionName == 'secondary'
        withMax.defaultMax == 10
    }

    void 'clone preserves default connectionName'() {
        given:
        def criteria = new DetachedCriteria(TestEntity)

        when:
        def cloned = criteria.clone()

        then:
        cloned.@connectionName == ConnectionSource.DEFAULT
    }

    void 'clone preserves existing fields'() {
        given:
        def criteria = new DetachedCriteria(TestEntity)
        criteria.@defaultMax = 50
        criteria.@defaultOffset = 10

        when:
        def cloned = criteria.clone()

        then:
        cloned.defaultMax == 50
        cloned.defaultOffset == 10
    }
}

class TestEntity {

    Long id
    String name
}
