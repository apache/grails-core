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

class UndefinedAliasSortSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    protected boolean isLeftJoinSort() {
        true
    }

    void setupSpec() {
        // a sort through an association keeps the instances without it with this setting
        manager.configuration['grails.gorm.leftJoinAssociationSort'] = leftJoinSort
        manager.domainClasses.addAll([Club, Team, Player, Contract])
    }

    void setup() {
        ['Manchester United', 'Arsenal', 'Chelsea'].each { clubName ->
            final club = new Club(name: clubName).save()
            new Team(club: club, name: "${clubName} First Team").save()
        }
        manager.session.flush()
        manager.session.clear()
    }

    void '#api sorts by a path that starts with the alias of the queried class'() {
        given: 'a team without a club'
        new Team(name: 'Unattached First Team').save(failOnError: true, flush: true)
        manager.session.clear()

        expect:
        query.call()*.name == expected

        where:
        api                                                     | query                                                                                       || expected
        'a detached criteria'                                   | { new DetachedCriteria(Team, 't').list(sort: 't.name') }                                    || TEAMS_BY_NAME
        'a detached criteria in descending order'               | { new DetachedCriteria(Team, 't').list(sort: 't.name', order: 'desc') }                     || TEAMS_BY_NAME.reverse()
        'the sort of a detached criteria'                       | { new DetachedCriteria(Team, 't').sort('t.name').list() }                                   || TEAMS_BY_NAME
        'a detached criteria with a criterion'                  | { new DetachedCriteria(Team, 't').build { like('name', '%First Team') }.list(sort: 't.name') } || TEAMS_BY_NAME
        'a where query, through the alias Hibernate gives it'   | { Team.where { name ==~ '%' }.list(sort: 'this.name') }                                     || TEAMS_BY_NAME
        'a dynamic finder, through the alias Hibernate gives it' | { Team.findAllByNameLike('%', [sort: 'this.name']) }                                       || TEAMS_BY_NAME
        'findAll by example, through the alias Hibernate gives it' | { Team.findAll(new Team(), [sort: 'this.name']) }                                        || TEAMS_BY_NAME
        'a criteria query, through the alias Hibernate gives it' | { Team.createCriteria().list(sort: 'this.name') { } }                                       || TEAMS_BY_NAME
    }

    void 'a paged detached criteria sorted through the alias of the queried class counts every team'() {
        when:
        final results = new DetachedCriteria(Team, 't').list(max: 2, sort: 't.name', order: 'desc')

        then:
        results*.name == ['Manchester United First Team', 'Chelsea First Team']
        results.totalCount == 3
    }

    void 'a root alias named #alias is used when the path does not resolve through a property'() {
        given:
        final criteria = new DetachedCriteria(Team, alias)

        expect:
        criteria.list(sort: property, order: 'desc')*.name == expected
        criteria.sort(property, 'desc').list()*.name == expected

        where:
        alias  | property         | expected
        'club' | 'club.club.name' | ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
        'name' | 'name.name'      | ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'an association path keeps its meaning when the root alias has the same name'() {
        given:
        new Team(name: 'Alias Z', club: Club.findByName('Arsenal')).save(failOnError: true)
        new Team(name: 'Alias A', club: Club.findByName('Chelsea')).save(failOnError: true)
        new Team(name: 'Alias M').save(failOnError: true, flush: true)
        manager.session.clear()
        final criteria = new DetachedCriteria(Team, 'club').like('name', 'Alias%')

        expect:
        criteria.list(sort: 'club.name')*.name - 'Alias M' == ['Alias Z', 'Alias A']
        criteria.sort('club.name').list()*.name - 'Alias M' == ['Alias Z', 'Alias A']
        criteria.list(sort: 'club.name', max: 10).totalCount == (leftJoinSort ? 3 : 2)
    }

    void 'a root-alias sort through an association follows the compatibility setting'() {
        given:
        new Team(name: 'Unattached First Team').save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final results = new DetachedCriteria(Team, 't').list(sort: 't.club.name', order: 'desc')

        then:
        ('Unattached First Team' in results*.name) == leftJoinSort
        results*.name - 'Unattached First Team' == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'a root alias matching #alias preserves the full #property path with #sortForm and pagination #paged'() {
        given:
        final first = new Team(name: 'Collision Z', club: Club.findByName('Arsenal')).save(failOnError: true)
        final second = new Team(name: 'Collision A', club: Club.findByName('Chelsea')).save(failOnError: true)
        [first, second].eachWithIndex { Team team, int index ->
            final player = new Player(name: "Collision player ${index}", team: team)
            player.contract = new Contract(salary: 100G, player: player)
            player.save(failOnError: true)
        }
        manager.session.flush()
        manager.session.clear()
        def criteria = new DetachedCriteria(domain, alias).like('name', 'Collision%')
        final arguments = paged ? [max: 1] : [:]
        if (sortForm == 'stored') {
            criteria = criteria.sort(property)
        }
        else {
            arguments.sort = sortForm == 'map' ? [(property): 'asc'] : property
        }

        when:
        final results = criteria.list(arguments)

        then:
        results*.name == (paged ? expected.take(1) : expected)
        !paged || results.totalCount == 2

        where:
        [domain, alias, property, expected, sortForm, paged] << [
                [
                        [Team, 'players', 'players.team.name', ['Collision A', 'Collision Z']],
                        [Player, 'team', 'team.club.name', ['Collision player 0', 'Collision player 1']],
                ],
                ['argument', 'stored', 'map'],
                [false, true],
        ].combinations().collect { List row -> row[0] + row[1..2] }
    }

    void 'a nested LEFT sort keeps an empty collection with root alias #rootAlias and fetch #fetchPath using #sortForm and pagination #paged'() {
        given:
        new Team(name: 'Nested empty').save(failOnError: true)
        final populated = new Team(name: 'Nested populated').save(failOnError: true)
        ['Nested player A', 'Nested player B'].each { String playerName ->
            final player = new Player(name: playerName, team: populated)
            player.contract = new Contract(salary: 100G, player: player)
            player.save(failOnError: true)
        }
        manager.session.flush()
        manager.session.clear()
        def criteria = (rootAlias == null ? new DetachedCriteria(Team) : new DetachedCriteria(Team, rootAlias)).like('name', 'Nested%')
        final arguments = [fetch: [(fetchPath): JoinType.LEFT]] + (paged ? [max: 10] : [:])
        if (sortForm == 'stored') {
            criteria = criteria.sort(sortProperty)
        }
        else {
            arguments.sort = sortForm == 'map' ? [(sortProperty): 'asc'] : sortProperty
        }

        when:
        final results = criteria.list(arguments)

        then:
        results*.name.sort() == ['Nested empty', 'Nested populated']
        results.every { Hibernate.isInitialized(it.players) == !paged }
        results.find { it.name == 'Nested empty' }.players.empty
        results.find { it.name == 'Nested populated' }.players*.name.sort() == ['Nested player A', 'Nested player B']

        where:
        [rootAlias, fetchPath, sortProperty, sortForm, paged] << [
                [
                        [null, 'players.team', 'players.team.name'],
                        ['players', 'players.team', 'players.team.name'],
                        ['t', 't.players.team', 't.players.team.name'],
                        ['t', 't.players.team', 'players.team.name'],
                        ['t', 'players.team', 't.players.team.name'],
                        ['players', 'players.players.team', 'players.players.team.name'],
                ],
                ['argument', 'stored', 'map'],
                [false, true],
        ].combinations().collect { List row -> row[0] + row[1..2] }
    }

    void 'a root-qualified #replacement replaces a canonical INNER fetch request'() {
        given:
        new Team(name: 'Fetch alias empty').save(failOnError: true, flush: true)
        manager.session.clear()
        def criteria = new DetachedCriteria(Team, 't').eq('name', 'Fetch alias empty').join('club', JoinType.INNER)
        criteria = replacement == 'join' ? criteria.join('t.club') : criteria.select('t.club')

        expect:
        criteria.list(sort: 't.name')*.name == ['Fetch alias empty']

        where:
        replacement << ['join', 'select']
    }

    private static final List<String> TEAMS_BY_NAME = ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team', 'Unattached First Team']

    void 'a sort by an alias that the query defines still sorts'() {
        when:
        final results = Team.where { def c1 = club; c1.name ==~ '%e%' }.list(sort: 'c1.name')

        then:
        results*.name == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }
}
