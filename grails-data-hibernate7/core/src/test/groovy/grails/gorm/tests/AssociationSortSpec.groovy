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

import grails.gorm.tests.entities.Club
import grails.gorm.tests.entities.Contract
import grails.gorm.tests.entities.Player
import grails.gorm.tests.entities.Team
import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * A sort by a property of an association must not leave out the instances whose association is null.
 */
class AssociationSortSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    void setupSpec() {
        manager.registerDomainClasses(Club, Team, Player, Contract)
    }

    void setup() {
        ['Manchester United': 'Bruno', 'Arsenal': 'Bukayo', 'Chelsea': 'Cole'].each { clubName, playerName ->
            final club = new Club(name: clubName).save()
            final team = new Team(club: club, name: "${clubName} First Team").save()
            new Player(name: playerName, team: team, contract: new Contract(salary: 1)).save()
        }
        final team = new Team(name: 'Unattached First Team').save(failOnError: true)
        new Player(name: 'Unattached Player', team: team, contract: new Contract(salary: 1)).save(failOnError: true)
        manager.session.flush()
        manager.session.clear()
    }

    void 'list sorted by #sort keeps a team without a club'() {
        when:
        final results = Team.list(sort: sort)

        then:
        'Unattached First Team' in results*.name

        and: 'the other teams are sorted by their club'
        results*.name - 'Unattached First Team' == expected

        where:
        sort        || expected
        'club.name' || ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        'club.id'   || ['Manchester United First Team', 'Arsenal First Team', 'Chelsea First Team']
    }

    void 'list sorted by a nested association property keeps a player whose team has no club'() {
        when:
        final results = Player.list(sort: 'team.club.name', order: 'desc')

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['Bruno', 'Cole', 'Bukayo']
    }

    void 'list sorted by a map sorts by every entry of the map'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = Team.list(sort: ['club.name': 'asc', name: 'desc'])

        then:
        results*.name - 'Unattached First Team' == ['Arsenal Second Team', 'Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }

    void 'a paged list sorted by an association property returns the teams that it counts'() {
        when:
        final results = Team.list(sort: 'club.name', max: 10)

        then:
        results.size() == 4
        results.totalCount == 4
    }

    void 'findAllWhere sorted by an association property finds a team without a club'() {
        expect:
        Team.findAllWhere([name: 'Unattached First Team'], [sort: 'club.name'])*.name == ['Unattached First Team']
    }

    void 'findWhere sorted by an association property finds a team without a club'() {
        expect:
        Team.findWhere([name: 'Unattached First Team'], [sort: 'club.name'])?.name == 'Unattached First Team'
    }

    void 'a criteria query with pagination arguments sorted by an association property keeps a team without a club'() {
        when:
        final results = Team.createCriteria().list(sort: 'club.name', max: 10) {
            like('name', '%First%')
        }

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        results.totalCount == 4
    }

    void 'a criteria query with pagination arguments sorted by a nested association property keeps a player whose team has no club'() {
        when:
        final results = Player.createCriteria().list(sort: 'team.club.name', max: 10) {
            like('name', '%')
        }

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['Bukayo', 'Cole', 'Bruno']
        results.totalCount == 4
    }

    void 'a criteria query with pagination arguments sorted by a nested association property reuses a join of the criteria'() {
        when:
        final results = Player.createCriteria().list(sort: 'team.club.name', order: 'desc', max: 10) {
            team {
                like('name', '%First%')
            }
        }

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['Bruno', 'Cole', 'Bukayo']
        results.totalCount == 4
    }

    void 'an order by an association property in a criteria query keeps a team without a club'() {
        when:
        final results = Team.createCriteria().list {
            like('name', '%First%')
            order('club.name')
        }

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }

    void 'an order inside an association block of a criteria query sorts by the property of the association'() {
        when:
        final results = Team.withCriteria {
            club {
                order('name', direction)
            }
        }

        then: 'the block joins the association with an inner join'
        results*.name == expected

        where:
        direction || expected
        'asc'     || ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        'desc'    || ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'a dynamic finder sorted by an association property keeps a team without a club'() {
        when:
        final results = Team.findAllByNameLike('%First%', [sort: 'club.name'])

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }
}
