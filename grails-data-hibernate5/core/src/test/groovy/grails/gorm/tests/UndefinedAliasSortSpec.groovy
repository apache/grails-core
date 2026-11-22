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
import grails.gorm.tests.entities.Club
import grails.gorm.tests.entities.Team
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * A sort by an alias that the query does not define is rejected, like any other invalid sort property.
 */
class UndefinedAliasSortSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.registerDomainClasses(Club, Team)
    }

    void setup() {
        ['Manchester United', 'Arsenal', 'Chelsea'].each { clubName ->
            final club = new Club(name: clubName).save()
            new Team(club: club, name: "${clubName} First Team").save()
        }
        manager.session.flush()
        manager.session.clear()
    }

    void 'a sort by an alias that the query does not define is rejected: #description'() {
        when:
        query.call()

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Invalid sort property'

        where:
        description                                   | query
        'a where query with a criterion on the club'  | { Team.where { club.name ==~ '%e%' }.list(sort: 'c1.name') }
        'a where query that defines another alias'    | { Team.where { def c1 = club; c1.name ==~ '%e%' }.list(sort: 'c2.name') }
        'a list without any criterion'                | { Team.list(sort: 'c1.name') }
        'a dynamic finder'                            | { Team.findAllByNameLike('%', [sort: 'c1.name']) }
        'a list that does not ignore case'            | { Team.list(sort: 'c1.name', ignoreCase: false) }
        'an order added to a detached criteria'       | { new DetachedCriteria(Team).build { order('c1.name') }.list() }
        'a detached criteria that names the team'     | { new DetachedCriteria(Team, 't').list(sort: 'c1.name') }
        'findAll by example'                          | { Team.findAll(new Team(), [sort: 'c1.name']) }
        'the sort argument of a criteria query'       | { Team.createCriteria().list(sort: 'c1.name') { } }
        'a count with a stored sort'                  | { Team.where { name != null }.sort('c1.name').count() }
        'a get with a stored sort'                    | { Team.where { name != null }.sort('c1.name').get() }
        'a descending paged list'                     | { Team.list(sort: 'c1.name', order: 'desc', max: 2) }
        'a map of sorts'                             | { Team.list(sort: ['c1.name': 'desc']) }
        'an invalid association property'            | { new DetachedCriteria(Team).list(sort: 'club.unknown') }
        'an invalid nested association path'         | { new DetachedCriteria(Team).list(sort: 'club.x.name') }
    }

    void 'a sort by an alias that the query defines still sorts'() {
        when:
        final results = Team.where { def c1 = club; c1.name ==~ '%e%' }.list(sort: 'c1.name')

        then:
        results*.name == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }

    void 'a sort by an alias that a criteria query defines is not rejected'() {
        when:
        final results = Team.createCriteria().list(sort: 'c.name', max: 10) {
            createAlias('club', 'c')
        }

        then:
        results.size() == 3
    }

    void 'a sort that starts with the alias of the queried class is not rejected'() {
        expect:
        new DetachedCriteria(Team, 't').list(sort: 't.name').size() == 3
    }
}
