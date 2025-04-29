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
package grails.gorm.tests

import grails.gorm.DetachedCriteria
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.orm.hibernate.query.HibernateQuery

import jakarta.persistence.criteria.JoinType

class DetachedCriteriaJoinSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Team, Club])
    }

    def "check if count works as expected"() {
        given:
        new Club(name: "Real Madrid").save()
        new Club(name: "Barcelona").save()
        new Club(name: "Chelsea").save()
        new Club(name: "Manchester United").save()

        expect: "max and offset should always be ignored when calling count()"
        Club.where {}.max(10).offset(0).count() == 4
        new DetachedCriteria<>(Club).max(10).offset(0).count() == 4
        Club.where {}.max(2).offset(0).count() == 4
        new DetachedCriteria<>(Club).max(2).offset(0).count() == 4
        Club.where {}.max(10).offset(10).count() == 4
        new DetachedCriteria<>(Club).max(10).offset(10).count() == 4
    }

    def 'check if inner join is applied correctly'() {
        given:
        def dc = new DetachedCriteria(Team).build {
            join('club', JoinType.INNER)
            createAlias('club', 'c')
        }
        HibernateQuery query = manager.session.createQuery(Team)

        DynamicFinder.applyDetachedCriteria(query, dc)
        def joinType = query.hibernateCriteria.subcriteriaList.first().joinType
        expect:
        joinType == org.hibernate.sql.JoinType.INNER_JOIN
    }

    def 'check if left join is applied correctly'() {
        given:
        def dc = new DetachedCriteria(Team).build {
            join('club', JoinType.LEFT)
            createAlias('club', 'c')
        }
        HibernateQuery query = manager.session.createQuery(Team)

        DynamicFinder.applyDetachedCriteria(query, dc)
        def joinType = query.hibernateCriteria.subcriteriaList.first().joinType
        expect:
        joinType == org.hibernate.sql.JoinType.LEFT_OUTER_JOIN
    }

    def 'check if right join is applied correctly'() {
        given:
        def dc = new DetachedCriteria(Team).build {
            join('club', JoinType.RIGHT)
            createAlias('club', 'c')
        }
        HibernateQuery query = manager.session.createQuery(Team)

        DynamicFinder.applyDetachedCriteria(query, dc)
        def joinType = query.hibernateCriteria.subcriteriaList.first().joinType
        expect:
        joinType == org.hibernate.sql.JoinType.RIGHT_OUTER_JOIN
    }
}
