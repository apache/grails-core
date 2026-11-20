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

import spock.lang.Issue

class WhereQueryWithAssociationSortSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    void setupSpec() {
        manager.registerDomainClasses(Club, Team, Player, Contract)
    }

    void setup() {
        ['Manchester United': 'Bruno', 'Arsenal': 'Bukayo', 'Chelsea': 'Cole'].each { clubName, playerName ->
            final club = new Club(name: clubName).save()
            final team = new Team(club: club, name: "${clubName} First Team").save()
            new Player(name: playerName, team: team, contract: new Contract(salary: 1)).save()
        }
        manager.session.flush()
        manager.session.clear()
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
        'asc'     || ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        'desc'    || ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    @Issue('https://github.com/apache/grails-core/issues/15589')
    void 'sort by an association property using a sort map when the where query uses the association'() {
        when:
        final results = Team.where {
            club.name != 'Chelsea'
        }.list(sort: ['club.name': 'desc'])

        then:
        results*.name == ['Manchester United First Team', 'Arsenal First Team']
    }

    @Issue('https://github.com/apache/grails-core/issues/15589')
    void 'sort by an association property when a chained where query uses the association'() {
        given:
        var query = Team.where {}
        query = query.where { club.name != 'Chelsea' }

        when:
        final results = query.list(sort: 'club.name', fetch: [club: 'join'])

        then:
        results*.name == ['Arsenal First Team', 'Manchester United First Team']
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
        results*.name == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }

    void 'sort by an association property when the where query does not use the association'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(sort: 'club.name', order: 'desc')

        then:
        results*.name == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'sort by two properties of the same association when the where query does not use it'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(sort: ['club.name': 'desc', 'club.id': 'asc'])

        then:
        results*.name == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'sort by a path through more than one association'() {
        when:
        final results = Player.where {
            name ==~ '%'
        }.list(sort: 'team.club.name')

        then:
        results*.name == ['Bukayo', 'Cole', 'Bruno']
    }

    void 'sort by a path through more than one association that is also used in the where query'() {
        when:
        final results = Player.where {
            team.club.name ==~ '%e%'
        }.list(sort: 'team.club.name', order: 'desc')

        then:
        results*.name == ['Bruno', 'Cole', 'Bukayo']
    }

    void 'sort by a path through more than one association when the where query uses only the first association'() {
        when:
        final results = Player.where {
            team.name ==~ '%First%'
        }.list(sort: 'team.club.name', order: 'desc')

        then:
        results*.name == ['Bruno', 'Cole', 'Bukayo']
    }

    void 'sort by a path through more than one association that starts with an explicit alias'() {
        when:
        final results = Player.where {
            final t = team
            t.name ==~ '%First%'
        }.list(sort: 't.club.name')

        then:
        results*.name == ['Bukayo', 'Cole', 'Bruno']
    }
}
