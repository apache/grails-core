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

class WhereQueryWithAssociationSortSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Club, Team, Player, Contract])
    }

    void setup() {
        final manUtd = new Club(name: 'Manchester United').save()
        final arsenal = new Club(name: 'Arsenal').save()
        final chelsea = new Club(name: 'Chelsea').save()
        final manUtdTeam = new Team(club: manUtd, name: 'MU First Team').save()
        final arsenalTeam = new Team(club: arsenal, name: 'Arsenal First Team').save()
        final chelseaTeam = new Team(club: chelsea, name: 'Chelsea First Team').save()
        savePlayer('MU Player', manUtdTeam)
        savePlayer('Arsenal Player', arsenalTeam)
        savePlayer('Chelsea Player', chelseaTeam)
    }

    private static void savePlayer(String name, Team team) {
        final player = new Player(name: name, team: team)
        player.contract = new Contract(salary: 1000G, player: player)
        player.save(failOnError: true, flush: true)
    }

    @Issue(['https://github.com/apache/grails-core/issues/9860', 'https://github.com/apache/grails-core/issues/15589'])
    void 'sort by an association property that is also used in the where query'() {
        when:
        final results = Team.where {
            club.name ==~ '%e%'
        }.list(sort: 'club.name', order: direction)

        then:
        results*.name == expected

        where:
        direction || expected
        'asc'     || ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'desc'    || ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    @Issue('https://github.com/apache/grails-core/issues/15589')
    void 'sort by an association property using a sort map when the where query uses the association'() {
        when:
        final results = Team.where {
            club.name != 'Chelsea'
        }.list(sort: ['club.name': 'desc'])

        then:
        results*.name == ['MU First Team', 'Arsenal First Team']
    }

    @Issue('https://github.com/apache/grails-core/issues/15589')
    void 'sort by an association property when a chained where query uses the association'() {
        given:
        var query = Team.where {}
        query = query.where { club.name != 'Chelsea' }

        when:
        final results = query.list(sort: 'club.name', fetch: [club: 'join'])

        then:
        results*.name == ['Arsenal First Team', 'MU First Team']
    }

    @Issue('https://github.com/apache/grails-core/issues/15589')
    void 'sort by an association id when the where query uses the association'() {
        given:
        final expected = Team.list().sort { it.club.id }.reverse()*.name

        when:
        final results = Team.where {
            club.name ==~ '%e%'
        }.list(sort: 'club.id', order: 'desc')

        then:
        results*.name == expected
    }

    @Issue('https://github.com/apache/grails-core/issues/9860')
    void 'sort by an explicit association alias defined in the where query'() {
        when:
        final results = Team.where {
            final c1 = club
            c1.name ==~ '%e%'
        }.list(sort: 'c1.name')

        then:
        results*.name == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
    }

    void 'sort by an association property when the where query does not use the association'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(sort: 'club.name', order: 'desc')

        then:
        results*.name == ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'sort by two properties of the same association when the where query does not use it'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(sort: ['club.name': 'desc', 'club.id': 'asc'])

        then:
        results*.name == ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'sort by a nested association property when the where query uses the nested association'() {
        when:
        final results = Player.where {
            team.club.name ==~ '%e%'
        }.list(sort: 'team.club.name', order: direction)

        then:
        results*.name == expected

        where:
        direction || expected
        'asc'     || ['Arsenal Player', 'Chelsea Player', 'MU Player']
        'desc'    || ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void 'sort by a nested association property when the where query uses only the first association'() {
        when:
        final results = Player.where {
            team.name ==~ '%First%'
        }.list(sort: 'team.club.name', order: 'desc')

        then:
        results*.name == ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void 'sort by a nested association property when the where query does not use the association'() {
        when:
        final results = Player.where {
            name ==~ '%Player%'
        }.list(sort: 'team.club.name')

        then:
        results*.name == ['Arsenal Player', 'Chelsea Player', 'MU Player']
    }

    void 'sort by a nested association property through an explicit alias'() {
        when:
        final results = Player.where {
            final t = team
            t.name ==~ '%First%'
        }.list(sort: 't.club.name')

        then:
        results*.name == ['Arsenal Player', 'Chelsea Player', 'MU Player']
    }
}
