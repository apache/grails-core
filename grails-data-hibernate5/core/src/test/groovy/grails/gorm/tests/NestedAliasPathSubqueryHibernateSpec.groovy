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

import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * A subquery of a where query that names a nested path of the alias of the enclosing query, {@code t.club.id},
 * is compared through the association of that alias. It used to throw a MissingPropertyException because the
 * transform left {@code t.club.id} as an expression on the class.
 */
@Issue('https://github.com/apache/grails-core/issues/16813')
class NestedAliasPathSubqueryHibernateSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Club, Team])
    }

    void setup() {
        def united = new Club(name: 'Manchester United').save(failOnError: true)
        def arsenal = new Club(name: 'Arsenal').save(failOnError: true)
        new Team(club: united, name: 'United First Team').save(failOnError: true)
        new Team(club: united, name: 'United Reserves').save(failOnError: true)
        new Team(club: arsenal, name: 'Arsenal First Team').save(flush: true, failOnError: true)
        manager.session.clear()
    }

    void "a subquery compares with the id reached through an association of the enclosing alias"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id == t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then: 'only the teams of the club the subquery names are returned'
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery compares with the association of the enclosing alias"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id == t.club && name == 'Arsenal' }.property('name'))
        }.list()

        then:
        teams*.name == ['Arsenal First Team']
    }
}
