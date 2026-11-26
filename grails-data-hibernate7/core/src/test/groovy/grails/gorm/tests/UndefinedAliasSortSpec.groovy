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

class UndefinedAliasSortSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Club, Team)
    }

    void setup() {
        ['Manchester United', 'Arsenal', 'Chelsea'].each { clubName ->
            final club = new Club(name: clubName).save(failOnError: true)
            new Team(club: club, name: "${clubName} First Team").save(failOnError: true)
        }
        session.flush()
        session.clear()
    }

    void 'an invalid sort is rejected consistently: #description'() {
        when:
        query.call()

        then:
        final exception = thrown(IllegalArgumentException)
        exception.message == 'Invalid sort property'

        where:
        description                         | query
        'where with an association'         | { Team.where { club.name ==~ '%e%' }.list(sort: 'x.name') }
        'where with a different alias'      | { Team.where { def c = club; c.name ==~ '%e%' }.list(sort: 'x.name') }
        'static list'                       | { Team.list(sort: 'x.name') }
        'dynamic finder'                    | { Team.findAllByNameLike('%', [sort: 'x.name']) }
        'case-sensitive where sort'         | { Team.where { name != null }.list(sort: 'x.name', ignoreCase: false) }
        'detached criteria order'           | { new DetachedCriteria(Team).build { order('x.name') }.list() }
        'detached criteria argument'        | { new DetachedCriteria(Team).list(sort: 'x.name') }
        'findAll by an empty example'       | { Team.findAll(new Team(), [sort: 'x.name']) }
        'find by an empty example'          | { Team.find(new Team(), [sort: 'x.name']) }
        'findAll by a populated example'    | { Team.findAll(new Team(name: 'Arsenal First Team'), [sort: 'x.name']) }
        'criteria sort argument'            | { Team.createCriteria().list(sort: 'x.name') { } }
        'criteria order'                    | { Team.createCriteria().list { order('x.name') } }
        'where stored sort'                 | { Team.where { name != null }.sort('x.name').list() }
        'count with a stored sort'          | { Team.where { name != null }.sort('x.name').count() }
        'get with a stored sort'            | { Team.where { name != null }.sort('x.name').get() }
        'descending paged where query'      | { Team.where { name != null }.list(sort: 'x.name', order: 'desc', max: 2) }
        'static sort map'                   | { Team.list(sort: ['x.name': 'desc']) }
        'where sort map'                    | { Team.where { name != null }.list(sort: ['x.name': 'desc']) }
        'invalid association property'      | { new DetachedCriteria(Team).list(sort: 'club.unknown') }
        'invalid nested association path'   | { new DetachedCriteria(Team).list(sort: 'club.x.name') }
    }

    void 'declared association aliases still sort'() {
        when:
        final byWhereAlias = Team.where { def c = club; c.name ==~ '%e%' }.list(sort: 'c.name')
        final byCriteriaAlias = Team.createCriteria().list(sort: 'c.name', order: 'desc', max: 2) {
            createAlias('club', 'c')
        }

        then:
        byWhereAlias*.name == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        byCriteriaAlias*.name == ['Manchester United First Team', 'Chelsea First Team']
    }

    void 'a valid sort on an empty example keeps the existing result'() {
        expect:
        Team.findAll(new Team(), [sort: 'name']) == null
        Team.find(new Team(), [sort: 'club.name']) == null
    }
}
