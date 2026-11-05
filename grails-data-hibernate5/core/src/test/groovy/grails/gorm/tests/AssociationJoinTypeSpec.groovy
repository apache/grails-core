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
