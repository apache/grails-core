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

import jakarta.persistence.criteria.JoinType

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

    private static void saveTeamWithoutClub() {
        savePlayer('Unattached Player', new Team(name: 'Unattached First Team').save(failOnError: true))
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

    void 'sort by #sort keeps a team without a club'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(sort: sort)

        then: 'the association is joined with a left join'
        'Unattached First Team' in results*.name

        and: 'the other teams are sorted by their club'
        results*.name - 'Unattached First Team' == expected

        where:
        sort        || expected
        'club.name' || ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'club.id'   || ['MU First Team', 'Arsenal First Team', 'Chelsea First Team']
    }

    void 'sort by an association property joins the association with the join type that fetch requests'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(sort: 'club.name', fetch: [club: joinType])

        then:
        ('Unattached First Team' in results*.name) == kept
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']

        where:
        joinType       || kept
        JoinType.LEFT  || true
        JoinType.INNER || false
    }

    void 'a sort stored on the query keeps a team without a club'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Team.where {
            name ==~ '%First%'
        }.sort('club.name').list()

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
    }

    void 'a sort stored on the query joins the association with the join type that fetch requests'() {
        given:
        saveTeamWithoutClub()

        when: 'the join type is requested after the sort is stored'
        final results = Team.where {
            name ==~ '%First%'
        }.sort('club.name').list(fetch: [club: joinType])

        then:
        ('Unattached First Team' in results*.name) == kept
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']

        where:
        joinType       || kept
        JoinType.LEFT  || true
        JoinType.INNER || false
    }

    void 'a sort stored on the query leaves out the only match when fetch requests an inner join'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Team.where {
            name == 'Unattached First Team'
        }.sort('club.name').list(fetch: [club: JoinType.INNER])

        then:
        results.empty
    }

    void 'a paged list with a sort stored on the query counts the rows of the join type that fetch requests'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Team.where {
            name ==~ '%First%'
        }.sort('club.name').list(max: 2, fetch: [club: joinType])

        then:
        results.size() == 2
        results.totalCount == totalCount

        where:
        joinType       || totalCount
        JoinType.LEFT  || 4
        JoinType.INNER || 3
    }

    void 'find with a sort stored on the query joins the association with the join type that fetch requests'() {
        given:
        saveTeamWithoutClub()

        when:
        final result = Team.where {
            name == 'Unattached First Team'
        }.sort('club.name').find(arguments)

        then:
        (result != null) == found

        where:
        arguments                      || found
        [:]                            || true
        [fetch: [club: JoinType.LEFT]] || true
        [fetch: [club: JoinType.INNER]] || false
    }

    void 'a nested sort stored on the query joins the association with the join type that fetch requests'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Player.where {
            name ==~ '%Player%'
        }.sort('team.club.name').list(fetch: ['team.club': joinType])

        then:
        ('Unattached Player' in results*.name) == kept
        results*.name - 'Unattached Player' == ['Arsenal Player', 'Chelsea Player', 'MU Player']

        where:
        joinType       || kept
        JoinType.LEFT  || true
        JoinType.INNER || false
    }

    void 'a sort stored on the query does not change the join of a criterion that list adds'() {
        given:
        saveTeamWithoutClub()

        when: 'a left join would let the criterion match the team without a club'
        final query = Team.where {
            name ==~ '%First%'
        }
        final additionalCriteria = {
            club {
                isNull('name')
            }
        }

        then: 'the criterion joins the club with an inner join, with and without the sort'
        query.list(additionalCriteria).empty
        query.sort('club.name').list(additionalCriteria).empty
    }

    void 'a nested sort stored on the query does not change the joins of criteria that list adds'() {
        given:
        saveTeamWithoutClub()

        when: 'left joins would let the criterion match the player whose team has no club'
        final query = Player.where {
            name ==~ '%Player%'
        }
        final additionalCriteria = {
            team {
                club {
                    isNull('name')
                }
            }
        }

        then: 'the criteria join the team and the club with inner joins, with and without the sort'
        query.list(additionalCriteria).empty
        query.sort('team.club.name').list(additionalCriteria).empty
    }

    void 'sort by an association property leaves out a team without a club when a criterion requires a club'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Team.where {
            club != null
        }.list(sort: 'club.name')

        then:
        results*.name == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
    }

    void 'a paged list sorted by an association property counts the rows without the association'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(sort: 'club.name', max: 2)

        then:
        results.size() == 2
        results.totalCount == 4
    }

    void 'sort by #sort #direction keeps a player whose team has no club'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Player.where {
            name ==~ '%Player%'
        }.list(sort: sort, order: direction)

        then:
        'Unattached Player' in results*.name

        and: 'the other players are sorted by the club of their team'
        results*.name - 'Unattached Player' == expected

        where:
        sort             | direction || expected
        'team.club.id'   | 'asc'     || ['MU Player', 'Arsenal Player', 'Chelsea Player']
        'team.club.id'   | 'desc'    || ['Chelsea Player', 'Arsenal Player', 'MU Player']
        'team.club.name' | 'asc'     || ['Arsenal Player', 'Chelsea Player', 'MU Player']
        'team.club.name' | 'desc'    || ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void 'sort by a nested association id through an explicit alias keeps a player whose team has no club'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Player.where {
            final t = team
            t.name ==~ '%First%'
        }.list(sort: 't.club.id')

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['MU Player', 'Arsenal Player', 'Chelsea Player']
    }

    void 'sort by a nested association id keeps a player whose team has no club when the where query uses the first association'() {
        given:
        saveTeamWithoutClub()

        when:
        final results = Player.where {
            team.name ==~ '%First%'
        }.list(sort: 'team.club.id')

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['MU Player', 'Arsenal Player', 'Chelsea Player']
    }

    void 'sort by the id of a nested hasOne association'() {
        when:
        final results = Contract.where {
            salary > 0
        }.list(sort: 'player.contract.id', order: 'desc')

        then:
        results*.player*.name == ['Chelsea Player', 'Arsenal Player', 'MU Player']
    }
}
