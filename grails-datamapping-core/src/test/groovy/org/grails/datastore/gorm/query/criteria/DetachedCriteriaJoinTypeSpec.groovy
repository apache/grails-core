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
package org.grails.datastore.gorm.query.criteria

import grails.gorm.DetachedCriteria
import spock.lang.Specification

import jakarta.persistence.FetchType
import jakarta.persistence.criteria.JoinType

class DetachedCriteriaJoinTypeSpec extends Specification {

    void "join with a join type records the join type"() {
        when:
        def criteria = new DetachedCriteria(TestEntity).join('name', JoinType.INNER)

        then:
        criteria.fetchStrategies == [name: FetchType.EAGER]
        criteria.joinTypes == [name: JoinType.INNER]
        criteria.supersededJoinTypes.isEmpty()
    }

    void "a later join without a join type replaces a join type that was requested before"() {
        when:
        def criteria = new DetachedCriteria(TestEntity).join('name', JoinType.INNER).join('name')

        then:
        criteria.fetchStrategies == [name: FetchType.EAGER]
        criteria.joinTypes.isEmpty()

        and: 'the join type is kept as one that the fetch mode replaced'
        criteria.supersededJoinTypes == [name: JoinType.INNER]
    }

    void "a later select replaces a join type that was requested before"() {
        when:
        def criteria = new DetachedCriteria(TestEntity).join('name', JoinType.INNER).select('name')

        then:
        criteria.fetchStrategies == [name: FetchType.LAZY]
        criteria.joinTypes.isEmpty()
        criteria.supersededJoinTypes == [name: JoinType.INNER]
    }

    void "a join type that is requested again after a fetch mode is no longer superseded"() {
        when:
        def criteria = new DetachedCriteria(TestEntity).join('name', JoinType.INNER).join('name').join('name', JoinType.LEFT)

        then:
        criteria.joinTypes == [name: JoinType.LEFT]
        criteria.supersededJoinTypes.isEmpty()
    }

    void "a copy of the criteria keeps the superseded join types"() {
        given:
        def criteria = new DetachedCriteria(TestEntity).join('name', JoinType.INNER).join('name')

        when:
        def copy = criteria.clone()
        criteria.join('name', JoinType.LEFT)

        then:
        copy.supersededJoinTypes == [name: JoinType.INNER]
        copy.joinTypes.isEmpty()
    }
}
