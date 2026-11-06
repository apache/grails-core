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

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.query.Query
import org.hibernate.FetchMode
import org.hibernate.Hibernate
import org.hibernate.criterion.Order

/**
 * A sort by a property of an association must not leave out the instances whose association is null,
 * and must not change what the query fetches.
 */
class AssociationSortSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Club, Team, Player, Contract, AssociationSortLeague])
    }

    void setup() {
        final manUtd = new Club(name: 'Manchester United').save()
        final arsenal = new Club(name: 'Arsenal').save()
        final chelsea = new Club(name: 'Chelsea').save()
        savePlayer('MU Player', new Team(club: manUtd, name: 'MU First Team').save())
        savePlayer('Arsenal Player', new Team(club: arsenal, name: 'Arsenal First Team').save())
        savePlayer('Chelsea Player', new Team(club: chelsea, name: 'Chelsea First Team').save())
        savePlayer('Unattached Player', new Team(name: 'Unattached First Team').save(failOnError: true))
    }

    private static void savePlayer(String name, Team team) {
        final player = new Player(name: name, team: team)
        player.contract = new Contract(salary: 1000G, player: player)
        player.save(failOnError: true, flush: true)
    }

    private void saveSquad() {
        final squad = new Team(club: Club.findByName('Arsenal'), name: 'Squad').save(failOnError: true)
        ['Squad Player A', 'Squad Player B', 'Squad Player C'].each { savePlayer(it, squad) }
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
        'club.name' || ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'club.id'   || ['MU First Team', 'Arsenal First Team', 'Chelsea First Team']
    }

    void 'list sorted by a nested association property keeps a player whose team has no club'() {
        when:
        final results = Player.list(sort: 'team.club.name', order: 'desc')

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void 'list sorted by a map sorts by every entry of the map'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = Team.list(sort: ['club.name': 'asc', name: 'desc'])

        then:
        results*.name - 'Unattached First Team' == ['Arsenal Second Team', 'Arsenal First Team', 'Chelsea First Team', 'MU First Team']
    }

    void 'a paged list sorted by an association property counts a team without a club'() {
        when:
        final results = Team.list(sort: 'club.name', max: 2)

        then:
        results.size() == 2
        results.totalCount == 4
    }

    void 'findAll by example sorted by an association property keeps a team without a club'() {
        when:
        final results = Team.findAll(new Team(), [sort: 'club.name'])

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
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
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        results.totalCount == 4
    }

    void 'a criteria query with pagination arguments sorted by a nested association property keeps a player whose team has no club'() {
        when:
        final results = Player.createCriteria().list(sort: 'team.club.name', max: 10) {
            like('name', '%Player%')
        }

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['Arsenal Player', 'Chelsea Player', 'MU Player']

        and: 'the count joins the nested association as the list does'
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
        results*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']
        results.totalCount == 4
    }

    void 'a criteria query with pagination arguments sorted by a nested association property reuses a join that the criteria make through an alias'() {
        when:
        final results = Player.createCriteria().list(sort: 'team.club.name', order: 'desc', max: 10) {
            createAlias('team', 't')
            createAlias('t.club', 'c')
            like('c.name', '%e%')
        }

        then:
        results*.name == ['MU Player', 'Chelsea Player', 'Arsenal Player']
        results.totalCount == 3
    }

    void 'a dynamic finder sorted by an association property keeps a team without a club'() {
        when:
        final results = Team.findAllByNameLike('%First%', [sort: 'club.name'])

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
    }

    void '#api sorted by two properties of an association joins the association once'() {
        when:
        final List<Team> results = query.call()

        then:
        results*.name - 'Unattached First Team' == ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']

        where:
        api                  | query
        'list'               | { Team.list(sort: ['club.name': 'desc', 'club.id': 'asc']) }
        'findAll by example' | { Team.findAll(new Team(), [sort: ['club.name': 'desc', 'club.id': 'asc']]) }
    }

    void 'findAllWhere and findWhere can be sorted by two properties of an association'() {
        expect:
        Team.findAllWhere([name: 'Arsenal First Team'], [sort: ['club.name': 'asc', 'club.id': 'desc']])*.name == ['Arsenal First Team']
        Team.findWhere([name: 'Arsenal First Team'], [sort: ['club.name': 'asc', 'club.id': 'desc']])?.name == 'Arsenal First Team'
    }

    void 'findAll by example can be sorted by two paths through the same association'() {
        when:
        final results = Player.findAll(new Player(), [sort: ['team.club.name': 'desc', 'team.name': 'asc']])

        then:
        results*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void 'list sorted by two properties of a collection returns a team once for each of its players'() {
        given:
        saveSquad()

        when:
        final results = Team.list(sort: ['players.name': 'asc', 'players.id': 'asc'])

        then:
        results.count { it.name == 'Squad' } == 3
    }

    void 'a criteria query ordered by an association property #direction keeps a team without a club'() {
        when:
        final results = Team.createCriteria().list {
            like('name', '%First%')
            order('club.name', direction)
        }

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == expected

        where:
        direction || expected
        'asc'     || ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'desc'    || ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'a criteria query can be ordered by an association property with #description'() {
        when:
        final results = Team.createCriteria().list {
            like('name', '%First%')
            order(sortOrder)
        }

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == expected

        where:
        description             | sortOrder                     || expected
        'the name of the path'  | 'club.name'                   || ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'an order for the path' | Query.Order.desc('club.name') || ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'a criteria query ordered by a nested association property keeps a player whose team has no club'() {
        when:
        final fromRoot = Player.createCriteria().list {
            order('team.club.name', 'desc')
        }
        final fromAssociation = Player.createCriteria().list {
            team {
                order('club.name', 'desc')
            }
        }

        then:
        'Unattached Player' in fromRoot*.name
        fromRoot*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']

        and: 'an order inside the criteria of an association is relative to that association'
        fromAssociation*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void 'a paged criteria query ordered by an association property counts a team without a club'() {
        when:
        final results = Team.createCriteria().list(max: 10) {
            like('name', '%First%')
            order('club.name')
        }

        then:
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        results.totalCount == 4
    }

    void 'a criteria query ordered by an association property uses the join that the criteria make #description'() {
        when:
        final results = Player.createCriteria().list(criteria)

        then:
        results*.name == ['MU Player', 'Chelsea Player', 'Arsenal Player']

        where:
        description                            | criteria
        'for the association'                  | { team { club { like('name', '%e%') } }; order('team.club.name', 'desc') }
        'for the association, after the order' | { order('team.club.name', 'desc'); team { club { like('name', '%e%') } } }
        'through an alias'                     | { createAlias('team', 't'); createAlias('t.club', 'c'); like('c.name', '%e%'); order('team.club.name', 'desc') }
        'through an alias, after the order'    | { order('team.club.name', 'desc'); createAlias('team', 't'); createAlias('t.club', 'c'); like('c.name', '%e%') }
    }

    void 'a criteria query adds its orders in the sequence in which it requests them: #description'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = Team.createCriteria().list(criteria)

        then:
        results*.name == expected

        where:
        description                              | criteria                                                              || expected
        'an association, then a Hibernate order' | { isNotNull('club'); order('club.name'); order(Order.desc('name')) } || ['Arsenal Second Team', 'Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'a Hibernate order, then an association' | { isNotNull('club'); order(Order.desc('name')); order('club.name') } || ['MU First Team', 'Chelsea First Team', 'Arsenal Second Team', 'Arsenal First Team']
        'a property, then an association'        | { isNotNull('club'); order('name', 'desc'); order('club.name') }     || ['MU First Team', 'Chelsea First Team', 'Arsenal Second Team', 'Arsenal First Team']
    }

    void 'a criteria query ordered by a property of a collection does not fetch the collection'() {
        given:
        saveSquad()

        when:
        final results = Team.createCriteria().list {
            eq('name', 'Squad')
            order('players.name')
            maxResults(2)
        }

        then: 'the row limit does not leave players out of the collection'
        !Hibernate.isInitialized(results.first().players)
        results.first().players.size() == 3
    }

    void 'a criteria query ordered by a property of a collection fetches the collection when #description'() {
        given:
        saveSquad()

        when:
        final List<Team> results = query.call()

        then:
        results*.name.unique() == ['Squad']
        results.every { Hibernate.isInitialized(it.players) }
        results.first().players.size() == 3

        where:
        description                          | query
        'join requests it before the order'  | { Team.createCriteria().list { eq('name', 'Squad'); join('players'); order('players.name') } }
        'join requests it after the order'   | { Team.createCriteria().list { eq('name', 'Squad'); order('players.name'); join('players') } }
        'a fetch mode requests it'           | { Team.createCriteria().list { eq('name', 'Squad'); fetchMode('players', FetchMode.JOIN); order('players.name') } }
        'the fetch argument requests it'     | { Team.createCriteria().list(fetch: [players: 'join']) { eq('name', 'Squad'); order('players.name') } }
        'listDistinct requests it with join' | { Team.createCriteria().listDistinct { eq('name', 'Squad'); join('players'); order('players.name') } }
        'join requests it for a Query.Order' | { Team.createCriteria().list { eq('name', 'Squad'); join('players'); order(Query.Order.asc('players.name')) } }
    }

    void 'list sorts by every property of the default sort of the mapping'() {
        given:
        [['Second', 'B'], ['First', 'B'], ['First', 'A'], ['Second', 'C']].each { String league, String name ->
            new AssociationSortLeague(league: league, name: name).save(failOnError: true)
        }
        manager.session.flush()

        expect:
        AssociationSortLeague.list().collect { it.league + ' ' + it.name } == ['First B', 'First A', 'Second C', 'Second B']
    }

    void '#api sorted by a property of a collection keeps a team without players'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Playerless First Team').save(failOnError: true, flush: true)

        when:
        final List<Team> results = query.call()

        then:
        'Playerless First Team' in results*.name
        results*.name - 'Playerless First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Unattached First Team']

        where:
        api                                | query
        'list'                             | { Team.list(sort: 'players.name') }
        'a dynamic finder'                 | { Team.findAllByNameLike('%First%', [sort: 'players.name']) }
        'findAll by example'               | { Team.findAll(new Team(), [sort: 'players.name']) }
        'a criteria query with pagination' | { Team.createCriteria().list(sort: 'players.name', max: 10) { like('name', '%First%') } }
    }

    void '#api sorted by a property of a collection does not fetch the collection'() {
        given:
        saveSquad()

        when:
        final Team team = query.call()

        then: 'a row limit does not leave players out of the collection'
        team.name == 'Squad'
        !Hibernate.isInitialized(team.players)
        team.players.size() == 3

        where:
        api                                | query
        'a dynamic finder with a limit'    | { Team.findAllByName('Squad', [sort: 'players.name', max: 2]).first() }
        'a dynamic finder for one result'  | { Team.findByName('Squad', [sort: 'players.name']) }
        'findAll by example with a limit'  | { Team.findAll(new Team(name: 'Squad'), [sort: 'players.name', max: 2]).first() }
        'find by example'                  | { Team.find(new Team(name: 'Squad'), [sort: 'players.name']) }
        'findAllWhere with a limit'        | { Team.findAllWhere([name: 'Squad'], [sort: 'players.name', max: 2]).first() }
        'findWhere'                        | { Team.findWhere([name: 'Squad'], [sort: 'players.name']) }
        'a criteria query with pagination' | { Team.createCriteria().list(sort: 'players.name', max: 2) { eq('name', 'Squad') }.first() }
    }
}

@Entity
class AssociationSortLeague {
    String league
    String name

    static mapping = {
        sort league: 'asc', name: 'desc'
    }
}
