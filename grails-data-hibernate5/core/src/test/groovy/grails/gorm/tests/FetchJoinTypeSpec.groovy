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
import org.hibernate.Hibernate

import jakarta.persistence.criteria.JoinType

/**
 * The join type that a query requests for an association decides which instances the query returns, and the
 * fetch loads the association with them. A fetched collection always holds all of its elements: a join type
 * never filters the elements of a collection, also when it is requested for a path through the collection.
 *
 * <pre>
 * | fetch                               | teams returned                                   | loaded                      |
 * |-------------------------------------|--------------------------------------------------|-----------------------------|
 * | [players: INNER] or [players: RIGHT]| the teams with at least one player               | players, all of them        |
 * | [players: LEFT]                     | all teams                                        | players, all of them        |
 * | ['players.contract': INNER]         | the teams with at least one player with contract | players, all of them        |
 * | ['players.contract': LEFT]          | all teams                                        | players, all of them        |
 * | [club: INNER] or [club: RIGHT]      | the teams with a club                            | club                        |
 * | [club: LEFT]                        | all teams                                        | club                        |
 * </pre>
 *
 * Every club has a team, so a right join returns the same teams as an inner join. A where query, a dynamic finder
 * and {@code list} return each team once. A criteria query and a query by example return a team once for each
 * element of a collection that they fetch, as they do for a fetch of a collection with {@code join}.
 */
class FetchJoinTypeSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    private static final List<String> ALL_TEAMS = ['Clubless Team', 'Contracted Team', 'Playerless Team', 'Uncontracted Team']
    private static final List<String> TEAMS_WITH_PLAYERS = ['Clubless Team', 'Contracted Team', 'Uncontracted Team']
    private static final List<String> TEAMS_WITH_A_CONTRACTED_PLAYER = ['Clubless Team', 'Contracted Team']
    private static final List<String> TEAMS_WITH_A_CLUB = ['Contracted Team', 'Playerless Team', 'Uncontracted Team']

    private static final Map<String, List<String>> PLAYERS = [
            'Clubless Team'    : ['Clubless Player'],
            'Contracted Team'  : ['Contracted Player', 'Uncontracted Player A'],
            'Playerless Team'  : [],
            'Uncontracted Team': ['Uncontracted Player B'],
    ]

    private static final Map<String, Closure<List<Team>>> QUERIES = [
            'a where query with the fetch argument'  : { Map fetch -> Team.where { name ==~ '%Team' }.list(fetch: fetch) },
            'a where query with join'                : { Map fetch ->
                final query = Team.where { name ==~ '%Team' }
                fetch.each { String path, JoinType joinType -> query.join(path, joinType) }
                query.list()
            },
            'a dynamic finder'                       : { Map fetch -> Team.findAllByNameLike('%Team', [fetch: fetch]) },
            'a criteria query with join'             : { Map fetch ->
                Team.createCriteria().list {
                    like('name', '%Team')
                    fetch.each { String path, JoinType joinType -> join(path, joinType) }
                }
            },
            'a criteria query with the fetch argument': { Map fetch -> Team.createCriteria().list(fetch: fetch, max: 100) { like('name', '%Team') } },
            'findAll by example'                     : { Map fetch -> Team.findAll(new Team(), [fetch: fetch]) },
            'list'                                   : { Map fetch -> Team.list(fetch: fetch) },
            'a paged list'                           : { Map fetch -> Team.list(fetch: fetch, max: 100) },
    ]

    private static final List<String> RETURN_EACH_TEAM_ONCE = ['a where query with the fetch argument', 'a where query with join', 'a dynamic finder', 'list', 'a paged list']

    private static final List<Map> CHART = [
            [fetch: [players: JoinType.INNER], teams: TEAMS_WITH_PLAYERS, loaded: 'players'],
            [fetch: [players: JoinType.RIGHT], teams: TEAMS_WITH_PLAYERS, loaded: 'players'],
            [fetch: [players: JoinType.LEFT], teams: ALL_TEAMS, loaded: 'players'],
            [fetch: ['players.contract': JoinType.INNER], teams: TEAMS_WITH_A_CONTRACTED_PLAYER, loaded: 'players'],
            [fetch: ['players.contract': JoinType.LEFT], teams: ALL_TEAMS, loaded: 'players'],
            [fetch: [club: JoinType.INNER], teams: TEAMS_WITH_A_CLUB, loaded: 'club'],
            [fetch: [club: JoinType.RIGHT], teams: TEAMS_WITH_A_CLUB, loaded: 'club'],
            [fetch: [club: JoinType.LEFT], teams: ALL_TEAMS, loaded: 'club'],
    ]

    void setupSpec() {
        manager.domainClasses.addAll([Club, Team, Player, Contract])
    }

    void setup() {
        final contractedTeam = new Team(name: 'Contracted Team', club: new Club(name: 'Arsenal').save(failOnError: true)).save(failOnError: true)
        savePlayer('Contracted Player', contractedTeam, true)
        savePlayer('Uncontracted Player A', contractedTeam, false)
        savePlayer('Uncontracted Player B', new Team(name: 'Uncontracted Team', club: new Club(name: 'Chelsea').save(failOnError: true)).save(failOnError: true), false)
        new Team(name: 'Playerless Team', club: new Club(name: 'Manchester United').save(failOnError: true)).save(failOnError: true)
        savePlayer('Clubless Player', new Team(name: 'Clubless Team').save(failOnError: true), true)
        manager.session.flush()
        manager.session.clear()
    }

    private static void savePlayer(String name, Team team, boolean contracted) {
        final player = new Player(name: name, team: team)
        if (contracted) {
            player.contract = new Contract(salary: 1000G, player: player)
        }
        player.save(validate: contracted, failOnError: contracted, flush: true)
    }

    void '#api with fetch #fetch returns #teams and fetches #loaded'() {
        when:
        final List<Team> results = QUERIES[api].call(fetch)
        final Map<String, Boolean> initialized = results.collectEntries { Team team ->
            [(team.name): loaded == 'players' ? Hibernate.isInitialized(team.players) : (team.club == null || Hibernate.isInitialized(team.club))]
        }

        then: 'the join type decides which teams the query returns'
        results*.name.unique().sort() == teams

        and: 'each team once, or once for each element of a fetched collection'
        results.size() == (api in RETURN_EACH_TEAM_ONCE || loaded != 'players' ? teams.size() : teams.sum { Math.max(1, PLAYERS[it].size()) })

        and: 'the association is fetched'
        initialized.values().every()

        and: 'a fetched collection holds all of its elements'
        loaded != 'players' || results.every { it.players*.name.sort() == PLAYERS[it.name] }

        and: 'a paged list counts the teams that it returns'
        !(api in ['a criteria query with the fetch argument', 'a paged list']) || results.totalCount == teams.size()

        where:
        [api, row] << [QUERIES.keySet().toList(), CHART].combinations()
        fetch = row.fetch
        teams = row.teams
        loaded = row.loaded
    }

    void '#api with the fetch mode join on a collection that can be empty returns every team with its players'() {
        when:
        final List<Team> results = QUERIES[api].call([players: 'join'])

        then:
        results*.name.unique().sort() == ALL_TEAMS
        results.every { Team team -> Hibernate.isInitialized(team.players) && team.players*.name.sort() == PLAYERS[team.name] }

        where:
        api << ['a where query with the fetch argument', 'a dynamic finder', 'list', 'a paged list']
    }

    void '#api with the fetch mode join on an association that can be null returns every team'() {
        when:
        final List<Team> results = QUERIES[api].call(fetch)

        then: 'a fetch mode, unlike a join type, never leaves out an instance whose association is null'
        results*.name.unique().sort() == ALL_TEAMS

        and: 'the association is fetched'
        results.every { Team team -> team.club == null || Hibernate.isInitialized(team.club) }

        where:
        [api, fetch] << [['a where query with the fetch argument', 'a dynamic finder', 'a criteria query with the fetch argument',
                          'findAll by example', 'list', 'a paged list'], [[club: 'join']]].combinations()
    }
}
