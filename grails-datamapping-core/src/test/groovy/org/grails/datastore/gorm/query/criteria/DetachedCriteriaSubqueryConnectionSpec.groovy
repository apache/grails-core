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
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.query.Query
import spock.lang.Specification

class DetachedCriteriaSubqueryConnectionSpec extends Specification {

    private static DetachedCriteria subquery() {
        new DetachedCriteria(TestEntity)
    }

    void "a subquery added before withConnection runs on the connection of the criteria that holds it"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        def sub = subquery()
        outer.add(new Query.GreaterThan('id', sub))

        when:
        def archive = outer.withConnection('archive')

        then:
        archive.criteria[0].value.@connectionName == 'archive'
    }

    void "a subquery added after withConnection runs on the connection of the criteria that holds it"() {
        given:
        def archive = new DetachedCriteria(TestEntity).withConnection('archive')

        when:
        archive.add(new Query.Equals('id', subquery()))

        then:
        archive.criteria[0].value.@connectionName == 'archive'
    }

    void "the criteria withConnection was called on keeps its own criterion and subquery"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        def sub = subquery()
        def criterion = new Query.LessThan('id', sub)
        outer.add(criterion)

        when:
        outer.withConnection('archive')

        then:
        outer.criteria[0].is(criterion)
        criterion.value.is(sub)
        sub.@connectionName == ConnectionSource.DEFAULT
    }

    void "a subquery built with its own connection keeps it"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        outer.add(new Query.GreaterThanEquals('id', subquery().withConnection('reports')))

        when:
        def archive = outer.withConnection('archive')

        then:
        archive.criteria[0].value.@connectionName == 'reports'
    }

    void "a subquery that took the connection of the criteria follows it back to the default connection"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        outer.add(new Query.GreaterThan('id', subquery()))

        when:
        def back = outer.withConnection('archive').withConnection(ConnectionSource.DEFAULT)

        then:
        back.criteria[0].value.@connectionName == ConnectionSource.DEFAULT
    }

    void "a subquery that chose the default connection keeps it when the criteria moves to another one"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        outer.add(new Query.GreaterThan('id', subquery().withConnection(ConnectionSource.DEFAULT)))

        when:
        def archive = outer.withConnection('archive')

        then:
        archive.criteria[0].value.@connectionName == ConnectionSource.DEFAULT
    }

    void "a subquery that took a connection moves again when the criteria moves to a third connection"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        outer.add(new Query.GreaterThan('id', subquery()))

        when:
        def reports = outer.withConnection('archive').withConnection('reports')

        then:
        reports.criteria[0].value.@connectionName == 'reports'
    }

    void "every comparison keeps its form and property when its subquery moves"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        outer.add(criterion.newInstance('id', subquery()))

        when:
        def archive = outer.withConnection('archive')

        then:
        archive.criteria[0].class == criterion
        archive.criteria[0].property == 'id'
        archive.criteria[0].value.@connectionName == 'archive'

        where:
        criterion << [Query.Equals, Query.NotEquals, Query.GreaterThan, Query.GreaterThanEquals,
                      Query.LessThan, Query.LessThanEquals]
    }

    void "in and notIn against a subquery move with the criteria"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        outer.add(new Query.In('id', subquery()))
        outer.add(new Query.NotIn('id', subquery()))

        when:
        def archive = outer.withConnection('archive')

        then:
        archive.criteria[0] instanceof Query.In
        archive.criteria[0].subquery.@connectionName == 'archive'
        archive.criteria[1] instanceof Query.NotIn
        archive.criteria[1].subquery.@connectionName == 'archive'
    }

    void "subqueries inside or, and and not move with the criteria"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        outer.add(new Query.Disjunction([new Query.Equals('id', subquery()),
                                         new Query.Negation().add(new Query.LessThan('id', subquery()))]))

        when:
        def archive = outer.withConnection('archive')
        def either = archive.criteria[0]
        def negation = either.criteria[1]

        then:
        either instanceof Query.Disjunction
        either.criteria[0].value.@connectionName == 'archive'
        negation instanceof Query.Negation
        negation.criteria[0].value.@connectionName == 'archive'
    }

    void "a comparison with a plain value is untouched"() {
        given:
        def outer = new DetachedCriteria(TestEntity)
        def criterion = new Query.Equals('name', 'Bart')
        outer.add(criterion)

        expect:
        outer.withConnection('archive').criteria[0].is(criterion)
    }
}
