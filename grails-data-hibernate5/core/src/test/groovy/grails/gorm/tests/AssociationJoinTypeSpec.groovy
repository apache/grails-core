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
import grails.gorm.annotation.Entity
import grails.gorm.tests.entities.Club
import grails.gorm.tests.entities.Contract
import grails.gorm.tests.entities.Player
import grails.gorm.tests.entities.Team
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.hibernate.FetchMode
import org.hibernate.Hibernate
import org.hibernate.criterion.Restrictions
import spock.lang.IgnoreIf

import jakarta.persistence.criteria.JoinType

class AssociationJoinTypeSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    private static final List<String> TEAMS_WITH_A_CLUB = ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Playerless First Team']
    private static final List<String> TEAMS_WITH_PLAYERS = ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Unattached First Team']
    private static final List<String> ALL_TEAMS = (TEAMS_WITH_A_CLUB + 'Unattached First Team').sort()

    /**
     * Whether the specification runs with the {@code grails.gorm.leftJoinAssociationSort} setting, with which
     * a sort by a property of an association joins the association with a left join
     */
    protected boolean isLeftJoinSort() {
        false
    }

    void setupSpec() {
        manager.configuration['grails.gorm.leftJoinAssociationSort'] = leftJoinSort
        manager.registerDomainClasses(Club, Team, Player, Contract, AssociationJoinTypeCategory, AssociationJoinTypeShelf, AssociationJoinTypeTag,
                                      AssociationJoinTypeFace, AssociationJoinTypeNose, AssociationJoinTypePost, AssociationJoinTypeAuthor,
                                      AssociationJoinTypeBook, AssociationJoinTypeSquad, AssociationJoinTypeSquadMember, AssociationJoinTypeTrophy, AssociationJoinTypeMedal,
                                      AssociationJoinTypePrize, AssociationJoinTypeRibbon, Join0_0)
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

    void 'a requested bulk join does not shadow the queried entity alias for #operation'() {
        given:
        final category = new AssociationJoinTypeTag(name: 'Category').save(failOnError: true)
        new Join0_0(name: 'With category', category: category).save(failOnError: true)
        new Join0_0(name: 'Without category').save(failOnError: true, flush: true)
        manager.session.clear()
        final query = new DetachedCriteria(Join0_0).join('category', JoinType.INNER)

        when:
        final changed = mutate.call(query)
        manager.session.clear()

        then:
        changed == 1
        Join0_0.list(sort: 'name')*.name == expected

        where:
        operation | mutate                                             | expected
        'update'  | { criteria -> criteria.updateAll(name: 'Updated') } | ['Updated', 'Without category']
        'delete'  | { criteria -> criteria.deleteAll() }                | ['Without category']
    }

    protected static void savePlayer(String name, Team team) {
        final player = new Player(name: name, team: team)
        player.contract = new Contract(salary: 1000G, player: player)
        player.save(failOnError: true, flush: true)
    }

    protected void saveTeamWithAPlayerWithoutContract() {
        final squad = new Team(club: Club.findByName('Arsenal'), name: 'Squad Team').save(failOnError: true)
        savePlayer('Squad Player A', squad)
        savePlayer('Squad Player B', squad)
        new Player(name: 'Squad Player without contract', team: squad).save(validate: false, flush: true)
        manager.session.clear()
    }

    void 'a criteria query with an inner join that fetch requests returns the same rows with a sort'() {
        when:
        final results = Team.createCriteria().list(sort: 'club.name', fetch: [club: JoinType.INNER]) {
            like('name', '%First%')
        }

        then:
        results*.name.sort() == TEAMS_WITH_A_CLUB
    }

    @IgnoreIf({ instance.leftJoinSort })
    void '#api applies the inner join that the fetch argument requests to a sort by the association'() {
        when:
        final List<Team> results = query.call()

        then:
        results*.name.sort() == TEAMS_WITH_A_CLUB
        results*.club*.name == ['Arsenal', 'Arsenal', 'Chelsea', 'Manchester United']

        where:
        api                  | query
        'findAll by example' | { Team.findAll(new Team(), [sort: 'club.name', fetch: [club: JoinType.INNER]]) }
        'list'               | { Team.list(sort: 'club.name', fetch: [club: JoinType.INNER]) }
        'a paged list'       | { Team.list(sort: 'club.name', max: 10, fetch: [club: JoinType.INNER]) }
    }

    @IgnoreIf({ instance.leftJoinSort })
    void '#api sorted by an association does not find an instance without it when the fetch argument requests an inner join'() {
        expect:
        query.call() == expected

        where:
        api               | query                                                                                                            || expected
        'findAllWhere'    | { Team.findAllWhere([name: 'Unattached First Team'], [sort: 'club.name', fetch: [club: JoinType.INNER]]) }      || []
        'findWhere'       | { Team.findWhere([name: 'Unattached First Team'], [sort: 'club.name', fetch: [club: JoinType.INNER]]) }         || null
        'find by example' | { Team.find(new Team(name: 'Unattached First Team'), [sort: 'club.name', fetch: [club: JoinType.INNER]]) }     || null
    }

    void 'a requested left join applies to an existing criteria #associationForm regardless of #request'() {
        when:
        final arguments = request == 'fetch' ? [fetch: [club: JoinType.LEFT]] : [:]
        final results = Team.createCriteria().list(arguments) {
            if (request == 'before') {
                join('club', JoinType.LEFT)
            }
            if (associationForm == 'alias') {
                createAlias('club', 'c')
                or {
                    eq('c.name', 'Chelsea')
                    eq('name', 'Unattached First Team')
                }
            }
            else {
                or {
                    club { eq('name', 'Chelsea') }
                    eq('name', 'Unattached First Team')
                }
            }
            if (request == 'after') {
                join('club', JoinType.LEFT)
            }
        }

        then:
        results*.name.sort() == ['Chelsea First Team', 'Unattached First Team']

        where:
        [associationForm, request] << [['block', 'alias'], ['before', 'after', 'fetch']].combinations()
    }

    void 'criteria arguments apply #paging before the requested #joinType fetch of #path'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = Team.withSession { session ->
            final criteria = session.createCriteria(Team)
            criteria.add(Restrictions.like('name', '%Team'))
            GrailsHibernateUtil.populateArgumentsForCriteria(manager.hibernateDatastore, Team, criteria,
                    paging + [sort: 'name', order: 'desc', fetch: [(path): joinType]], manager.hibernateDatastore.mappingContext.conversionService)
            criteria.list()
        }

        then:
        results*.name == expected
        results.every { !Hibernate.isInitialized(it.players) }
        results.findAll { it.name == 'Squad Team' }.every { it.players*.name.sort() == ['Squad Player A', 'Squad Player B', 'Squad Player without contract'] }

        where:
        [path, joinType, paging] << [['players', 'players.contract'], [JoinType.LEFT, JoinType.INNER], [[max: 2], [max: 1, offset: 1], [offset: 2]]].combinations()
        expected = paging.offset == 2 ? (joinType == JoinType.LEFT ? ['Playerless First Team', 'MU First Team', 'Chelsea First Team', 'Arsenal First Team'] :
                ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']) : paging.offset == 1 ? ['Squad Team'] : ['Unattached First Team', 'Squad Team']
    }

    void 'a paged criteria requested join keeps every element of a collection on its path'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = Team.createCriteria().list(max: 2, offset: offset, fetch: ['players.contract': joinType]) {
            order('name', 'desc')
        }
        final squad = results.find { it.name == 'Squad Team' }

        then:
        squad != null
        squad.players*.name.sort() == ['Squad Player A', 'Squad Player B', 'Squad Player without contract']

        where:
        [joinType, offset] << [[JoinType.LEFT, JoinType.INNER], [0, 1]].combinations()
    }

    void 'a paged detached criteria keeps the alias of the queried class when the total is read'() {
        when:
        final results = new DetachedCriteria(Team, 't').eq('t.name', 'Arsenal First Team').list(max: 2)

        then:
        results*.name == ['Arsenal First Team']
        results.totalCount == 1
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

    void 'a bulk #operation applies the join requested by a subquery inside a junction'() {
        given:
        new Team(name: 'Bulk linked', club: Club.findByName('Arsenal')).save(failOnError: true)
        new Team(name: 'Bulk unlinked').save(failOnError: true, flush: true)
        manager.session.clear()
        final subquery = Team.where { name ==~ 'Bulk%' }.join('club', JoinType.INNER).id()
        final query = {
            new DetachedCriteria(Team).build {
                or {
                    inList('id', subquery)
                    eq('name', 'No matching team')
                }
            }
        }

        expect:
        query.call().list()*.name == ['Bulk linked']

        when:
        final changed = operation == 'update' ? query.call().updateAll(name: 'Bulk updated') : query.call().deleteAll()
        manager.session.clear()

        then:
        changed == 1
        Team.findByName('Bulk unlinked') != null
        Team.findByName('Bulk linked') == null
        (Team.findByName('Bulk updated') != null) == (operation == 'update')

        where:
        operation << ['update', 'delete']
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

    void 'updateAll applies an inner join that the query requests for an association to the queried class itself'() {
        given:
        final root = new AssociationJoinTypeCategory(name: 'Root category').save(failOnError: true)
        new AssociationJoinTypeCategory(name: 'Child category', parent: root).save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final updated = AssociationJoinTypeCategory.where {
            name ==~ '%category'
        }.join('parent', JoinType.INNER).updateAll(name: 'Updated')
        manager.session.clear()

        then:
        updated == 1
        AssociationJoinTypeCategory.list()*.name.sort() == ['Root category', 'Updated']
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

    void 'a right join keeps one null row for each club that no team refers to'() {
        given:
        new Club(name: 'Another teamless club').save()
        new Club(name: 'Teamless').save(flush: true)
        manager.session.clear()

        when:
        final query = new DetachedCriteria(Team).join('club', JoinType.RIGHT)
        final results = query.list()

        then: 'the team without a club is left out, and the club without a team adds a row without a team'
        results.findAll { it != null }*.name.sort() == TEAMS_WITH_A_CLUB
        results.count { it == null } == 2
        query.count() == 6

        and:
        query.list(max: 10).count { it == null } == 2
        query.list(max: 10).totalCount == 6
    }

    void 'a right join that criteria on the association make keeps one null row for each club that no team refers to when a fetch mode replaced it'() {
        given:
        new Club(name: 'Another teamless club').save()
        new Club(name: 'Teamless').save(flush: true)
        manager.session.clear()

        when:
        final query = Team.where { club.name ==~ '%' }.join('club', JoinType.RIGHT).join('club')
        final results = query.list()

        then: 'the right join is kept for the criteria, so each club without a team adds a row without a team'
        results.findAll { it != null }*.name.sort() == TEAMS_WITH_A_CLUB
        results.count { it == null } == 2
        query.count() == 6

        and:
        query.list(max: 10).count { it == null } == 2
        query.list(max: 10).totalCount == 6
    }

    void 'an alias named like an association does not take the place of the join that the query requests for the association'() {
        given: 'an alias of the players named club, and an alias of the club named players'
        final aliasNamedClub = new DetachedCriteria(Team).build { createAlias('players', 'club') }.join('club', JoinType.INNER)
        final aliasNamedPlayers = new DetachedCriteria(Team).build { createAlias('club', 'players') }.join('players', JoinType.INNER)
        final teamsWithAClubAndPlayers = TEAMS_WITH_A_CLUB.intersect(TEAMS_WITH_PLAYERS).sort()

        expect: 'both joins are inner joins, so the team without a club and the team without players are left out'
        aliasNamedClub.list()*.name.sort() == teamsWithAClubAndPlayers
        aliasNamedClub.count() == 3
        aliasNamedPlayers.list()*.name.sort() == teamsWithAClubAndPlayers
        aliasNamedPlayers.count() == 3
        Team.where { name ==~ '%First%' }.list(fetch: [club: JoinType.INNER]) { createAlias('players', 'club') }*.name.sort() == teamsWithAClubAndPlayers
    }

    void 'a paged #api with a requested #joinType join through a collection keeps its elements'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        when:
        final arguments = [max: 2, offset: offset, sort: 'name', order: 'desc']
        final results = api == 'nested' ? Player.where { name ==~ '%' }.list(arguments + [fetch: ['team.players': joinType]]) :
                api == 'detached' ? new DetachedCriteria(Team).join('players.contract', joinType).list(arguments) :
                        Team.where { name ==~ '%' }.list(arguments + [fetch: ['players.contract': joinType]])
        final squad = api == 'nested' ? results.find { it.team.name == 'Squad Team' }.team : results.find { it.name == 'Squad Team' }

        then:
        squad != null
        squad.players*.name.sort() == ['Squad Player A', 'Squad Player B', 'Squad Player without contract']

        where:
        [joinType, offset, api] << [[JoinType.INNER, JoinType.LEFT], [0, 1], ['where', 'detached', 'nested']].combinations()
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

    void 'deleteAll deletes the instances that list returns when the query requests an inner join of a collection that has a join table'() {
        given:
        saveShelves()

        when:
        final query = AssociationJoinTypeShelf.where { name ==~ 'First%' }.join('tags', JoinType.INNER)
        final listed = query.list()*.name
        final deleted = query.deleteAll()
        manager.session.flush()
        manager.session.clear()

        then:
        listed == ['First tagged shelf']
        deleted == 1

        and: 'the other shelves keep their tags'
        AssociationJoinTypeShelf.list().collect { "${it.name}: ${it.tags*.name.sort()}" }.sort() == ['First untagged shelf: []', 'Other shelf: [y]']
    }

    void 'updateAll updates the instances that list returns when the query requests an inner join of a collection that has a join table'() {
        given:
        saveShelves()

        when:
        final updated = AssociationJoinTypeShelf.where { name ==~ 'First%' }.join('tags', JoinType.INNER).updateAll(name: 'Updated shelf')
        manager.session.clear()

        then:
        updated == 1
        AssociationJoinTypeShelf.list().collect { "${it.name}: ${it.tags*.name.sort()}" }.sort() == ['First untagged shelf: []', 'Other shelf: [y]', 'Updated shelf: [x]']
    }

    void 'a copied query keeps its replacement fetch mode when the original requests another join type'() {
        given:
        final original = new DetachedCriteria(Team).join('club', JoinType.INNER).join('club')
        final copy = original.clone()

        when:
        original.join('club', JoinType.INNER)

        then:
        copy.list()*.name.sort() == ALL_TEAMS
        copy.count() == ALL_TEAMS.size()
        original.list()*.name.sort() == TEAMS_WITH_A_CLUB
        original.count() == TEAMS_WITH_A_CLUB.size()
    }

    protected void saveShelves() {
        new AssociationJoinTypeShelf(name: 'First tagged shelf').addToTags(new AssociationJoinTypeTag(name: 'x')).save(failOnError: true)
        new AssociationJoinTypeShelf(name: 'First untagged shelf').save(failOnError: true)
        new AssociationJoinTypeShelf(name: 'Other shelf').addToTags(new AssociationJoinTypeTag(name: 'y')).save(failOnError: true, flush: true)
        manager.session.clear()
    }

    void 'updateAll applies an inner join of the side of a one-to-one that the other class has the foreign key of'() {
        given: 'one nose of a face, and noses of no face'
        final noses = (1..3).collect { new AssociationJoinTypeNose(freckles: true).save(validate: false) }
        new AssociationJoinTypeFace(name: 'Face with a nose', nose: noses.last()).save(failOnError: true)
        new AssociationJoinTypeFace(name: 'Face without a nose').save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final listed = AssociationJoinTypeNose.where { freckles == true }.join('face', JoinType.INNER).list()*.id
        final updated = AssociationJoinTypeNose.where { freckles == true }.join('face', JoinType.INNER).updateAll(freckles: false)
        manager.session.clear()

        then:
        listed == [noses.last().id]
        updated == 1
        AssociationJoinTypeNose.findAllByFreckles(false)*.id == [noses.last().id]
    }

    void 'count and updateAll apply an inner join requested for a collection of a basic type'() {
        given:
        new AssociationJoinTypePost(title: 'Tagged post', tags: ['x']).save(failOnError: true)
        new AssociationJoinTypePost(title: 'Untagged post').save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final counted = AssociationJoinTypePost.where { title ==~ '%post' }.join('tags', JoinType.INNER).count()
        final updated = AssociationJoinTypePost.where { title ==~ '%post' }.join('tags', JoinType.INNER).updateAll(title: 'Updated post')
        manager.session.clear()

        then:
        counted == 1
        updated == 1
        AssociationJoinTypePost.list()*.title.sort() == ['Untagged post', 'Updated post']
    }

    void '#api joins and fetches the associations before a collection on a path that it requests an inner join for'() {
        given:
        saveTeamWithAPlayerWithoutContract()
        final lonelyTeam = new Team(name: 'Lonely Team').save(failOnError: true)
        new Player(name: 'Lonely Player', team: lonelyTeam).save(validate: false, flush: true)
        manager.session.clear()

        when:
        final List<Player> players = query.call()

        then: 'a player whose team has no player with a contract is left out'
        players*.name.unique().sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Squad Player A', 'Squad Player B', 'Squad Player without contract', 'Unattached Player']

        and: 'a where query returns each player once, and a criteria query once for each player of the fetched team'
        players.size() == rows

        and: 'the team of each player is fetched, with all of its players'
        players.every { Hibernate.isInitialized(it.team) && Hibernate.isInitialized(it.team.players) }
        players.find { it.name == 'Squad Player A' }.team.players.size() == 3

        where:
        api                | query                                                                                                              || rows
        'a where query'    | { Player.where { name ==~ '%Player%' }.join('team.players.contract', JoinType.INNER).list() }                      || 7
        'a criteria query' | { Player.createCriteria().list { like('name', '%Player%'); join('team.players.contract', JoinType.INNER) } }       || 13
    }

    void 'a right join requested for a collection that criteria on the collection join returns the instances that an inner join returns'() {
        given: 'a book without an author'
        final author = new AssociationJoinTypeAuthor(name: 'Author').save(failOnError: true)
        new AssociationJoinTypeBook(title: 'The book', author: author).save(failOnError: true)
        new AssociationJoinTypeBook(title: 'The book without an author').save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final query = AssociationJoinTypeAuthor.where { books { title ==~ 'The%' } }.join('books', JoinType.RIGHT)

        then:
        query.list()*.name == ['Author']
        query.count() == 1
    }

    void 'a fetch mode requested after a join type keeps the join type for the join that criteria on the association make: #description'() {
        expect:
        query.call()*.name.sort() == ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team']

        where:
        description                  | query
        'the fetch argument of list' | { Team.where { club.name == 'Arsenal' || name == 'Unattached First Team' }.join('club', JoinType.LEFT).list(fetch: [club: 'join']) }
        'join without a join type'   | { Team.where { club.name == 'Arsenal' || name == 'Unattached First Team' }.join('club', JoinType.LEFT).join('club').list() }
        'select'                     | { Team.where { club.name == 'Arsenal' || name == 'Unattached First Team' }.join('club', JoinType.LEFT).select('club').list() }
    }

    void 'a fetch mode that a criteria query requests after a join type keeps the join type for the join that criteria on the association make: #description'() {
        expect:
        query.call()*.name.sort() == expected

        where:
        description                                  | query                                                                                                                                                               || expected
        'join without a join type'                   | { Team.createCriteria().list { join('club', JoinType.LEFT); join('club'); or { club { eq('name', 'Arsenal') }; eq('name', 'Unattached First Team') } } }          || ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team']
        'select'                                     | { Team.createCriteria().list { join('club', JoinType.LEFT); select('club'); or { club { eq('name', 'Arsenal') }; eq('name', 'Unattached First Team') } } }        || ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team']
        'fetchMode'                                  | { Team.createCriteria().list { join('club', JoinType.LEFT); fetchMode('club', FetchMode.JOIN); or { club { eq('name', 'Arsenal') }; eq('name', 'Unattached First Team') } } } || ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team']
        'the fetch argument of list'                 | { Team.createCriteria().list(fetch: [club: 'join']) { join('club', JoinType.LEFT); or { club { eq('name', 'Arsenal') }; eq('name', 'Unattached First Team') } } } || ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team']
        'join without a join type after the block'   | { Team.createCriteria().list { or { club { eq('name', 'Arsenal') }; eq('name', 'Unattached First Team') }; join('club', JoinType.LEFT); join('club') } }          || ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team']
        'an alias'                                   | { Team.createCriteria().list { join('club', JoinType.LEFT); join('club'); createAlias('club', 'c'); or { eq('c.name', 'Arsenal'); eq('name', 'Unattached First Team') } } } || ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team']
        'a paged list'                               | { Team.createCriteria().list(max: 10) { join('club', JoinType.LEFT); join('club'); or { club { eq('name', 'Arsenal') }; eq('name', 'Unattached First Team') } } } || ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team']
        'a nested block'                             | { Player.createCriteria().list { join('team.club', JoinType.LEFT); join('team.club'); or { team { club { eq('name', 'Arsenal') } }; eq('name', 'Unattached Player') } } } || ['Arsenal Player', 'Unattached Player']
        'an alias made through another alias'        | { Player.createCriteria().list { join('team.club', JoinType.LEFT); select('team.club'); createAlias('team', 't'); createAlias('t.club', 'c'); or { eq('c.name', 'Arsenal'); eq('name', 'Unattached Player') } } } || ['Arsenal Player', 'Unattached Player']
        'a join type requested after the fetch mode' | { Team.createCriteria().list { join('club', JoinType.LEFT); join('club'); join('club', JoinType.INNER); or { club { eq('name', 'Arsenal') }; eq('name', 'Unattached First Team') } } } || ['Arsenal First Team', 'Playerless First Team']
        'a join type in the fetch argument of list'  | { Team.createCriteria().list(fetch: [club: JoinType.INNER]) { join('club', JoinType.LEFT); join('club'); or { club { eq('name', 'Arsenal') }; eq('name', 'Unattached First Team') } } } || ['Arsenal First Team', 'Playerless First Team']
    }

    void 'count and the total of a paged criteria query count the instances that a join type replaced by a fetch mode keeps'() {
        when:
        final page = Team.createCriteria().list(max: 2, sort: 'name') {
            join('club', JoinType.LEFT)
            join('club')
            or {
                club { eq('name', 'Arsenal') }
                eq('name', 'Unattached First Team')
            }
        }
        final count = Team.createCriteria().count {
            join('club', JoinType.LEFT)
            join('club')
            or {
                club { eq('name', 'Arsenal') }
                eq('name', 'Unattached First Team')
            }
        }

        then:
        page*.name == ['Arsenal First Team', 'Playerless First Team']
        page.totalCount == 3
        count == 3
    }

    void 'a join type that a fetch mode replaced in a criteria query does not join the association by itself'() {
        expect: 'without criteria on the club, the fetch mode alone decides, and an inner join leaves out no team'
        Team.createCriteria().list { like('name', '%First%'); join('club', JoinType.INNER); join('club') }*.name.sort() == ALL_TEAMS
        Team.createCriteria().count { like('name', '%First%'); join('club', JoinType.INNER); select('club') } == ALL_TEAMS.size()
    }

    void 'a join type that a fetch mode replaced in a criteria query does not change the join that a join type requested below the association makes'() {
        given:
        final root = new AssociationJoinTypeCategory(name: 'Root category').save(failOnError: true)
        final child = new AssociationJoinTypeCategory(name: 'Child category', parent: root).save(failOnError: true)
        new AssociationJoinTypeCategory(name: 'Grandchild category', parent: child).save(failOnError: true, flush: true)
        final allCategories = ['Child category', 'Grandchild category', 'Root category']

        expect: 'the left join of parent.parent joins the parent with a left join, which keeps the category without a parent'
        AssociationJoinTypeCategory.createCriteria().list {
            join('parent', JoinType.INNER)
            join('parent')
            join('parent.parent', JoinType.LEFT)
        }*.name.sort() == allCategories
        AssociationJoinTypeCategory.where { name ==~ '%category' }
                .join('parent', JoinType.INNER)
                .join('parent')
                .join('parent.parent', JoinType.LEFT)
                .list()*.name.sort() == allCategories
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
        results.every { !Hibernate.isInitialized(it.players) }
        results.every { it.players.size() == 1 }
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

    void 'an inner join requested for an association of a collection fetches the collection with all of its elements'() {
        given: 'a team with a player that has no contract'
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = Team.where {
            name ==~ '%Team%'
        }.list(fetch: ['players.contract': JoinType.INNER])
        final squad = results.find { it.name == 'Squad Team' }

        then: 'the team without players is left out'
        results*.name.sort() == (TEAMS_WITH_PLAYERS + 'Squad Team').sort()

        and: 'the inner join does not leave the player without a contract out of the fetched collection'
        Hibernate.isInitialized(squad.players)
        squad.players*.name.sort() == ['Squad Player A', 'Squad Player B', 'Squad Player without contract']
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

    void 'a criteria sort keeps an explicitly requested left join from #request'() {
        when:
        final results = query.call()

        then:
        results*.name.sort() == expected
        results.totalCount == expected.size()

        where:
        request      | query                                                                                                                          | expected
        'fetch'      | { Team.createCriteria().list(max: 10, sort: 'club.name', fetch: [club: JoinType.LEFT]) {} }                                       | ALL_TEAMS
        'join'       | { Team.createCriteria().list(max: 10, sort: 'club.name') { join('club', JoinType.LEFT) } }                                         | ALL_TEAMS
        'nested fetch' | { Player.createCriteria().list(max: 10, sort: 'team.club.name', fetch: ['team.club': JoinType.LEFT]) {} }                     | ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Unattached Player']
        'nested join'  | { Player.createCriteria().list(max: 10, sort: 'team.club.name') { join('team.club', JoinType.LEFT) } }                       | ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Unattached Player']
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

        and: 'the limited rows do not initialize a partial collection'
        results.every { !Hibernate.isInitialized(it.players) }
        results.every { it.players.size() == 1 }

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

    void 'an inner join that a criteria query requests for an association of a collection fetches the collection with all of its elements'() {
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

        then: 'each team is counted once'
        results*.name.unique().sort() == (TEAMS_WITH_PLAYERS + 'Squad Team').sort()
        count == 5
        paged.totalCount == 5

        and: 'returned once for each of its players, as a criteria query returns an instance for a fetched collection'
        results.size() == 7

        and: 'the paged query loads the collection separately and returns each root once'
        paged.size() == 5

        and: 'the inner join does not leave the player without a contract out of the fetched collection'
        Hibernate.isInitialized(squad.players)
        squad.players*.name.sort() == ['Squad Player A', 'Squad Player B', 'Squad Player without contract']
    }

    void 'a requested inner join reuses the alias of a criteria query'() {
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

    void 'criteria on an association use its requested inner join'() {
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

    void 'the total of a paged detached criteria with a root alias is counted with that alias: #description'() {
        when:
        final results = query.call()

        then:
        results.totalCount == total

        where:
        description                                  | query                                                                                                              || total
        'criteria that name the alias'               | { new DetachedCriteria(Team, 't').eq('t.name', 'MU First Team').list(max: 2) }                                     || 1
        'criteria that name it and an inner join'    | { new DetachedCriteria(Team, 't').eq('t.name', 'MU First Team').join('players', JoinType.INNER).list(max: 2) }     || 1
        'an inner join of a collection'              | { new DetachedCriteria(Team, 't').join('players', JoinType.INNER).list(max: 2) }                                   || 4
        'an inner join of a path through collection' | { new DetachedCriteria(Team, 't').join('players.contract', JoinType.INNER).list(max: 2) }                          || 4
        'an inner join of an association'            | { new DetachedCriteria(Team, 't').join('club', JoinType.INNER).list(max: 2) }                                      || 4
        'an alias that ends like one of a subquery'  | { new DetachedCriteria(Team, 't_sub').join('players', JoinType.INNER).list(max: 2) }                               || 4
        'an alias that a subquery used to have'      | { new DetachedCriteria(Team, 'join_subquery').join('players', JoinType.INNER).list(max: 2) }                       || 4
        'an alias of a join that a subquery had'     | { new DetachedCriteria(Team, 'join_subquery0').join('players', JoinType.INNER).list(max: 2) }                      || 4
    }

    /**
     * A left join requested for a collection that the query has criteria on:
     *
     * <pre>
     * | criteria on the collection | another collection fetched | squads returned                   | fetched collection            |
     * |----------------------------|----------------------------|-----------------------------------|-------------------------------|
     * | need a value               | none                       | those with a matching element     | none                          |
     * | need a value               | of the same class          | those with a matching element     | holds its own elements        |
     * | need a value               | of another class           | those with a matching element     | holds its own elements        |
     * | accept a missing element   | none                       | also those without an element     | none                          |
     * | accept a missing element   | of the same class          | also those without an element     | holds its own elements        |
     * </pre>
     */
    void 'find with a left join of a collection that criteria use returns the instance and loads the collection that it fetches'() {
        given:
        saveSquads()

        when:
        final squad = AssociationJoinTypeSquad.where { players { kind == null } }.join('players', JoinType.LEFT).join('reserves').find()

        then:
        squad.name == 'Bench'
        Hibernate.isInitialized(squad.reserves)
        squad.reserves*.name == ['Bench reserve']
    }

    void 'a paged list with a left join of a collection that criteria use counts the instance without an element'() {
        given:
        saveSquads()

        when:
        final results = AssociationJoinTypeSquad.where { players { kind == null } }.join('players', JoinType.LEFT).join('reserves').list(max: 10)

        then:
        results*.name == ['Bench']
        results.totalCount == 1
        Hibernate.isInitialized(results.first().reserves)
    }

    void 'a left join of a collection that criteria use loads a collection fetched through a map after the query'() {
        given:
        saveSquads()

        when:
        final results = AssociationJoinTypeSquad.where { players { kind == 'Regular' } }.list(fetch: [players: JoinType.LEFT, 'prizes.ribbons': JoinType.LEFT])

        then:
        results*.name == ['Full']
        Hibernate.isInitialized(results.first().prizes)
        Hibernate.isInitialized(results.first().prizes['main'].ribbons)
        results.first().prizes['main'].ribbons*.name == ['Blue']
    }

    void 'a projection with a left join of a collection that criteria use returns its values'() {
        given:
        saveSquads()

        when:
        final names = AssociationJoinTypeSquad.where { players { kind == null } }.join('players', JoinType.LEFT).join('reserves').property('name').list()

        then:
        names == ['Bench']
    }

    protected void saveSquads() {
        final full = new AssociationJoinTypeSquad(name: 'Full').save(failOnError: true)
        new AssociationJoinTypeSquadMember(name: 'Regular', kind: 'Regular', squad: full).save(failOnError: true)
        final reserve = new AssociationJoinTypeSquadMember(name: 'Reserve', kind: 'Reserve', reserveSquad: full).save(failOnError: true)
        new AssociationJoinTypeMedal(name: 'Gold', member: reserve).save(failOnError: true)
        new AssociationJoinTypeTrophy(name: 'Cup', squad: full).save(failOnError: true)
        final prize = new AssociationJoinTypePrize(name: 'Prize').save(failOnError: true)
        new AssociationJoinTypeRibbon(name: 'Blue', prize: prize).save(failOnError: true)
        full.prizes = [main: prize]
        full.save(failOnError: true)
        final bench = new AssociationJoinTypeSquad(name: 'Bench').save(failOnError: true)
        new AssociationJoinTypeSquadMember(name: 'Bench reserve', kind: 'Reserve', reserveSquad: bench).save(failOnError: true, flush: true)
        manager.session.clear()
    }
}

@Entity
class AssociationJoinTypeMedal {
    String name
    static belongsTo = [member: AssociationJoinTypeSquadMember]
}

@Entity
class AssociationJoinTypePrize {
    String name
    static hasMany = [ribbons: AssociationJoinTypeRibbon]
}

@Entity
class AssociationJoinTypeRibbon {
    String name
    static belongsTo = [prize: AssociationJoinTypePrize]
}

@Entity
class AssociationJoinTypeSquad {
    String name
    Map<String, AssociationJoinTypePrize> prizes
    static hasMany = [players: AssociationJoinTypeSquadMember, reserves: AssociationJoinTypeSquadMember, trophies: AssociationJoinTypeTrophy, prizes: AssociationJoinTypePrize]
    static mappedBy = [players: 'squad', reserves: 'reserveSquad']

    static constraints = {
        prizes nullable: true
    }
}

@Entity
class AssociationJoinTypeSquadMember {
    String name
    String kind
    AssociationJoinTypeSquad squad
    AssociationJoinTypeSquad reserveSquad
    static hasMany = [medals: AssociationJoinTypeMedal]

    static constraints = {
        kind nullable: true
        squad nullable: true
        reserveSquad nullable: true
    }
}

@Entity
class AssociationJoinTypeTrophy {
    String name
    static belongsTo = [squad: AssociationJoinTypeSquad]
}

@Entity
class AssociationJoinTypeShelf {
    String name
    static hasMany = [tags: AssociationJoinTypeTag]
}

@Entity
class AssociationJoinTypeTag {
    String name
}

@Entity
class AssociationJoinTypeFace {
    String name
    AssociationJoinTypeNose nose

    static constraints = {
        nose nullable: true
    }
}

@Entity
class AssociationJoinTypeNose {
    boolean freckles
    static belongsTo = [face: AssociationJoinTypeFace]
}

@Entity
class AssociationJoinTypePost {
    String title
    static hasMany = [tags: String]
}

@Entity
class AssociationJoinTypeAuthor {
    String name
    static hasMany = [books: AssociationJoinTypeBook]
}

@Entity
class AssociationJoinTypeBook {
    String title
    AssociationJoinTypeAuthor author

    static constraints = {
        author nullable: true
    }
}

@Entity
class AssociationJoinTypeCategory {
    String name
    AssociationJoinTypeCategory parent

    static constraints = {
        parent nullable: true
    }
}

// Its decapitalized name collides with the first generated alias of a requested bulk join.
@Entity
class Join0_0 {
    String name
    AssociationJoinTypeTag category

    static constraints = {
        category nullable: true
    }
}
