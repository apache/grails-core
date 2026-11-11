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
import grails.gorm.tests.entities.Player
import grails.gorm.tests.entities.Team
import org.hibernate.Hibernate
import spock.lang.Issue

/**
 * The fetch mode join of the {@code fetch} argument fetches an association and never leaves out an instance whose
 * association is null or empty, whichever way the query is made. Only a join type asks for that.
 */
@Issue('https://github.com/apache/grails-core/pull/16853')
class ListFetchJoinNullSpec extends HibernateGormDatastoreSpec {

    private static final List<String> ALL_TEAMS = ['Clubless Team', 'Playerless Team', 'Team With Players']

    void setupSpec() {
        manager.registerDomainClasses(Club, Team, Player)
    }

    void setup() {
        final Team withPlayers = new Team(name: 'Team With Players', club: new Club(name: 'Arsenal').save(failOnError: true)).save(failOnError: true)
        new Player(name: 'First', team: withPlayers).save(failOnError: true)
        new Player(name: 'Second', team: withPlayers).save(failOnError: true)
        new Team(name: 'Playerless Team', club: new Club(name: 'Chelsea').save(failOnError: true)).save(failOnError: true)
        new Team(name: 'Clubless Team').save(failOnError: true)
        manager.session.flush()
        manager.session.clear()
    }

    private static final Map<String, Closure<List<Team>>> QUERIES = [
            'list'                  : { Map fetch -> Team.list(fetch: fetch) },
            'a paged list'          : { Map fetch -> Team.list(fetch: fetch, max: 100) },
            'a where query'         : { Map fetch -> Team.where { name ==~ '%Team%' }.list(fetch: fetch) },
            'a dynamic finder'      : { Map fetch -> Team.findAllByNameLike('%Team%', [fetch: fetch]) },
    ]

    void '#api with the fetch mode join on an association that can be null returns every team'() {
        when:
        final List<Team> results = QUERIES[api].call([club: 'join'])

        then:
        results*.name.unique().sort() == ALL_TEAMS

        and: 'the club is fetched'
        results.every { Team team -> team.club == null || Hibernate.isInitialized(team.club) }

        where:
        api << QUERIES.keySet().toList()
    }

    void '#api with the fetch mode join on a collection that can be empty returns every team with its players'() {
        when:
        final List<Team> results = QUERIES[api].call([players: 'join'])

        then:
        results*.name.unique().sort() == ALL_TEAMS
        results.every { Team team -> Hibernate.isInitialized(team.players) }
        results.find { it.name == 'Team With Players' }.players*.name.sort() == ['First', 'Second']

        where:
        api << QUERIES.keySet().toList()
    }
}
