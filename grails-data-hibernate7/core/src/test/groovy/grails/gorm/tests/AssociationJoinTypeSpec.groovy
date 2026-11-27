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
import grails.gorm.PagedResultList
import grails.gorm.annotation.Entity
import grails.gorm.tests.entities.Club
import grails.gorm.tests.entities.Contract
import grails.gorm.tests.entities.Player
import grails.gorm.tests.entities.Team
import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.query.Query
import org.hibernate.FetchMode
import org.hibernate.Hibernate
import org.hibernate.loader.MultipleBagFetchException
import spock.lang.Shared

import jakarta.persistence.criteria.JoinType

class AssociationJoinTypeSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    private static final List<String> TEAMS_WITH_A_CLUB = ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Playerless First Team']
    private static final List<String> TEAMS_WITH_PLAYERS = ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Unattached First Team']
    private static final List<String> ALL_TEAMS = (TEAMS_WITH_A_CLUB + 'Unattached First Team').sort()

    @Shared
    final AssociationSortSqlCapture sqlCapture = new AssociationSortSqlCapture()

    void setupSpec() {
        manager.grailsConfig.hibernate.session_factory.statement_inspector = sqlCapture
        manager.registerDomainClasses(Club, Team, Player, Contract, AssociationJoinTypeCategory, AssociationJoinTypeShelf, AssociationJoinTypeTag,
                                      AssociationJoinTypeFace, AssociationJoinTypeNose, AssociationJoinTypePost, AssociationJoinTypeAuthor,
                                      AssociationJoinTypeBook, AssociationJoinTypeSquad, AssociationJoinTypeSquadMember, AssociationJoinTypeTrophy, AssociationJoinTypeMedal,
                                       AssociationJoinTypePrize, AssociationJoinTypeRibbon,
                                       RequestedJoinBagTeam, RequestedJoinBagMember, RequestedJoinBagTrophy,
                                       RequestedJoinNestedBagOwner, RequestedJoinNestedBagChild, RequestedJoinNestedBagLeaf,
                                       RequestedJoinCompositeItem, RequestedJoinCompositeTag,
                                       AssociationSortBook, AssociationSortAuthor, RequestedJoinSortedTeam)
    }

    void 'a join type requested for a path through an embedded component is left alone by #description, as with Hibernate 5'() {
        given: 'a book with an author, and one whose component has none'
        final king = new AssociationSortAuthor(name: 'King').save(failOnError: true)
        new AssociationSortBook(title: 'It', details: new AssociationSortBookDetails(isbn: '1', author: king)).save(failOnError: true)
        new AssociationSortBook(title: 'Anonymous', details: new AssociationSortBookDetails(isbn: '3')).save(failOnError: true, flush: true)
        manager.session.clear()

        expect:
        query.call() == expected

        where:
        description                  | query                                                                                                                       || expected
        'a where query'              | { AssociationSortBook.where { title ==~ '%' }.join('details.author', JoinType.INNER).list(sort: 'title')*.title }           || ['Anonymous', 'It']
        'the count of a where query' | { AssociationSortBook.where { title ==~ '%' }.join('details.author', JoinType.INNER).count() }                              || 2
        'a criteria query'           | { AssociationSortBook.createCriteria().list { join('details.author', JoinType.INNER); order('title') }*.title }             || ['Anonymous', 'It']
        'list'                       | { AssociationSortBook.list(fetch: ['details.author': JoinType.INNER], sort: 'title')*.title }                               || ['Anonymous', 'It']
        'a paged list'               | { AssociationSortBook.list(fetch: ['details.author': JoinType.INNER], max: 10).totalCount }                                 || 2
        'deleteAll'                  | { AssociationSortBook.where { title ==~ '%' }.join('details.author', JoinType.INNER).deleteAll() }                          || 2
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

    void '#api with an empty map returns #expected when the fetch argument requests a join type'() {
        expect: 'as without the join type, a query by no property finds nothing'
        query.call() == expected

        where:
        api            | query                                                                       || expected
        'findWhere'    | { Team.findWhere([:], [fetch: [club: JoinType.LEFT]]) }                     || null
        'findAllWhere' | { Team.findAllWhere([:], [fetch: [club: JoinType.LEFT]]) }                  || null
        'findWhere'    | { Team.findWhere(null, [fetch: [club: JoinType.INNER]]) }                   || null
        'findAllWhere' | { Team.findAllWhere(null, [fetch: [club: JoinType.INNER]]) }                || null
    }

    void 'findAllWhere with a join type in the fetch argument and max returns a list, as without the join type'() {
        when:
        final results = Team.findAllWhere([club: Club.findByName('Arsenal')], [fetch: [club: JoinType.INNER], max: 1, sort: 'name'])

        then: 'a list, as the HQL and the sorted query of findAllWhere return, and as with Hibernate 5'
        !(results instanceof PagedResultList)
        results*.name == ['Arsenal First Team']
    }

    void 'findWhere with a join type in the fetch argument selects one team when several match'() {
        given:
        final arsenal = Club.findByName('Arsenal')
        sqlCapture.statements.clear()

        when:
        final team = Team.findWhere([club: arsenal], [fetch: [club: JoinType.INNER], sort: 'name', order: 'desc'])

        then: 'with one query, which is limited to one row, as with Hibernate 5'
        team.name == 'Playerless First Team'
        sqlCapture.statements.size() == 1
    }

    void 'list with a join type in the fetch argument sorts by the default sort of the mapping'() {
        given: 'teams saved out of the order of their names, one of them without a club'
        final club = Club.findByName('Arsenal')
        new RequestedJoinSortedTeam(name: 'Charlie', club: club).save(failOnError: true)
        new RequestedJoinSortedTeam(name: 'Alpha').save(failOnError: true)
        new RequestedJoinSortedTeam(name: 'Bravo', club: club).save(failOnError: true, flush: true)
        manager.session.clear()

        expect: 'the same order, and the same first page, as without the join type'
        RequestedJoinSortedTeam.list()*.name == ['Alpha', 'Bravo', 'Charlie']
        RequestedJoinSortedTeam.list(fetch: [club: JoinType.LEFT])*.name == ['Alpha', 'Bravo', 'Charlie']
        RequestedJoinSortedTeam.list(fetch: [club: JoinType.INNER])*.name == ['Bravo', 'Charlie']
        RequestedJoinSortedTeam.list(max: 2, fetch: [club: JoinType.LEFT])*.name == RequestedJoinSortedTeam.list(max: 2)*.name
        RequestedJoinSortedTeam.list(fetch: [club: JoinType.LEFT], order: 'desc')*.name == RequestedJoinSortedTeam.list(order: 'desc')*.name
    }

    void 'list with max 0 and a join type in the fetch argument lists no team and counts the teams that the join keeps'() {
        when:
        final page = Team.list(max: 0, fetch: [club: joinType])

        then: 'as without the join type, max 0 lists nothing, and the total counts what the query would list'
        page.empty
        page.totalCount == total
        Team.list(max: 0).empty

        where:
        joinType       || total
        JoinType.LEFT  || ALL_TEAMS.size()
        JoinType.INNER || TEAMS_WITH_A_CLUB.size()
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

    void 'a bulk #operation honors a subquery requested join inside a junction'() {
        given:
        new Team(name: 'Bulk linked', club: Club.findByName('Arsenal')).save(failOnError: true)
        new Team(name: 'Bulk unlinked').save(failOnError: true, flush: true)
        manager.session.clear()
        final subquery = Team.where { name ==~ 'Bulk%' }.join('club', JoinType.INNER).id()
        final query = new DetachedCriteria(Team).build {
            or {
                inList('id', subquery)
                eq('name', 'No matching team')
            }
        }

        expect:
        query.list()*.name == ['Bulk linked']

        when:
        final changed = operation == 'update' ? query.updateAll(name: 'Bulk updated') : query.deleteAll()
        manager.session.clear()

        then:
        changed == 1
        Team.findAllByNameLike('Bulk%')*.name.sort() == (operation == 'update' ? ['Bulk unlinked', 'Bulk updated'] : ['Bulk unlinked'])

        where:
        operation << ['update', 'delete']
    }

    void 'a copied query retains its replacement fetch mode when the original requests an inner join'() {
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

    void 'a join requested inside an association block uses the complete path'() {
        expect:
        Player.createCriteria().list {
            team { join('club', JoinType.INNER) }
        }*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player']
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

    void 'a requested left join supplied #request the block belongs only to that builder call'() {
        given:
        final criteria = Team.createCriteria()
        final arguments = request == 'fetch' ? [max: 10, fetch: [club: JoinType.LEFT]] : [max: 10]
        final page = criteria.list(arguments) {
            if (request == 'before') {
                join('club', JoinType.LEFT)
            }
            club { order('name') }
            if (request == 'after') {
                join('club', JoinType.LEFT)
            }
        }

        when:
        final next = criteria.list { club { order('name') } }

        then:
        page*.name.sort() == ALL_TEAMS
        page.totalCount == ALL_TEAMS.size()
        next*.name.sort() == TEAMS_WITH_A_CLUB

        where:
        request << ['before', 'after', 'fetch']
    }

    void 'a block-local association sort honors a root #joinType request supplied #request the block'() {
        when:
        final arguments = request == 'fetch' ? [max: 10, fetch: ['team.club': joinType]] : [max: 10]
        final results = Player.createCriteria().list(arguments) {
            if (request == 'before') {
                join('team.club', joinType)
            }
            team { order('club.name') }
            if (request == 'after') {
                join('team.club', joinType)
            }
        }

        then:
        results*.name.contains('Unattached Player') == (joinType == JoinType.LEFT)
        results*.name - 'Unattached Player' == ['Arsenal Player', 'Chelsea Player', 'MU Player']
        results.totalCount == (joinType == JoinType.LEFT ? 4 : 3)

        where:
        [joinType, request] << [[JoinType.LEFT, JoinType.INNER], ['before', 'after', 'fetch']].combinations()
    }

    void 'a nested left join supplied #request the blocks keeps the alternative without a club'() {
        given:
        final equivalent = Player.where { team.club.name == 'Chelsea' || name == 'Unattached Player' }
                .join('team.club', JoinType.LEFT)

        when:
        final arguments = request == 'fetch' ? [max: 10, fetch: ['team.club': JoinType.LEFT]] : [max: 10]
        final results = Player.createCriteria().list(arguments) {
            if (request == 'before') {
                join('team.club', JoinType.LEFT)
            }
            or {
                team { club { eq('name', 'Chelsea') } }
                eq('name', 'Unattached Player')
            }
            if (request == 'after') {
                join('team.club', JoinType.LEFT)
            }
        }

        then:
        results*.name.sort() == ['Chelsea Player', 'Unattached Player']
        results.totalCount == 2

        and: 'the equivalent where query has the same rows'
        equivalent.list()*.name.sort() == results*.name.sort()

        where:
        request << ['before', 'after', 'fetch']
    }

    void 'an explicit nested inner block retains its restriction with a left request #request the blocks'() {
        when:
        final arguments = request == 'fetch' ? [fetch: ['team.club': JoinType.LEFT]] : [:]
        final results = Player.createCriteria().list(arguments) {
            if (request == 'before') {
                join('team.club', JoinType.LEFT)
            }
            or {
                team { club(0) { eq('name', 'Chelsea') } }
                eq('name', 'Unattached Player')
            }
            if (request == 'after') {
                join('team.club', JoinType.LEFT)
            }
        }

        then:
        results*.name == ['Chelsea Player']

        where:
        request << ['before', 'after', 'fetch']
    }

    void 'a left join followed by #mode and a default block keeps its criteria join type'() {
        when:
        final results = Team.createCriteria().list(max: 10) {
            join('club', JoinType.LEFT)
            "$mode"('club')
            or {
                club { eq('name', 'Chelsea') }
                eq('name', 'Unattached First Team')
            }
        }

        then:
        results*.name.sort() == ['Chelsea First Team', 'Unattached First Team']
        results.totalCount == 2

        where:
        mode << ['join', 'select']
    }

    void 'a left join followed only by an untyped join remains nonrestricting'() {
        expect:
        Team.createCriteria().list {
            join('club', JoinType.LEFT)
            join('club')
        }*.name.sort() == ALL_TEAMS
    }

    void 'an explicit parent #parentType wins over a nested fetch through #api with parent first #parentFirst'() {
        given:
        new Player(name: 'No team').save(validate: false, flush: true)
        manager.session.clear()
        final fetches = parentFirst ? [team: parentType, 'team.club': JoinType.LEFT] : ['team.club': JoinType.LEFT, team: parentType]
        final arguments = [fetch: fetches, max: 10]

        when:
        final results = api == 'criteria' ? Player.createCriteria().list(arguments) { } :
                api == 'static' ? Player.list(arguments) :
                api == 'where' ? Player.where { name != 'Missing' }.list(arguments) :
                new DetachedCriteria(Player).list(arguments)

        then:
        results*.name.sort() == (parentType == JoinType.LEFT ?
                ['Arsenal Player', 'Chelsea Player', 'MU Player', 'No team', 'Unattached Player'] :
                ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Unattached Player'])
        results.totalCount == results.size()
        results.findAll { it.team != null }.every { Hibernate.isInitialized(it.team) && Hibernate.isInitialized(it.team.club) }

        where:
        [parentType, parentFirst, api] << [[JoinType.INNER, JoinType.LEFT], [false, true], ['criteria', 'static', 'where', 'detached']].combinations()
    }

    void 'a parent type replaced by #mode stays replaced with a nested fetch and parent first #parentFirst'() {
        given:
        new Player(name: 'No team').save(validate: false, flush: true)
        manager.session.clear()
        final query = new DetachedCriteria(Player)
        if (!parentFirst) {
            query.join('team.club', JoinType.LEFT)
        }
        query.join('team', JoinType.INNER)
        query."$mode"('team')
        if (parentFirst) {
            query.join('team.club', JoinType.LEFT)
        }

        when:
        final results = query.list(max: 10)

        then:
        results*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player', 'No team', 'Unattached Player']
        results.totalCount == 5
        query.count() == 5

        where:
        [mode, parentFirst] << [['join', 'select'], [false, true]].combinations()
    }

    void 'a parent #parentType requested by join agrees across list count and projection with parent first #parentFirst'() {
        given:
        new Player(name: 'No team').save(validate: false, flush: true)
        manager.session.clear()
        final query = new DetachedCriteria(Player)
        if (parentFirst) {
            query.join('team', parentType)
        }
        query.join('team.club', JoinType.LEFT)
        if (!parentFirst) {
            query.join('team', parentType)
        }
        final expected = parentType == JoinType.LEFT ?
                ['Arsenal Player', 'Chelsea Player', 'MU Player', 'No team', 'Unattached Player'] :
                ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Unattached Player']

        expect:
        query.list()*.name.sort() == expected
        query.count() == expected.size()
        query.property('name').list().sort() == expected
        Player.createCriteria().list {
            if (parentFirst) {
                join('team', parentType)
            }
            join('team.club', JoinType.LEFT)
            if (!parentFirst) {
                join('team', parentType)
            }
        }*.name.sort() == expected

        where:
        [parentType, parentFirst] << [[JoinType.INNER, JoinType.LEFT], [false, true]].combinations()
    }

    void 'a nested right join has the same rows in its list count and paged total'() {
        given: 'a playerless team already refers to Arsenal, and two clubs have no teams'
        new Club(name: 'Teamless A').save(failOnError: true)
        new Club(name: 'Teamless B').save(failOnError: true, flush: true)
        manager.session.clear()
        final query = new DetachedCriteria(Player).join('team.club', JoinType.RIGHT)

        when:
        final results = query.list()
        final page = query.list(max: 10)

        then:
        results.findAll { it != null }*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player']
        results.count { it == null } == 2
        page.size() == results.size()
        query.count() == results.size()
        page.totalCount == results.size()
    }

    void 'a right join keeps unmatched null rows with collection fetch #fetch and paging #paged'() {
        given:
        new Club(name: 'Teamless A').save(failOnError: true)
        new Club(name: 'Teamless B').save(failOnError: true)
        savePlayer('Second Arsenal Player', Team.findByName('Arsenal First Team'))
        manager.session.clear()
        final query = new DetachedCriteria(Team).join('club', JoinType.RIGHT)
        final arguments = [fetch: [players: fetch]]
        if (paged) {
            arguments.max = 10
        }

        when:
        final results = query.list(arguments)

        then:
        results.findAll { it != null }*.name.sort() == TEAMS_WITH_A_CLUB
        results.count { it == null } == 2
        query.count() == results.size()
        !paged || results.totalCount == results.size()
        results.findAll { it != null }.every { Hibernate.isInitialized(it.players) }
        results.find { it?.name == 'Arsenal First Team' }.players*.name.sort() == ['Arsenal Player', 'Second Arsenal Player']

        where:
        [fetch, paged] << [[JoinType.LEFT, 'join'], [false, true]].combinations()
    }

    void 'right join pages retain each unmatched row when a collection is fetched'() {
        given:
        new Club(name: 'Teamless A').save(failOnError: true)
        new Club(name: 'Teamless B').save(failOnError: true, flush: true)
        manager.session.clear()
        final query = new DetachedCriteria(Team).join('club', JoinType.RIGHT)

        when:
        final pages = (0..<6).collect { offset ->
            query.list(max: 1, offset: offset, sort: 'id', fetch: [players: JoinType.LEFT])
        }
        final rows = pages.collectMany { it.toList() }

        then:
        pages.every { it.size() == 1 && it.totalCount == 6 }
        rows.findAll { it != null }*.name.sort() == TEAMS_WITH_A_CLUB
        rows.count { it == null } == 2
    }

    void 'right join pages sorted by a fetched collection count the rows without a team'() {
        given: 'two clubs without a team, and a team with two players'
        new Club(name: 'Teamless A').save(failOnError: true)
        new Club(name: 'Teamless B').save(failOnError: true)
        savePlayer('Second Arsenal Player', Team.findByName('Arsenal First Team'))
        manager.session.clear()
        final query = new DetachedCriteria(Team).join('club', JoinType.RIGHT)

        when: 'a page of one row for each row that the total counts, in the order of the players and then of the teams'
        final sort = ['players.name': 'asc', id: 'asc']
        final total = query.list(max: 1, sort: sort, fetch: [players: JoinType.LEFT]).totalCount
        final pages = (0..<total).collect { offset ->
            query.list(max: 1, offset: offset, sort: sort, fetch: [players: JoinType.LEFT])
        }
        final rows = pages.collectMany { it.toList() }

        then: 'a row for each player of a team with a club, one for the team without players, and one for each club without a team'
        total == 7
        pages.every { it.size() == 1 && it.totalCount == total }
        rows.findAll { it != null }*.name.unique().sort() == TEAMS_WITH_A_CLUB
        rows.count { it?.name == 'Arsenal First Team' } == 2
        rows.count { it == null } == 2
    }

    void 'the total of #api counts each instance once when the list fetches a collection behind an association'() {
        given: 'a team with three players, so that the join of the players of a player has nine rows for them'
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = query.call()

        then: 'each player once, and a total that counts the players'
        results*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Squad Player A', 'Squad Player B', 'Squad Player without contract', 'Unattached Player']
        results.totalCount == results.size()

        where:
        api                  | query
        'a detached criteria' | { new DetachedCriteria(Player).join('team.players', JoinType.LEFT).list(max: 10, sort: 'team.players.name') }
        'a where query'       | { Player.where { name ==~ '%' }.list(max: 10, sort: 'team.players.name', fetch: ['team.players': JoinType.LEFT]) }
        'a criteria query'    | { Player.createCriteria().list(max: 10, sort: 'team.players.name', fetch: ['team.players': JoinType.LEFT]) { } }
        'list'                | { Player.list(max: 10, sort: 'team.players.name', fetch: ['team.players': JoinType.LEFT]) }
    }

    void 'the total of #api sorted by one collection that fetches another counts the instances that the list returns'() {
        given: 'S1 with two players and two reserves, S2 with one player, S3 with neither'
        final s1 = new AssociationJoinTypeSquad(name: 'S1').save(failOnError: true)
        final s2 = new AssociationJoinTypeSquad(name: 'S2').save(failOnError: true)
        new AssociationJoinTypeSquad(name: 'S3').save(failOnError: true)
        new AssociationJoinTypeSquadMember(name: 'P1', squad: s1).save(failOnError: true)
        new AssociationJoinTypeSquadMember(name: 'P2', squad: s1).save(failOnError: true)
        new AssociationJoinTypeSquadMember(name: 'R1', reserveSquad: s1).save(failOnError: true)
        new AssociationJoinTypeSquadMember(name: 'R2', reserveSquad: s1).save(failOnError: true)
        new AssociationJoinTypeSquadMember(name: 'P3', squad: s2).save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final results = query.call()

        then: 'the fetch of the reserves makes the list return each squad with a player once, and the total counts those squads'
        results*.name.sort() == ['S1', 'S2']
        results.totalCount == 2
        results.find { it.name == 'S1' }.reserves*.name.sort() == ['R1', 'R2']

        where:
        api                    | query
        'a criteria query'     | { AssociationJoinTypeSquad.createCriteria().list(max: 10, sort: 'players.name', fetch: [reserves: 'join']) { } }
        'a requested left join' | { AssociationJoinTypeSquad.createCriteria().list(max: 10, sort: 'players.name', fetch: [reserves: JoinType.LEFT]) { } }
        'a where query'        | { AssociationJoinTypeSquad.where { name ==~ 'S%' }.join('reserves').list(max: 10, sort: 'players.name') }
        'list'                 | { AssociationJoinTypeSquad.list(max: 10, sort: 'players.name', fetch: [reserves: 'join']) }
    }

    void 'the total of #api sorted by one collection whose bag fetch it defers counts the rows that its pages go through'() {
        given: 'Full with two members and two trophies, Members with one member, Trophies with one trophy, and Empty with neither'
        final full = new RequestedJoinBagTeam(name: 'Full')
        full.addToMembers(name: 'M1').addToMembers(name: 'M2').addToTrophies(name: 'T1').addToTrophies(name: 'T2')
        full.save(failOnError: true)
        new RequestedJoinBagTeam(name: 'Members').addToMembers(name: 'M3').save(failOnError: true)
        new RequestedJoinBagTeam(name: 'Trophies').addToTrophies(name: 'T3').save(failOnError: true)
        new RequestedJoinBagTeam(name: 'Empty').save(failOnError: true, flush: true)
        manager.session.clear()

        when: 'the pages of two rows that a pager asks for, which stops at the total'
        final first = query.call(0)
        final names = [] as Set
        for (int offset = 0; offset < first.totalCount; offset += 2) {
            names.addAll(query.call(offset)*.name)
        }

        then: 'the bag is loaded after the query, so the rows are those of the sort, a row for each trophy'
        first.totalCount == 3
        names == ['Full', 'Trophies'] as Set
        first.find { it.name == 'Full' }.members*.name.sort() == ['M1', 'M2']

        where:
        api                | query
        'list'             | { int offset -> RequestedJoinBagTeam.list(max: 2, offset: offset, sort: 'trophies.name', fetch: [members: 'join']) }
        'a where query'    | { int offset -> RequestedJoinBagTeam.where { name ==~ '%' }.list(max: 2, offset: offset, sort: 'trophies.name', fetch: [members: 'join']) }
        'a criteria query' | { int offset -> RequestedJoinBagTeam.createCriteria().list(max: 2, offset: offset, sort: 'trophies.name', fetch: [members: 'join']) { } }
    }

    void 'static list fetches a nested collection together with its owner'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        when:
        final results = Player.list(fetch: ['team.players': 'join'], max: 2, sort: 'name', order: 'desc')
        final squad = results.find { it.team.name == 'Squad Team' }.team

        then:
        results.size() == 2
        Hibernate.isInitialized(squad.players)
        squad.players*.name.sort() == ['Squad Player A', 'Squad Player B', 'Squad Player without contract']
    }

    void '#api fetches a complete bag without multiplying its elements by another collection'() {
        given:
        final team = new RequestedJoinBagTeam(name: 'Full team')
        team.addToMembers(name: 'Member A').addToMembers(name: 'Member B')
        team.addToTrophies(name: 'Trophy A').addToTrophies(name: 'Trophy B')
        team.save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final results = query.call()

        then:
        results*.name == ['Full team']
        Hibernate.isInitialized(results.first().members)
        results.first().members*.name.sort() == ['Member A', 'Member B']
        results.first().trophies*.name.sort() == ['Trophy A', 'Trophy B']

        where:
        api        | query
        'criteria' | { RequestedJoinBagTeam.where { members { name == 'Member A' } }.join('members', JoinType.LEFT).join('trophies').list() }
        'list'     | { RequestedJoinBagTeam.list(sort: 'trophies.name', fetch: [members: 'join']) }
    }

    void '#api keeps the restriction on fetching two bags for a path through two bags'() {
        given: 'an owner whose child has two leaves, and an owner without children'
        final owner = new RequestedJoinNestedBagOwner(name: 'Full')
        final child = new RequestedJoinNestedBagChild(name: 'Child')
        child.addToLeaves(name: 'Leaf A').addToLeaves(name: 'Leaf B')
        owner.addToChildren(child)
        owner.save(failOnError: true)
        new RequestedJoinNestedBagOwner(name: 'Empty').save(failOnError: true, flush: true)
        manager.session.clear()

        when: 'both bags on the path are counted, so neither is loaded after the query'
        query.call()

        then: 'Hibernate refuses to fetch two bags together, as for two requested bags of the queried class'
        final IllegalArgumentException e = thrown()
        e.cause instanceof MultipleBagFetchException

        where:
        api              | query
        'list'           | { RequestedJoinNestedBagOwner.list(fetch: ['children.leaves': JoinType.INNER]) }
        'a where query'  | { RequestedJoinNestedBagOwner.where { name ==~ '%' }.join('children.leaves', JoinType.INNER).list() }
        'a left join'    | { RequestedJoinNestedBagOwner.where { name ==~ '%' }.join('children.leaves', JoinType.LEFT).list() }
    }

    void 'deleteAll of an entity with a composite identifier fails with a message for a requested join through a join table'() {
        given:
        new RequestedJoinCompositeItem(code: 'A', region: 'EU').addToTags(name: 'red').save(failOnError: true, flush: true)
        manager.session.clear()

        when: 'the instances are deleted by their identifiers, which a composite identifier is not'
        RequestedJoinCompositeItem.where { code == 'A' }.join('tags', JoinType.INNER).deleteAll()

        then:
        final UnsupportedOperationException e = thrown()
        e.message.contains('composite identifier')
    }

    void 'updateAll of an entity with a composite identifier applies a requested join through a join table'() {
        given:
        new RequestedJoinCompositeItem(code: 'A', region: 'EU').addToTags(name: 'red').save(failOnError: true)
        new RequestedJoinCompositeItem(code: 'A', region: 'US').save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final changed = RequestedJoinCompositeItem.where { code == 'A' }.join('tags', JoinType.INNER).updateAll(label: 'changed')
        manager.session.clear()

        then: 'only the item with a tag is changed'
        changed == 1
        RequestedJoinCompositeItem.list(sort: 'region').collect { it.region + ':' + it.label } == ['EU:changed', 'US:null']
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

    void '#description applies an inner join requested for a collection of a basic type'() {
        given:
        new AssociationJoinTypePost(title: 'Tagged post', tags: ['x', 'y']).save(failOnError: true)
        new AssociationJoinTypePost(title: 'Untagged post').save(failOnError: true, flush: true)
        manager.session.clear()

        expect: 'a fetched collection repeats the post for each element in some of the queries'
        query.call()*.title.unique() == ['Tagged post']

        where:
        description           | query
        'a where query'       | { AssociationJoinTypePost.where { title ==~ '%post' }.join('tags', JoinType.INNER).list() }
        'the fetch argument'  | { AssociationJoinTypePost.where { title ==~ '%post' }.list(fetch: [tags: JoinType.INNER]) }
        'a criteria query'    | { AssociationJoinTypePost.createCriteria().list { like('title', '%post'); join('tags', JoinType.INNER) } }
        'a dynamic finder'    | { AssociationJoinTypePost.findAllByTitleLike('%post', [fetch: [tags: JoinType.INNER]]) }
        'findAll by example'  | { ['Tagged post', 'Untagged post'].collectMany { title -> AssociationJoinTypePost.findAll(new AssociationJoinTypePost(title: title), [fetch: [tags: JoinType.INNER]]) } }
        'list'                | { AssociationJoinTypePost.list(fetch: [tags: JoinType.INNER]) }
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

    void 'deleteAll applies a requested join of basic values before deleting their collection rows'() {
        given:
        new AssociationJoinTypePost(title: 'First tagged post', tags: ['x', 'y']).save(failOnError: true)
        new AssociationJoinTypePost(title: 'First untagged post').save(failOnError: true)
        new AssociationJoinTypePost(title: 'Other post', tags: ['z']).save(failOnError: true, flush: true)
        manager.session.clear()
        final query = AssociationJoinTypePost.where { title ==~ 'First%' }.join('tags', joinType)

        when:
        final listed = query.list()*.title
        final deleted = query.deleteAll()
        manager.session.clear()

        then:
        listed == ['First tagged post']
        deleted == 1
        AssociationJoinTypePost.list(sort: 'title')*.title == ['First untagged post', 'Other post']
        AssociationJoinTypePost.findByTitle('Other post').tags as Set == ['z'] as Set

        where:
        joinType << [JoinType.INNER, JoinType.RIGHT]
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

        and: 'each player is returned once'
        players.size() == 7

        and: 'the team of each player is fetched, with all of its players'
        players.every { Hibernate.isInitialized(it.team) && Hibernate.isInitialized(it.team.players) }
        players.find { it.name == 'Squad Player A' }.team.players.size() == 3

        where:
        api                | query
        'a where query'    | { Player.where { name ==~ '%Player%' }.join('team.players.contract', JoinType.INNER).list() }
        'a criteria query' | { Player.createCriteria().list { like('name', '%Player%'); join('team.players.contract', JoinType.INNER) } }
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

    void 'an order of a criteria query uses the join that criteria on the association make with the join type that a fetch mode replaced: #description'() {
        when:
        final List<Team> results = query.call()

        then: 'the left join of the criteria keeps the teams without a club, and the order sorts the teams with a club by its name'
        results*.name.sort() == expected
        results*.club.findAll()*.name == clubs

        where:
        description                       | query                                                                                                                                                                                       || expected                                                                    | clubs
        'an order after the block'        | { Team.createCriteria().list { join('club', JoinType.LEFT); join('club'); or { club { eq('name', 'Arsenal') }; eq('name', 'Unattached First Team') }; order('club.name') } }                 || ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team'] | ['Arsenal', 'Arsenal']
        'an order with a Query.Order'     | { Team.createCriteria().list(max: 10) { join('club', JoinType.LEFT); select('club'); or { club { eq('name', 'Chelsea') }; eq('name', 'Unattached First Team') }; order(Query.Order.desc('club.name')) } }    || ['Chelsea First Team', 'Unattached First Team']                           | ['Chelsea']
        'an order inside the block'       | { Team.createCriteria().list { join('club', JoinType.LEFT); join('club'); club { order('name', 'desc') } } }                                                                              || ALL_TEAMS                                                                   | ['Manchester United', 'Chelsea', 'Arsenal', 'Arsenal']
        'a paged order inside the block'  | { Team.createCriteria().list(max: 10) { join('club', JoinType.LEFT); select('club'); club { order('name', 'desc') } } }                                                                   || ALL_TEAMS                                                                   | ['Manchester United', 'Chelsea', 'Arsenal', 'Arsenal']
        'an order through an alias'       | { Team.createCriteria().list { join('club', JoinType.LEFT); join('club'); createAlias('club', 'c'); or { eq('c.name', 'Arsenal'); eq('name', 'Unattached First Team') }; order('c.name') } } || ['Arsenal First Team', 'Playerless First Team', 'Unattached First Team'] | ['Arsenal', 'Arsenal']
        'the sort argument'               | { Team.createCriteria().list(max: 10, sort: 'club.name', order: 'desc') { join('club', JoinType.LEFT); select('club'); or { club { eq('name', 'Chelsea') }; eq('name', 'Unattached First Team') } } } || ['Chelsea First Team', 'Unattached First Team'] | ['Chelsea']
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
        query."$method"(fetch: [club: JoinType.LEFT])?.name == 'Unattached First Team'
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

        and: 'the name of the club of each team that has one'
        results.findAll { it != null }.sort() == ['Arsenal', 'Arsenal', 'Chelsea', 'Manchester United']

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
        results*.name.sort() == (TEAMS_WITH_PLAYERS + 'Squad Team').sort()
        count == 5
        paged.totalCount == 5

        and: 'returned once'
        results.size() == 5
        paged.size() == 5

        and: 'the inner join does not leave the player without a contract out of the fetched collection'
        Hibernate.isInitialized(squad.players)
        squad.players*.name.sort() == ['Squad Player A', 'Squad Player B', 'Squad Player without contract']
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

    void '#api applies the inner join that the fetch argument requests to a sort by the association'() {
        when:
        final List<Team> results = query.call()

        then:
        results*.name.sort() == TEAMS_WITH_A_CLUB
        results*.club*.name == ['Arsenal', 'Arsenal', 'Chelsea', 'Manchester United']

        where:
        api                  | query
        'list'               | { Team.list(sort: 'club.name', fetch: [club: JoinType.INNER]) }
        'a paged list'       | { Team.list(sort: 'club.name', max: 10, fetch: [club: JoinType.INNER]) }
    }

    void '#api sorted by an association does not find an instance without it when the fetch argument requests an inner join'() {
        expect:
        query.call() == expected

        where:
        api               | query                                                                                                            || expected
        'findAllWhere'    | { Team.findAllWhere([name: 'Unattached First Team'], [sort: 'club.name', fetch: [club: JoinType.INNER]]) }      || []
        'findWhere'       | { Team.findWhere([name: 'Unattached First Team'], [sort: 'club.name', fetch: [club: JoinType.INNER]]) }         || null
        'find by example' | { Team.find(new Team(name: 'Unattached First Team'), [sort: 'club.name', fetch: [club: JoinType.INNER]]) }     || null
    }

    void '#api applies an inner join that the fetch argument requests for #path without a sort'() {
        given:
        saveTeamWithAPlayerWithoutContract()

        expect: 'each team once, also when the fetched collection holds several players of the team'
        query.call()*.name.sort() == expected

        where:
        api                  | path               | query                                                                                         || expected
        'findAll by example' | 'club'             | { Team.findAll(new Team(name: 'Unattached First Team'), [fetch: [club: JoinType.INNER]]) }   || []
        'list'               | 'club'             | { Team.list(fetch: [club: JoinType.INNER]) }                                                 || TEAMS_WITH_A_CLUB + 'Squad Team'
        'findAllWhere'       | 'players'          | { Team.findAllWhere([name: 'Playerless First Team'], [fetch: [players: JoinType.INNER]]) }   || []
        'list'               | 'players'          | { Team.list(fetch: [players: JoinType.INNER]) }                                              || (TEAMS_WITH_PLAYERS + 'Squad Team').sort()
        'list'               | 'players.contract' | { Team.list(fetch: ['players.contract': JoinType.INNER]) }                                   || (TEAMS_WITH_PLAYERS + 'Squad Team').sort()
    }

    void 'the total count of a paged list counts the instances that an inner join the fetch argument requests leaves'() {
        when:
        final results = Team.list(max: 2, fetch: [club: JoinType.INNER])

        then:
        results.size() == 2
        results.totalCount == TEAMS_WITH_A_CLUB.size()
    }

    void 'a #description of a criteria query that requests a join of a collection counts each team once'() {
        given: 'a team with three players'
        saveTeamWithAPlayerWithoutContract()

        expect: 'as on 8.0.x and with Hibernate 5: a count of a property counts the teams, which a join of the collection would repeat'
        new ArrayList(query.call()) == expected

        where:
        description                                          | query                                                                                                           || expected
        'count of a property with a left join'               | { Team.createCriteria().list { join('players', JoinType.LEFT); projections { count('name') } } }                 || [6L]
        'count of a property with a fetch mode'              | { Team.createCriteria().list { join('players'); projections { count('name') } } }                               || [6L]
        'count of a property with the fetch argument'        | { Team.createCriteria().list(fetch: [players: 'join']) { projections { count('name') } } }                       || [6L]
        'count of a property with an inner join'             | { Team.createCriteria().list { join('players', JoinType.INNER); projections { count('name') } } }                || [5L]
        'count of an association with a left join'           | { Team.createCriteria().list { join('players', JoinType.LEFT); projections { count('club') } } }                 || [5L]
        'distinct count of a property with a left join'      | { Team.createCriteria().list { join('players', JoinType.LEFT); projections { countDistinct('name') } } }         || [6L]
        'row count and count of a property with a left join' | { Team.createCriteria().list { join('players', JoinType.LEFT); projections { rowCount(); count('name') } } }     || [[6L, 6L]]
        'row count with a left join'                         | { Team.createCriteria().list { join('players', JoinType.LEFT); projections { rowCount() } } }                    || [6L]
    }

    void '#api of an association through another association is a fetch mode, and the query returns its players'() {
        expect: 'as with Hibernate 5, and as for an association of the queried class'
        query.call()*.name.sort() == ['Arsenal Player', 'Chelsea Player', 'MU Player', 'Unattached Player']

        where:
        api                                              | query
        'select of a criteria query'                     | { Player.createCriteria().list { select('team.club') } }
        'select of a where query'                        | { Player.where { name ==~ '%Player' }.select('team.club').list() }
        'select in the fetch argument of a where query'  | { Player.where { name ==~ '%Player' }.list(fetch: ['team.club': 'select']) }
        'lazy in the fetch argument of a dynamic finder' | { Player.findAllByNameLike('%Player', [fetch: ['team.club': 'lazy']]) }
    }

    void 'list rejects a fetch mode for #path, as without an association before it on the path'() {
        when:
        Player.list(fetch: [(path): 'join'])

        then:
        thrown(IllegalArgumentException)

        where:
        path << ['team.bogus', 'team.name', 'bogus']
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
class RequestedJoinBagTeam {
    String name
    Collection<RequestedJoinBagMember> members
    static hasMany = [members: RequestedJoinBagMember, trophies: RequestedJoinBagTrophy]
}

@Entity
class RequestedJoinNestedBagOwner {
    String name
    Collection<RequestedJoinNestedBagChild> children
    static hasMany = [children: RequestedJoinNestedBagChild]
}

@Entity
class RequestedJoinNestedBagChild {
    String name
    Collection<RequestedJoinNestedBagLeaf> leaves
    static belongsTo = [owner: RequestedJoinNestedBagOwner]
    static hasMany = [leaves: RequestedJoinNestedBagLeaf]
}

@Entity
class RequestedJoinNestedBagLeaf {
    String name
    static belongsTo = [child: RequestedJoinNestedBagChild]
}

@Entity
class RequestedJoinCompositeItem implements Serializable {
    String code
    String region
    String label
    static hasMany = [tags: RequestedJoinCompositeTag]

    static mapping = {
        id composite: ['code', 'region']
    }

    static constraints = {
        label nullable: true
    }
}

@Entity
class RequestedJoinCompositeTag {
    String name
}

@Entity
class RequestedJoinBagMember {
    String name
    static belongsTo = [team: RequestedJoinBagTeam]
}

@Entity
class RequestedJoinBagTrophy {
    String name
    static belongsTo = [team: RequestedJoinBagTeam]
}

@Entity
class AssociationJoinTypeCategory {
    String name
    AssociationJoinTypeCategory parent

    static constraints = {
        parent nullable: true
    }
}

@Entity
class RequestedJoinSortedTeam {
    String name
    Club club

    static mapping = {
        sort 'name'
    }
}
