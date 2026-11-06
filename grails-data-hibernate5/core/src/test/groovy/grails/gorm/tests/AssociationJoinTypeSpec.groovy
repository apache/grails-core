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
import org.hibernate.Hibernate

import jakarta.persistence.criteria.JoinType

class AssociationJoinTypeSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    private static final List<String> TEAMS_WITH_A_CLUB = ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Playerless First Team']
    private static final List<String> TEAMS_WITH_PLAYERS = ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Unattached First Team']
    private static final List<String> ALL_TEAMS = (TEAMS_WITH_A_CLUB + 'Unattached First Team').sort()

    void setupSpec() {
        manager.domainClasses.addAll([Club, Team, Player, Contract])
    }

    void setup() {
        final manUtd = new Club(name: 'Manchester United').save()
        final arsenal = new Club(name: 'Arsenal').save()
        final chelsea = new Club(name: 'Chelsea').save()
        savePlayer('MU Player', new Team(club: manUtd, name: 'MU First Team').save())
        savePlayer('Arsenal Player', new Team(club: arsenal, name: 'Arsenal First Team').save())
        savePlayer('Chelsea Player', new Team(club: chelsea, name: 'Chelsea First Team').save())
        savePlayer('Unattached Player', new Team(name: 'Unattached First Team').save(failOnError: true))
        new Team(club: arsenal, name: 'Playerless First Team').save(failOnError: true)
        manager.session.flush()
        manager.session.clear()
    }

    private static void savePlayer(String name, Team team) {
        final player = new Player(name: name, team: team)
        player.contract = new Contract(salary: 1000G, player: player)
        player.save(failOnError: true, flush: true)
    }

    private void saveTeamWithAPlayerWithoutContract() {
        final squad = new Team(club: Club.findByName('Arsenal'), name: 'Squad Team').save(failOnError: true)
        savePlayer('Squad Player A', squad)
        savePlayer('Squad Player B', squad)
        new Player(name: 'Squad Player without contract', team: squad).save(validate: false, flush: true)
        manager.session.clear()
    }

    void 'a where query joins an association with the join type that fetch requests'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(fetch: [club: fetch])

        then:
        results*.name.sort() == expected

        and: 'the association is fetched'
        results.findAll { it.club != null }.every { Hibernate.isInitialized(it.club) }

        where:
        fetch          || expected
        'join'         || ALL_TEAMS
        JoinType.LEFT  || ALL_TEAMS
        JoinType.INNER || TEAMS_WITH_A_CLUB
        JoinType.RIGHT || TEAMS_WITH_A_CLUB
    }

    void 'an inner join that fetch requests returns the same rows with and without a sort'() {
        given:
        final query = Team.where {
            name ==~ '%First%'
        }

        when:
        final unsorted = query.list(fetch: [club: JoinType.INNER])
        final sorted = query.list(fetch: [club: JoinType.INNER], sort: sort)

        then:
        unsorted*.name.sort() == TEAMS_WITH_A_CLUB
        sorted*.name.sort() == TEAMS_WITH_A_CLUB

        where:
        sort << ['name', 'club.name']
    }

    void 'the total count of a paged list counts the rows of an inner join that fetch requests'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(max: 2, fetch: [club: JoinType.INNER])

        then:
        results.size() == 2
        results.totalCount == 4
    }

    void 'a join type requested with the join method applies to list and to count'() {
        given:
        final query = Team.where {
            name ==~ '%First%'
        }.join('club', JoinType.INNER)

        expect:
        query.list()*.name.sort() == TEAMS_WITH_A_CLUB
        query.count() == 4
    }

    void 'updateAll updates the teams that list returns when the query requests #description'() {
        given:
        final uncontracted = new Team(name: 'Uncontracted First Team').save(failOnError: true)
        new Player(name: 'Player without contract', team: uncontracted).save(validate: false, flush: true)
        manager.session.clear()
        final allTeams = (ALL_TEAMS + 'Uncontracted First Team').sort()

        when:
        final listed = query.call().list()*.name.sort()
        final updated = query.call().updateAll(name: 'Updated Team')
        manager.session.clear()

        then:
        listed == expected
        updated == expected.size()

        and: 'the other teams keep their name'
        (Team.list()*.name - 'Updated Team').sort() == allTeams - expected

        where:
        description                                       | query                                                                                                           || expected
        'an inner join of an association'                 | { Team.where { name ==~ '%First%' }.join('club', JoinType.INNER) }                                              || TEAMS_WITH_A_CLUB
        'a right join of an association'                  | { Team.where { name ==~ '%First%' }.join('club', JoinType.RIGHT) }                                              || TEAMS_WITH_A_CLUB
        'an inner join, without criteria'                 | { new DetachedCriteria(Team).join('club', JoinType.INNER) }                                                     || TEAMS_WITH_A_CLUB
        'an inner join of a collection'                   | { Team.where { name ==~ '%First%' }.join('players', JoinType.INNER) }                                           || (TEAMS_WITH_PLAYERS + 'Uncontracted First Team').sort()
        'an inner join of an association of a collection' | { Team.where { name ==~ '%First%' }.join('players.contract', JoinType.INNER) }                                  || TEAMS_WITH_PLAYERS
        'inner joins of an association and a collection'  | { Team.where { name ==~ '%First%' }.join('club', JoinType.INNER).join('players', JoinType.INNER) }              || ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'a left join of an association'                   | { Team.where { name ==~ '%First%' }.join('club', JoinType.LEFT) }                                               || (ALL_TEAMS + 'Uncontracted First Team').sort()
        'a join without a join type'                      | { Team.where { name ==~ '%First%' }.join('club') }                                                              || (ALL_TEAMS + 'Uncontracted First Team').sort()
    }

    void 'updateAll updates the players that list returns when the query requests an inner join of #description'() {
        given:
        new Player(name: 'Player without contract', team: Team.findByName('Playerless First Team')).save(validate: false, flush: true)
        manager.session.clear()
        final allPlayers = ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Player without contract', 'Unattached Player']

        when:
        final listed = query.call().list()*.name.sort()
        final updated = query.call().updateAll(name: 'Updated')
        manager.session.clear()

        then:
        listed == expected
        updated == expected.size()

        and: 'the other players keep their name'
        (Player.list()*.name - 'Updated').sort() == allPlayers - expected

        where:
        description                                               | query                                                                         || expected
        'an association whose key the associated class has'       | { Player.where { name ==~ '%Player%' }.join('contract', JoinType.INNER) }     || ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Unattached Player']
        'an association of an association'                        | { Player.where { name ==~ '%Player%' }.join('team.club', JoinType.INNER) }    || ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Player without contract']
    }

    void 'deleteAll deletes the instances that list returns when the query requests an inner join'() {
        given: 'no contract refers to a player any more'
        Contract.where { salary > 0G }.deleteAll()

        when:
        final deleted = Player.where { name ==~ '%Player' }.join('team.club', JoinType.INNER).deleteAll()
        manager.session.clear()

        then: 'the player whose team has no club is left'
        deleted == 3
        Player.list()*.name == ['Unattached Player']
    }

    void 'a right join has a row without a team for a club that no team refers to'() {
        given:
        new Club(name: 'Teamless').save(flush: true)
        manager.session.clear()

        when:
        final query = new DetachedCriteria(Team).join('club', JoinType.RIGHT)
        final results = query.list()

        then: 'the team without a club is left out, and the club without a team adds a row without a team'
        results.findAll { it != null }*.name.sort() == TEAMS_WITH_A_CLUB
        results.count { it == null } == 1
        query.count() == 5
    }

    void 'a fetch mode that list requests replaces a join type that the query requested before'() {
        given:
        final query = Team.where {
            name ==~ '%First%'
        }.join('club', JoinType.INNER)

        expect:
        query.list(fetch: [club: fetch])*.name.sort() == ALL_TEAMS

        where:
        fetch << ['lazy', 'join']
    }

    void 'a fetch mode that a where query requests with #method replaces a join type that it requested before'() {
        given:
        final query = Team.where {
            name ==~ '%First%'
        }.join('club', JoinType.INNER)

        expect:
        query."$method"('club').list()*.name.sort() == ALL_TEAMS

        where:
        method << ['join', 'select']
    }

    void 'a fetch mode that a criteria query requests with #method replaces a join type that it requested before'() {
        when:
        final results = Team.createCriteria().list {
            like('name', '%First%')
            join('club', JoinType.INNER)
            "$method"('club')
        }

        then:
        results*.name.sort() == ALL_TEAMS

        where:
        method << ['join', 'select']
    }

    void 'the fetch argument of a criteria query replaces a join type that the criteria requested'() {
        when:
        final results = Team.createCriteria().list(max: 10, fetch: [club: fetch]) {
            like('name', '%First%')
            join('club', JoinType.INNER)
        }

        then:
        results*.name.sort() == ALL_TEAMS

        where:
        fetch << ['lazy', 'join', JoinType.LEFT]
    }

    void '#method leaves out a team without a club when fetch requests an inner join'() {
        given:
        final query = Team.where {
            name == 'Unattached First Team'
        }

        expect:
        query."$method"(fetch: [club: JoinType.LEFT]) != null
        query."$method"(fetch: [club: JoinType.INNER]) == null

        where:
        method << ['find', 'get']
    }

    void 'a dynamic finder joins an association with the join type that fetch requests'() {
        when:
        final results = Team.findAllByNameLike('%First%', [fetch: [club: fetch]])

        then:
        results*.name.sort() == expected

        where:
        fetch          || expected
        JoinType.LEFT  || ALL_TEAMS
        JoinType.INNER || TEAMS_WITH_A_CLUB
        JoinType.RIGHT || TEAMS_WITH_A_CLUB
    }

    void 'an inner join that fetch requests for a nested association joins every association on the path'() {
        when:
        final results = Player.where {
            name ==~ '%Player%'
        }.list(fetch: ['team.club': JoinType.INNER])

        then: 'the player whose team has no club is left out'
        results*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player']

        and: 'the associations on the path are fetched'
        results.every { Hibernate.isInitialized(it.team) && Hibernate.isInitialized(it.team.club) }
    }

    void 'a where query joins a collection with the join type that fetch requests'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(fetch: [players: fetch])

        then:
        results*.name.sort() == expected

        and: 'the collection is fetched'
        results.every { Hibernate.isInitialized(it.players) }

        where:
        fetch          || expected
        'join'         || ALL_TEAMS
        JoinType.LEFT  || ALL_TEAMS
        JoinType.INNER || TEAMS_WITH_PLAYERS
        JoinType.RIGHT || TEAMS_WITH_PLAYERS
    }

    void 'a join type requested for a collection with the join method fetches the collection'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.join('players', joinType).list()

        then:
        results*.name.sort() == TEAMS_WITH_PLAYERS
        results.every { Hibernate.isInitialized(it.players) }

        where:
        joinType << [JoinType.INNER, JoinType.RIGHT]
    }

    void 'a join type requested for a collection applies to the total count and to count'() {
        given:
        final query = Team.where {
            name ==~ '%First%'
        }

        when:
        final results = query.list(max: 2, fetch: [players: joinType])

        then:
        results.size() == 2
        results.totalCount == 4
        results.every { Hibernate.isInitialized(it.players) }
        query.join('players', joinType).count() == 4

        where:
        joinType << [JoinType.INNER, JoinType.RIGHT]
    }

    void 'a join type requested for a collection fetches the collection when the query is sorted by it'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(fetch: [players: joinType], sort: 'players.name')

        then:
        results*.name == TEAMS_WITH_PLAYERS
        results.every { Hibernate.isInitialized(it.players) }

        where:
        joinType << [JoinType.INNER, JoinType.RIGHT]
    }

    void 'an inner join requested for an association of a collection does not fetch the collection'() {
        given: 'a team with a player that has no contract'
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = Team.where {
            name ==~ '%Team%'
        }.list(fetch: ['players.contract': JoinType.INNER])
        final squad = results.find { it.name == 'Squad Team' }

        then: 'the team without players is left out'
        results*.name.sort() == (TEAMS_WITH_PLAYERS + 'Squad Team').sort()

        and: 'the inner join does not leave the player without a contract out of the collection'
        !Hibernate.isInitialized(squad.players)
        squad.players.size() == 3
    }

    void 'a join type requested for an association of a collection returns and counts each instance once'() {
        given: 'a team with two players that have a contract'
        saveTeamWithAPlayerWithoutContract()
        final query = Team.where {
            name ==~ '%Team%'
        }

        when:
        final results = query.list(max: 10, fetch: ['players.contract': joinType])

        then:
        results*.name.sort() == (TEAMS_WITH_PLAYERS + 'Squad Team').sort()
        results.totalCount == 5
        query.join('players.contract', joinType).count() == 5

        where:
        joinType << [JoinType.INNER, JoinType.RIGHT]
    }

    void 'an inner join requested for an association of a collection of an association counts each instance once'() {
        given:
        saveTeamWithAPlayerWithoutContract()
        final query = Player.where {
            name ==~ '%Player%'
        }.join('team.players.contract', JoinType.INNER)

        expect: 'every player is in a team that has a player with a contract'
        query.list().size() == 7
        query.count() == 7
    }

    void 'an inner join requested for a collection and for an association of that collection applies to both'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = Team.where {
            name ==~ '%Team%'
        }.list(fetch: [players: JoinType.INNER, 'players.contract': JoinType.INNER])
        final squad = results.find { it.name == 'Squad Team' }

        then:
        results*.name.sort() == (TEAMS_WITH_PLAYERS + 'Squad Team').sort()

        and: 'the collection is fetched, with the player that has no contract'
        Hibernate.isInitialized(squad.players)
        squad.players.size() == 3
    }

    void 'a fetch requested for a collection is kept when an inner join is requested for an association of the collection'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = Team.where {
            name == 'Squad Team'
        }.list(fetch: [players: 'join', 'players.contract': JoinType.INNER])

        then: 'the collection is fetched, with the player that has no contract'
        results*.name == ['Squad Team']
        Hibernate.isInitialized(results.first().players)
        results.first().players.size() == 3
    }

    void 'a left join that #description requests for a collection that the query has criteria on does not fetch the collection'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        when:
        final List<Team> results = query.call()

        then:
        results*.name == ['Squad Team']

        and: 'the criteria do not leave players out of the collection'
        !Hibernate.isInitialized(results.first().players)
        results.first().players.size() == 3

        where:
        description          | query
        'the fetch argument' | { Team.where { players { name == 'Squad Player A' } }.list(fetch: [players: JoinType.LEFT]) }
        'join'               | { Team.where { players { name == 'Squad Player A' } }.join('players', JoinType.LEFT).list() }
        'join, with a sort'  | { Team.where { players { name == 'Squad Player A' } }.join('players', JoinType.LEFT).sort('players.name').list() }
    }

    void 'a join type that fetch requests is used for the join that the criteria of the query make'() {
        given:
        final query = Team.where {
            club.name == 'Chelsea' || name == 'Unattached First Team'
        }

        when:
        final results = query.list(fetch: [club: fetch], sort: 'club.name', max: 10)

        then:
        results*.name.sort() == expected
        results.totalCount == expected.size()

        where:
        fetch          || expected
        JoinType.INNER || ['Chelsea First Team']
        JoinType.LEFT  || ['Chelsea First Team', 'Unattached First Team']
    }

    void 'an inner join that fetch requests is used for the join that a projection of the query makes'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.property('club.name').list(fetch: [club: fetch])

        then:
        results.contains(null) == containsNull
        results.findAll { it != null }.sort() == ['Arsenal', 'Chelsea', 'Manchester United']

        where:
        fetch          || containsNull
        JoinType.LEFT  || true
        JoinType.INNER || false
    }

    void 'a join type requested for a nested association is used for the join that the criteria of the query make'() {
        given:
        final query = Player.where {
            team.club.name == 'Arsenal' || name == 'Unattached Player'
        }

        expect:
        query.list(fetch: ['team.club': joinType])*.name.sort() == expected
        query.join('team.club', joinType).list()*.name.sort() == expected

        where:
        joinType       || expected
        JoinType.INNER || ['Arsenal Player']
        JoinType.LEFT  || ['Arsenal Player', 'Unattached Player']
    }

    void 'a join type requested for an association is not used for a nested association of the same name'() {
        when:
        final results = Player.where {
            team.club.name == 'Arsenal' || name == 'Unattached Player'
        }.join('club', JoinType.LEFT).list()

        then:
        results*.name == ['Arsenal Player']
    }

    void 'a join type requested for a collection at the end of a path fetches the collection'() {
        when:
        final results = Player.where {
            name ==~ '%Player%'
        }.list(fetch: ['team.players': joinType])

        then:
        results*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Unattached Player']
        results.every { Hibernate.isInitialized(it.team) && Hibernate.isInitialized(it.team.players) }

        where:
        joinType << [JoinType.INNER, JoinType.RIGHT]
    }

    void 'an inner join requested for an association and for a collection applies to both'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(fetch: [club: JoinType.INNER, players: JoinType.INNER])

        then:
        results*.name.sort() == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        results.every { Hibernate.isInitialized(it.club) && Hibernate.isInitialized(it.players) }
    }

    void 'an inner join requested by a subquery applies to the subquery'() {
        given:
        final teamsWithAClub = Team.where {
            name ==~ '%First%'
        }.join('club', JoinType.INNER).id()

        when:
        final results = new DetachedCriteria(Team).inList('id', teamsWithAClub).list()

        then:
        results*.name.sort() == TEAMS_WITH_A_CLUB
    }

    void 'a subquery can have criteria on an association that it requests a join type for'() {
        given:
        final arsenalPlayers = Player.where {
            team {
                club {
                    name == 'Arsenal'
                }
            }
        }.join('team.club', joinType).id()

        when:
        final results = new DetachedCriteria(Player).inList('id', arsenalPlayers).list()

        then:
        results*.name == ['Arsenal Player']

        where:
        joinType << [JoinType.LEFT, JoinType.INNER, JoinType.RIGHT]
    }

    void 'a subquery applies a requested inner join to the associations that its criteria do not join'() {
        given:
        final playersOfATeamWithAClub = Player.where {
            team {
                name ==~ '%First%'
            }
        }.join('team.club', JoinType.INNER).id()

        when:
        final results = new DetachedCriteria(Player).inList('id', playersOfATeamWithAClub).list()

        then: 'the player whose team has no club is left out'
        results*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player']
    }

    void 'a criteria query can embed a subquery that has criteria on an association that it requests a join type for'() {
        given:
        final arsenalPlayers = Player.where {
            team {
                club {
                    name == 'Arsenal'
                }
            }
        }.join('team.club', JoinType.INNER).id()

        when:
        final results = Player.createCriteria().list {
            inList('id', arsenalPlayers)
        }

        then:
        results*.name == ['Arsenal Player']
    }

    void 'an inner join that fetch requests applies to a projection'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.property('name').list(fetch: [club: JoinType.INNER])

        then:
        results.sort() == TEAMS_WITH_A_CLUB
    }

    void 'a criteria query joins an association with the join type that join requests'() {
        when:
        final results = Team.createCriteria().list {
            like('name', '%First%')
            join('club', joinType)
        }

        then:
        results*.name.sort() == expected

        and: 'the association is fetched'
        results.findAll { it.club != null }.every { Hibernate.isInitialized(it.club) }

        where:
        joinType       || expected
        JoinType.LEFT  || ALL_TEAMS
        JoinType.INNER || TEAMS_WITH_A_CLUB
        JoinType.RIGHT || TEAMS_WITH_A_CLUB
    }

    void 'count and get apply an inner join that a criteria query requests'() {
        when:
        final count = Team.createCriteria().count {
            like('name', '%First%')
            join('club', JoinType.INNER)
        }
        final team = Team.createCriteria().get {
            eq('name', 'Unattached First Team')
            join('club', JoinType.INNER)
        }

        then:
        count == 4
        team == null
    }

    void 'a criteria query joins an association with the join type that the fetch argument requests'() {
        when:
        final results = Team.createCriteria().list(fetch: [club: joinType]) {
            like('name', '%First%')
        }

        then:
        results*.name.sort() == expected

        and: 'the association is fetched'
        results.findAll { it.club != null }.every { Hibernate.isInitialized(it.club) }

        where:
        joinType       || expected
        JoinType.LEFT  || ALL_TEAMS
        JoinType.INNER || TEAMS_WITH_A_CLUB
        JoinType.RIGHT || TEAMS_WITH_A_CLUB
    }

    void 'the total count of a paged criteria query counts the rows of an inner join that fetch requests'() {
        when:
        final results = Team.createCriteria().list(max: 2, fetch: [club: JoinType.INNER]) {
            like('name', '%First%')
        }

        then:
        results.size() == 2
        results.totalCount == 4
    }

    void 'a criteria query with an inner join that fetch requests returns the same rows with a sort'() {
        when:
        final results = Team.createCriteria().list(sort: 'club.name', fetch: [club: JoinType.INNER]) {
            like('name', '%First%')
        }

        then:
        results*.name.sort() == TEAMS_WITH_A_CLUB
    }

    void 'a criteria query ordered by an association applies the join type that #description for the association'() {
        when:
        final List<Team> results = query.call()

        then: 'the order does not bring back the team without a club'
        results*.name.sort() == TEAMS_WITH_A_CLUB
        results*.club*.name == ['Arsenal', 'Arsenal', 'Chelsea', 'Manchester United']

        where:
        description                           | query
        'join requests before the order'      | { Team.createCriteria().list { like('name', '%First%'); join('club', JoinType.INNER); order('club.name') } }
        'join requests after the order'       | { Team.createCriteria().list { like('name', '%First%'); order('club.name'); join('club', JoinType.INNER) } }
        'join requests with a right join'     | { Team.createCriteria().list { like('name', '%First%'); join('club', JoinType.RIGHT); order('club.name') } }
        'the fetch argument requests'         | { Team.createCriteria().list(fetch: [club: JoinType.INNER]) { like('name', '%First%'); order('club.name') } }
        'join requests in a paged list'       | { Team.createCriteria().list(max: 10) { like('name', '%First%'); join('club', JoinType.INNER); order('club.name') } }
    }

    void 'the total count and get of a criteria query ordered by an association apply an inner join that it requests'() {
        when:
        final results = Team.createCriteria().list(max: 2) {
            like('name', '%First%')
            join('club', JoinType.INNER)
            order('club.name')
        }
        final unattached = Team.createCriteria().get {
            eq('name', 'Unattached First Team')
            join('club', JoinType.INNER)
            order('club.name')
        }

        then:
        results.size() == 2
        results.totalCount == 4
        unattached == null
    }

    void 'a criteria query ordered by a nested association applies an inner join that it requests #description'() {
        when:
        final results = Player.createCriteria().list(criteria)

        then:
        results*.name == ['Arsenal Player', 'Chelsea Player', 'MU Player']

        where:
        description                              | criteria
        'for the path'                           | { join('team.club', JoinType.INNER); order('team.club.name') }
        'for the path, after the order'          | { order('team.club.name'); join('team.club', JoinType.INNER) }
        'inside the criteria of the association' | { team { join('club', JoinType.INNER); order('club.name') } }
    }

    void 'a criteria query ordered by a collection fetches the collection with the join type that #description'() {
        when:
        final List<Team> results = query.call()

        then:
        results*.name == expected

        and: 'the collection is fetched'
        results.every { Hibernate.isInitialized(it.players) }

        where:
        description                               | query                                                                                                                || expected
        'join requests before the order'          | { Team.createCriteria().list { like('name', '%First%'); join('players', JoinType.INNER); order('players.name') } }  || TEAMS_WITH_PLAYERS
        'join requests after the order'           | { Team.createCriteria().list { like('name', '%First%'); order('players.name'); join('players', JoinType.INNER) } }  || TEAMS_WITH_PLAYERS
        'join requests with a right join'         | { Team.createCriteria().list { like('name', '%First%'); join('players', JoinType.RIGHT); order('players.name') } }  || TEAMS_WITH_PLAYERS
        'join requests with a left join'          | { Team.createCriteria().list { like('name', '%First%'); join('players', JoinType.LEFT); order('players.name') } }   || ['Playerless First Team'] + TEAMS_WITH_PLAYERS
        'the fetch argument requests'             | { Team.createCriteria().list(fetch: [players: JoinType.INNER]) { like('name', '%First%'); order('players.name') } } || TEAMS_WITH_PLAYERS
    }

    void 'a criteria query joins a collection with the join type that join requests'() {
        when:
        final results = Team.createCriteria().list {
            like('name', '%First%')
            join('players', joinType)
        }
        final count = Team.createCriteria().count {
            like('name', '%First%')
            join('players', joinType)
        }

        then:
        results*.name.sort() == expected
        count == expected.size()

        and: 'the collection is fetched'
        results.every { Hibernate.isInitialized(it.players) }

        where:
        joinType       || expected
        JoinType.LEFT  || ALL_TEAMS
        JoinType.INNER || TEAMS_WITH_PLAYERS
        JoinType.RIGHT || TEAMS_WITH_PLAYERS
    }

    void 'a criteria query joins a collection with the join type that the fetch argument requests'() {
        when:
        final results = Team.createCriteria().list(max: 2, fetch: [players: joinType]) {
            like('name', '%First%')
        }

        then:
        results.size() == 2
        results.totalCount == expectedTotal

        and: 'the collection is fetched'
        results.every { Hibernate.isInitialized(it.players) }

        where:
        joinType       || expectedTotal
        JoinType.LEFT  || 5
        JoinType.INNER || 4
        JoinType.RIGHT || 4
    }

    void 'an inner join that a criteria query requests for a nested association joins every association on the path'() {
        when:
        final results = Player.createCriteria().list {
            join('team.club', JoinType.INNER)
        }

        then: 'the player whose team has no club is left out'
        results*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player']

        and: 'the associations on the path are fetched'
        results.every { Hibernate.isInitialized(it.team) && Hibernate.isInitialized(it.team.club) }
    }

    void 'a fetch that a criteria query requests for a collection is kept when it requests an inner join for an association of the collection'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = Team.createCriteria().list {
            like('name', '%Team%')
            join('players')
            join('players.contract', JoinType.INNER)
        }
        final squad = results.find { it.name == 'Squad Team' }

        then: 'the collection is fetched, with the player that has no contract'
        Hibernate.isInitialized(squad.players)
        squad.players.size() == 3
    }

    void 'an inner join that a criteria query requests for an association of a collection does not fetch the collection'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = Team.createCriteria().list {
            like('name', '%Team%')
            join('players.contract', JoinType.INNER)
        }
        final count = Team.createCriteria().count {
            like('name', '%Team%')
            join('players.contract', JoinType.INNER)
        }
        final paged = Team.createCriteria().list(max: 10, fetch: ['players.contract': JoinType.INNER]) {
            like('name', '%Team%')
        }
        final squad = results.find { it.name == 'Squad Team' }

        then: 'each team is returned and counted once'
        results*.name.sort() == (TEAMS_WITH_PLAYERS + 'Squad Team').sort()
        count == 5
        paged.totalCount == 5

        and:
        !Hibernate.isInitialized(squad.players)
        squad.players.size() == 3
    }

    void 'an association that a criteria query joins through an alias keeps its join when join requests a join type'() {
        when:
        final results = Player.createCriteria().list {
            createAlias('team', 't')
            createAlias('t.club', 'c')
            like('c.name', '%e%')
            join('team.club', JoinType.INNER)
        }

        then:
        results*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player']
    }

    void 'an association that a criteria query has criteria on keeps its join when join requests a join type'() {
        when:
        final results = Team.createCriteria().list {
            join('club', JoinType.INNER)
            club {
                like('name', '%e%')
            }
        }

        then:
        results*.name.sort() == TEAMS_WITH_A_CLUB
    }

    void 'a join type requested for a property that is not an association is ignored'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(fetch: [name: JoinType.INNER])

        then:
        results*.name.sort() == ALL_TEAMS
    }
}
