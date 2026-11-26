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
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.query.Query
import org.hibernate.FetchMode
import org.hibernate.Hibernate
import org.hibernate.criterion.Order
import spock.lang.IgnoreIf
import spock.lang.Requires

import jakarta.persistence.criteria.JoinType

/**
 * A sort by a property of an association must not leave out the instances whose association is null,
 * and must not change what the query fetches.
 */
class AssociationSortSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    /**
     * Whether the specification runs with the {@code grails.gorm.leftJoinAssociationSort} setting, with which
     * a sort by a property of an association joins the association with a left join
     */
    protected boolean isLeftJoinSort() {
        false
    }

    void setupSpec() {
        manager.configuration['grails.gorm.leftJoinAssociationSort'] = leftJoinSort
        manager.domainClasses.addAll([Club, Team, Player, Contract, AssociationSortLeague, AssociationSortEmployee, AssociationSortDepartment,
                                      AssociationSortSquad, AssociationSortMember, AssociationSortBagSquad, AssociationSortBagMember,
                                      AssociationSortMixedSquad, AssociationSortMixedMember,
                                      AssociationSortAuthor, AssociationSortBook, AssociationSortPublisher, AssociationSortAward, AssociationSortCategory,
                                      AssociationSortMappedAuthor, AssociationSortMappedBook, AssociationSortEagerAuthor, AssociationSortEagerBook,
                                      AssociationSortMappedPublisher, AssociationSortMappedAward, AssociationSortMappedNode, AssociationSortMappedLeaf,
                                      AssociationSortVenue, AssociationSortGround, AssociationSortMappedSquad, AssociationSortMappedMember,
                                      AssociationSortMappedSetSquad, AssociationSortMappedSetMember])
    }

    void setup() {
        final manUtd = new Club(name: 'Manchester United').save()
        final arsenal = new Club(name: 'Arsenal').save()
        final chelsea = new Club(name: 'Chelsea').save()
        savePlayer('MU Player', new Team(club: manUtd, name: 'MU First Team').save())
        savePlayer('Arsenal Player', new Team(club: arsenal, name: 'Arsenal First Team').save())
        savePlayer('Chelsea Player', new Team(club: chelsea, name: 'Chelsea First Team').save())
        savePlayer('Unattached Player', new Team(name: 'Unattached First Team').save(failOnError: true))
    }

    protected static void savePlayer(String name, Team team) {
        final player = new Player(name: name, team: team)
        player.contract = new Contract(salary: 1000G, player: player)
        player.save(failOnError: true, flush: true)
    }

    protected void saveSquad() {
        final squad = new Team(club: Club.findByName('Arsenal'), name: 'Squad').save(failOnError: true)
        ['Squad Player A', 'Squad Player B', 'Squad Player C'].each { savePlayer(it, squad) }
        manager.session.clear()
    }

    @IgnoreIf({ instance.leftJoinSort })
    void '#api sorted by an association property leaves out a team without a club'() {
        expect: 'an inner join for the sort, since the leftJoinAssociationSort setting is not enabled'
        query.call() == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']

        where:
        api                        | query
        'list'                     | { Team.list(sort: 'club.name')*.name }
        'findAll by example'       | { Team.findAll(new Team(), [sort: 'club.name'])*.name }
        'a dynamic finder'         | { Team.findAllByNameLike('%First%', [sort: 'club.name'])*.name }
        'a where query'            | { Team.where { name ==~ '%First%' }.list(sort: 'club.name')*.name }
        'a where query with sort'  | { Team.where { name ==~ '%First%' }.sort('club.name').list()*.name }
        'a detached criteria'      | { new DetachedCriteria(Team).build { like('name', '%First%') }.list(sort: 'club.name')*.name }
        'a paged criteria query'   | { Team.createCriteria().list(sort: 'club.name', max: 10) { like('name', '%First%') }*.name }
    }

    @IgnoreIf({ instance.leftJoinSort })
    void 'the counts of a query that is sorted by an association property leave out a team without a club'() {
        expect:
        Team.list(sort: 'club.name', max: 2).totalCount == 3
        Team.where { name ==~ '%First%' }.list(sort: 'club.name', max: 2).totalCount == 3
        Team.where { name ==~ '%First%' }.sort('club.name').count() == 3
    }

    @Requires({ instance.leftJoinSort })
    void 'list sorted by #sort keeps a team without a club'() {
        when:
        final results = Team.list(sort: sort)

        then:
        'Unattached First Team' in results*.name

        and: 'the other teams are sorted by their club'
        results*.name - 'Unattached First Team' == expected

        where:
        sort        || expected
        'club.name' || ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'club.id'   || ['MU First Team', 'Arsenal First Team', 'Chelsea First Team']
    }

    @Requires({ instance.leftJoinSort })
    void 'list sorted by a nested association property keeps a player whose team has no club'() {
        when:
        final results = Player.list(sort: 'team.club.name', order: 'desc')

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void '#api sorted by the identifier of a nested association applies the inner join that the query requests for the association'() {
        when:
        final List<Player> results = query.call()

        then: 'the player whose team has no club is left out, and the others are in the order of the identifiers of their clubs'
        results*.name == ['MU Player', 'Arsenal Player', 'Chelsea Player']

        where:
        api                       | query
        'a where query with sort' | { Player.where { name ==~ '%Player%' }.sort('team.club.id').list(fetch: ['team.club': JoinType.INNER]) }
        'a where query'           | { Player.where { name ==~ '%Player%' }.list(sort: 'team.club.id', fetch: ['team.club': JoinType.INNER]) }
        'a detached criteria'     | { new DetachedCriteria(Player).like('name', '%Player%').join('team.club', JoinType.INNER).list(sort: 'team.club.id') }
        'a dynamic finder'        | { Player.findAllByNameLike('%Player%', [sort: 'team.club.id', fetch: ['team.club': JoinType.INNER]]) }
    }

    void 'the total of a paged where query sorted by the identifier of a nested association applies the inner join that it requests'() {
        when:
        final results = Player.where { name ==~ '%Player%' }.list(sort: 'team.club.id', max: 2, fetch: ['team.club': JoinType.INNER])

        then:
        results*.name == ['MU Player', 'Arsenal Player']
        results.totalCount == 3
    }

    void 'list sorted by a map sorts by every entry of the map before paging'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = Team.list(sort: ['club.name': 'asc', name: 'desc'])

        then:
        results*.name - 'Unattached First Team' == ['Arsenal Second Team', 'Arsenal First Team', 'Chelsea First Team', 'MU First Team']

        and:
        Team.list(sort: ['club.name': 'asc', name: 'desc'], max: 2, offset: 1)*.name ==
                (leftJoinSort ? ['Arsenal Second Team', 'Arsenal First Team'] : ['Arsenal First Team', 'Chelsea First Team'])
    }

    @Requires({ instance.leftJoinSort })
    void 'a paged list sorted by an association property counts a team without a club'() {
        when:
        final results = Team.list(sort: 'club.name', max: 2)

        then:
        results.size() == 2
        results.totalCount == 4
    }

    @Requires({ instance.leftJoinSort })
    void 'findAll by example sorted by an association property keeps a team without a club'() {
        when:
        final results = Team.findAll(new Team(), [sort: 'club.name'])

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
    }

    @Requires({ instance.leftJoinSort })
    void 'findAllWhere sorted by an association property finds a team without a club'() {
        expect:
        Team.findAllWhere([name: 'Unattached First Team'], [sort: 'club.name'])*.name == ['Unattached First Team']
    }

    @Requires({ instance.leftJoinSort })
    void 'findWhere sorted by an association property finds a team without a club'() {
        expect:
        Team.findWhere([name: 'Unattached First Team'], [sort: 'club.name'])?.name == 'Unattached First Team'
    }

    @Requires({ instance.leftJoinSort })
    void 'a criteria query with pagination arguments sorted by an association property keeps a team without a club'() {
        when:
        final results = Team.createCriteria().list(sort: 'club.name', max: 10) {
            like('name', '%First%')
        }

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        results.totalCount == 4
    }

    @Requires({ instance.leftJoinSort })
    void 'a dynamic finder sorted by an association property keeps a team without a club'() {
        when:
        final results = Team.findAllByNameLike('%First%', [sort: 'club.name'])

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
    }

    void '#api sorted by two properties of an association joins the association once'() {
        when:
        final List<Team> results = query.call()

        then:
        results*.name - 'Unattached First Team' == ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']

        where:
        api                  | query
        'list'               | { Team.list(sort: ['club.name': 'desc', 'club.id': 'asc']) }
        'findAll by example' | { Team.findAll(new Team(), [sort: ['club.name': 'desc', 'club.id': 'asc']]) }
    }

    void 'findAllWhere and findWhere can be sorted by two properties of an association'() {
        expect:
        Team.findAllWhere([name: 'Arsenal First Team'], [sort: ['club.name': 'asc', 'club.id': 'desc']])*.name == ['Arsenal First Team']
        Team.findWhere([name: 'Arsenal First Team'], [sort: ['club.name': 'asc', 'club.id': 'desc']])?.name == 'Arsenal First Team'
    }

    void 'findAll by example can be sorted by two paths through the same association'() {
        when:
        final results = Player.findAll(new Player(), [sort: ['team.club.name': 'desc', 'team.name': 'asc']])

        then:
        results*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void 'list sorted by two properties of a collection returns a team once for each of its players'() {
        given:
        saveSquad()

        when:
        final results = Team.list(sort: ['players.name': 'asc', 'players.id': 'asc'])

        then:
        results.count { it.name == 'Squad' } == 3
    }

    protected void saveEmployees() {
        final departmentA = new AssociationSortDepartment(name: 'A dept').save(failOnError: true)
        final departmentB = new AssociationSortDepartment(name: 'B dept').save(failOnError: true)
        final departmentX = new AssociationSortDepartment(name: 'X dept').save(failOnError: true)
        final departmentY = new AssociationSortDepartment(name: 'Y dept').save(failOnError: true)
        final managerOne = new AssociationSortEmployee(name: 'm1', department: departmentB).save(failOnError: true)
        final managerTwo = new AssociationSortEmployee(name: 'm2', department: departmentA).save(failOnError: true)
        departmentX.manager = managerTwo
        departmentY.manager = managerOne
        new AssociationSortEmployee(name: 'e1', manager: managerOne, department: departmentX).save(failOnError: true)
        new AssociationSortEmployee(name: 'e2', manager: managerTwo, department: departmentY).save(failOnError: true, flush: true)
        manager.session.clear()
    }

    /**
     * A sort by a property of a collection, in a query that fetches another collection of the same class:
     *
     * <pre>
     * | sorted by           | fetched           | result                                                          |
     * |---------------------|-------------------|-----------------------------------------------------------------|
     * | a set               | nothing           | every squad, also one whose set is empty                        |
     * | a set               | another set       | every squad, and the fetched set holds its own elements once    |
     * | a set               | a list            | every squad, and the fetched list holds its own elements once   |
     * | a list              | a set             | every squad, and the fetched set holds its own elements once    |
     * | a set               | a bag             | UnsupportedOperationException: the rows repeat, the bag cannot tell |
     * | a bag               | nothing           | every squad, also one whose bag is empty                        |
     * | a bag               | a set             | UnsupportedOperationException: the rows repeat, the bag cannot tell |
     * | a bag               | another bag       | UnsupportedOperationException: the rows repeat, the bag cannot tell |
     * </pre>
     */
    /**
     * A sort by a property of a collection that the mapping fetches with {@code fetch: 'join'}, in a query
     * that fetches another collection of the same class:
     *
     * <pre>
     * | sorted by | fetched     | a query                                              | a list                         |
     * |-----------|-------------|------------------------------------------------------|--------------------------------|
     * | a bag     | a set       | the collections of the same query without the sort   | the same                       |
     * | a bag     | a list      | the collections of the same query without the sort   | the same                       |
     * | a bag     | another bag | UnsupportedOperationException                        | UnsupportedOperationException  |
     * | a set     | a bag       | the collections of the same query without the sort   | UnsupportedOperationException  |
     * </pre>
     *
     * A query fetches the collection that the mapping fetches anyway, so the sort makes no join of its own and
     * leaves the rows of the query as they are. A list does not fetch what the mapping fetches, so its sort
     * joins the collection, and the rows of that join would repeat the elements of a bag that it fetches.
     */
    protected void saveMappedSquads(Class squadClass) {
        final full = squadClass.newInstance(name: 'Full squad')
        ['Regular B', 'Regular C'].each { full.addToRegulars(name: it) }
        ['Substitute A', 'Substitute B'].each { full.addToSubstitutes(name: it) }
        final small = squadClass.newInstance(name: 'Small squad')
        small.addToRegulars(name: 'Regular A')
        small.addToSubstitutes(name: 'Substitute C')
        if (squadClass == AssociationSortMappedSquad) {
            ['Reserve A', 'Reserve B'].each { full.addToReserves(name: it) }
            ['Lineup A', 'Lineup B'].each { full.addToLineup(name: it) }
            small.addToReserves(name: 'Reserve C')
            small.addToLineup(name: 'Lineup C')
        }
        full.save(failOnError: true)
        small.save(failOnError: true, flush: true)
        manager.session.clear()
    }

    protected static Map<String, List<List<String>>> mappedSquadContents(List squads, String fetched) {
        squads.collectEntries { [(it.name): [it.regulars*.name.sort(), it."$fetched"*.name.sort()]] }
    }

    private static final Map<String, Closure<List>> MAPPED_FETCH_QUERIES = [
            'a detached criteria'      : { Class squadClass, String sort, String fetched ->
                final query = new DetachedCriteria(squadClass).like('name', '%squad').join(fetched)
                sort ? query.list(sort: sort) : query.list()
            },
            'order in a criteria query': { Class squadClass, String sort, String fetched ->
                squadClass.createCriteria().list {
                    like('name', '%squad')
                    join(fetched)
                    if (sort) {
                        order(sort)
                    }
                }
            },
            'a paged criteria query'   : { Class squadClass, String sort, String fetched ->
                squadClass.createCriteria().list(mappedSquadArguments(sort, fetched) + [max: 10]) { like('name', '%squad') }
            },
            'a dynamic finder'         : { Class squadClass, String sort, String fetched ->
                squadClass.findAllByNameLike('%squad', mappedSquadArguments(sort, fetched))
            },
            'findAll by example'       : { Class squadClass, String sort, String fetched ->
                squadClass.findAll(squadClass.newInstance(), mappedSquadArguments(sort, fetched))
            },
            'a list'                   : { Class squadClass, String sort, String fetched ->
                squadClass.list(mappedSquadArguments(sort, fetched))
            },
    ]

    protected static Map mappedSquadArguments(String sort, String fetched) {
        sort ? [sort: sort, fetch: [(fetched): 'join']] : [fetch: [(fetched): 'join']]
    }

    private static final Map<String, List<String>> FETCHED_ELEMENTS = [
            regulars   : ['Regular A', 'Regular B'],
            substitutes: ['Substitute A', 'Substitute B'],
            reserves   : ['Reserve A', 'Reserve B'],
            benched    : ['Benched A', 'Benched B'],
            lineup     : ['Lineup A', 'Lineup B'],
    ]

    private static final Map<String, Closure<List<AssociationSortMixedSquad>>> MIXED_QUERIES = [
            'a where query'            : { String sort, String fetched ->
                final query = AssociationSortMixedSquad.where { name ==~ '%squad' }
                if (fetched != 'nothing') {
                    query.join(fetched)
                }
                query.list(sort: sort)
            },
            'order in a criteria query': { String sort, String fetched ->
                AssociationSortMixedSquad.createCriteria().list {
                    like('name', '%squad')
                    if (fetched != 'nothing') {
                        join(fetched)
                    }
                    order(sort)
                }
            },
            'a list'                   : { String sort, String fetched ->
                fetched == 'nothing' ? AssociationSortMixedSquad.list(sort: sort) : AssociationSortMixedSquad.list(sort: sort, fetch: [(fetched): 'join'])
            },
    ]

    void 'the total of #api sorted by a collection counts the rows that it pages through'() {
        given:
        saveSquadsToPage()

        when:
        final firstPage = query.call(0)
        final secondPage = query.call(2)

        then:
        firstPage*.name == firstPageNames
        firstPage.totalCount == 3
        secondPage*.name == ['Second squad']

        where: 'a where query returns an instance once from the rows of a page'
        api                      | query                                                                                                                                  || firstPageNames
        'a list'                 | { int offset -> AssociationSortSquad.list(sort: 'players.name', max: 2, offset: offset) }                                              || ['First squad', 'First squad']
        'a where query'          | { int offset -> AssociationSortSquad.where { name ==~ '%squad' }.list(sort: 'players.name', max: 2, offset: offset) }                  || ['First squad']
        'a paged criteria query' | { int offset -> AssociationSortSquad.createCriteria().list(sort: 'players.name', max: 2, offset: offset) { like('name', '%squad') } } || ['First squad', 'First squad']
    }

    protected void saveSquadsToPage() {
        final first = new AssociationSortSquad(name: 'First squad').save(failOnError: true)
        new AssociationSortMember(name: 'A', squad: first).save(failOnError: true)
        new AssociationSortMember(name: 'B', squad: first).save(failOnError: true)
        new AssociationSortMember(name: 'Reserve', reserveSquad: first).save(failOnError: true)
        final second = new AssociationSortSquad(name: 'Second squad').save(failOnError: true)
        new AssociationSortMember(name: 'C', squad: second).save(failOnError: true, flush: true)
        manager.session.clear()
    }

    void 'the total of a list sorted through an association that it fetches counts the rows that it pages through: #fetch'() {
        given: 'a team with three players and four teams with one player, so 13 rows'
        saveSquad()

        when:
        final unfetched = Player.list(max: 2, sort: 'team.players.name')
        final fetched = Player.list(max: 2, sort: 'team.players.name', fetch: [team: fetch])

        then:
        unfetched.totalCount == 13
        fetched.totalCount == 13

        where:
        fetch << ['join', JoinType.LEFT, JoinType.INNER]
    }

    void '#api that fetches a bag through an association holds each element once when no collection is sorted'() {
        given:
        saveAuthorAndCategories()

        when:
        final results = query.call()

        then:
        results.first().publisher.awards*.title.sort() == ['W1', 'W2']

        where:
        api                                 | query
        'a where query'                     | { AssociationSortAuthor.where { name ==~ '%' }.list(fetch: ['publisher.awards': JoinType.LEFT]) }
        'a where query sorted by a property' | { AssociationSortAuthor.where { name ==~ '%' }.list(sort: 'name', fetch: ['publisher.awards': JoinType.LEFT]) }
        'a list'                            | { AssociationSortAuthor.list(fetch: ['publisher.awards': JoinType.LEFT]) }
    }

    void 'a bag that a mapping fetches below the root holds each element once #description'() {
        given:
        saveMappedAuthors()

        when:
        final results = query.call()

        then:
        results*.name.unique() == ['Eager']
        results.first().publisher.awards*.title.sort() == ['W1', 'W2']

        where:
        description                       | query
        'without a sort by a collection'  | { AssociationSortEagerAuthor.where { name ==~ '%' }.list() }
        'when the query asks not to fetch' | { AssociationSortEagerAuthor.where { name ==~ '%' }.list(sort: 'books.title', fetch: ['publisher.awards': 'select']) }
    }

    void 'a list sorted by a collection is not rejected for what a mapping fetches, since a list does not join it'() {
        given:
        saveMappedAuthors()

        expect:
        AssociationSortEagerAuthor.list(sort: 'books.title')*.name.unique() == ['Eager']
    }

    protected void saveMappedAuthors() {
        final publisher = new AssociationSortMappedPublisher(name: 'Publisher').save(failOnError: true)
        ['W1', 'W2'].each { new AssociationSortMappedAward(title: it, publisher: publisher).save(failOnError: true) }
        final author = new AssociationSortMappedAuthor(name: 'Mapped', publisher: publisher).save(failOnError: true)
        ['B1', 'B2'].each { new AssociationSortMappedBook(title: it, author: author).save(failOnError: true) }
        final eager = new AssociationSortEagerAuthor(name: 'Eager', publisher: publisher).save(failOnError: true)
        ['B1', 'B2'].each { new AssociationSortEagerBook(title: it, author: eager).save(failOnError: true, flush: true) }
        manager.session.clear()
    }

    protected void saveAuthorAndCategories() {
        final publisher = new AssociationSortPublisher(name: 'Publisher').save(failOnError: true)
        ['W1', 'W2'].each { new AssociationSortAward(title: it, publisher: publisher).save(failOnError: true) }
        final author = new AssociationSortAuthor(name: 'Author', publisher: publisher).save(failOnError: true)
        ['B1', 'B2'].each { new AssociationSortBook(title: it, author: author).save(failOnError: true) }
        final top = new AssociationSortCategory(name: 'Top').save(failOnError: true)
        final left = new AssociationSortCategory(name: 'Left', parent: top).save(failOnError: true)
        new AssociationSortCategory(name: 'Right', parent: top).save(failOnError: true)
        ['G1', 'G2'].each { new AssociationSortCategory(name: it, parent: left).save(failOnError: true, flush: true) }
        manager.session.clear()
    }

    protected void saveSquadWithASubstituteOnly() {
        final squad = new AssociationSortBagSquad(name: 'Squad').save(failOnError: true)
        new AssociationSortBagMember(name: 'Substitute', substituteSquad: squad).save(failOnError: true, flush: true)
        manager.session.clear()
    }

    void 'a projection of a property of an embedded component of an association uses the join of the association'() {
        given:
        final ground = new AssociationSortGround(name: 'Ground', address: new AssociationSortAddress(city: 'London')).save(failOnError: true)
        new AssociationSortVenue(name: 'Venue', ground: ground).save(failOnError: true)
        new AssociationSortVenue(name: 'Venue without a ground').save(failOnError: true, flush: true)
        manager.session.clear()

        expect:
        AssociationSortVenue.where { name ==~ 'Venue%' }.property('ground.address.city').list() as Set == ['London', null] as Set

        and: 'the sort leaves out the venue without a ground, unless the leftJoinAssociationSort setting is enabled'
        AssociationSortVenue.where { name ==~ 'Venue%' }.list(sort: 'ground.address.city')*.name as Set ==
                (leftJoinSort ? ['Venue', 'Venue without a ground'] : ['Venue']) as Set
    }

    void 'a criteria query ordered by the identifier of an association can be distinct on it or group by it'() {
        given:
        final clubIds = [Club.findByName('Manchester United'), Club.findByName('Arsenal'), Club.findByName('Chelsea')]*.id

        when:
        final distinct = Team.createCriteria().list {
            isNotNull('club')
            projections {
                distinct('club.id')
            }
            order('club.id')
        }
        final grouped = Team.createCriteria().list {
            isNotNull('club')
            projections {
                groupProperty('club.id')
                rowCount()
            }
            order('club.id', 'desc')
        }

        then: 'the order reads the foreign key, as the projection does'
        distinct == clubIds.sort()
        grouped.collect { it[0] } == clubIds.sort().reverse()
    }

    void 'a criteria query ordered by the identifier of an association keeps a team without a club'() {
        when:
        final results = Team.createCriteria().list {
            order('club.id')
        }

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['MU First Team', 'Arsenal First Team', 'Chelsea First Team']
    }

    void 'list sorts by every property of the default sort of the mapping'() {
        given:
        [['Second', 'B'], ['First', 'B'], ['First', 'A'], ['Second', 'C']].each { String league, String name ->
            new AssociationSortLeague(league: league, name: name).save(failOnError: true)
        }
        manager.session.flush()

        expect:
        AssociationSortLeague.list().collect { it.league + ' ' + it.name } == ['First B', 'First A', 'Second C', 'Second B']
    }

    void '#api sorted by a property of a collection does not fetch the collection'() {
        given:
        saveSquad()

        when:
        final Team team = query.call()

        then: 'a row limit does not leave players out of the collection'
        team.name == 'Squad'
        !Hibernate.isInitialized(team.players)
        team.players.size() == 3

        where:
        api                                | query
        'a dynamic finder with a limit'    | { Team.findAllByName('Squad', [sort: 'players.name', max: 2]).first() }
        'a dynamic finder for one result'  | { Team.findByName('Squad', [sort: 'players.name']) }
        'findAll by example with a limit'  | { Team.findAll(new Team(name: 'Squad'), [sort: 'players.name', max: 2]).first() }
        'find by example'                  | { Team.find(new Team(name: 'Squad'), [sort: 'players.name']) }
        'findAllWhere with a limit'        | { Team.findAllWhere([name: 'Squad'], [sort: 'players.name', max: 2]).first() }
        'findWhere'                        | { Team.findWhere([name: 'Squad'], [sort: 'players.name']) }
        'a criteria query with pagination' | { Team.createCriteria().list(sort: 'players.name', max: 2) { eq('name', 'Squad') }.first() }
    }
}

@Entity
class AssociationSortEmployee {
    String name
    AssociationSortEmployee manager
    AssociationSortDepartment department

    static constraints = {
        manager nullable: true
        department nullable: true
    }
}

@Entity
class AssociationSortDepartment {
    String name
    AssociationSortEmployee manager

    static constraints = {
        manager nullable: true
    }
}

@Entity
class AssociationSortSquad {
    String name
    static hasMany = [players: AssociationSortMember, reserves: AssociationSortMember]
    static mappedBy = [players: 'squad', reserves: 'reserveSquad']
}

@Entity
class AssociationSortMember {
    String name
    AssociationSortSquad squad
    AssociationSortSquad reserveSquad

    static constraints = {
        squad nullable: true
        reserveSquad nullable: true
    }
}

@Entity
class AssociationSortBagSquad {
    String name
    Collection<AssociationSortBagMember> regulars
    Collection<AssociationSortBagMember> substitutes
    static hasMany = [regulars: AssociationSortBagMember, substitutes: AssociationSortBagMember]
    static mappedBy = [regulars: 'squad', substitutes: 'substituteSquad']
}

@Entity
class AssociationSortBagMember {
    String name
    AssociationSortBagSquad squad
    AssociationSortBagSquad substituteSquad

    static constraints = {
        squad nullable: true
        substituteSquad nullable: true
    }
}

@Entity
class AssociationSortMappedPublisher {
    String name
    Collection<AssociationSortMappedAward> awards
    static hasMany = [awards: AssociationSortMappedAward]
    static mapping = {
        awards fetch: 'join'
    }
}

@Entity
class AssociationSortMappedAward {
    String title
    static belongsTo = [publisher: AssociationSortMappedPublisher]
}

@Entity
class AssociationSortMappedAuthor {
    String name
    AssociationSortMappedPublisher publisher
    static hasMany = [books: AssociationSortMappedBook]
}

@Entity
class AssociationSortMappedBook {
    String title
    static belongsTo = [author: AssociationSortMappedAuthor]
}

@Entity
class AssociationSortEagerAuthor {
    String name
    AssociationSortMappedPublisher publisher
    static hasMany = [books: AssociationSortEagerBook]
    static mapping = {
        publisher fetch: 'join'
    }
}

@Entity
class AssociationSortEagerBook {
    String title
    static belongsTo = [author: AssociationSortEagerAuthor]
}

@Entity
class AssociationSortMappedNode {
    String name
    AssociationSortMappedNode next
    static hasMany = [leaves: AssociationSortMappedLeaf]

    static constraints = {
        next nullable: true
    }
    static mapping = {
        next fetch: 'join'
    }
}

@Entity
class AssociationSortMappedLeaf {
    String name
    static belongsTo = [node: AssociationSortMappedNode]
}

@Entity
class AssociationSortAuthor {
    String name
    AssociationSortPublisher publisher
    static hasMany = [books: AssociationSortBook]

    static constraints = {
        publisher nullable: true
    }
}

@Entity
class AssociationSortBook {
    String title
    static belongsTo = [author: AssociationSortAuthor]
}

@Entity
class AssociationSortPublisher {
    String name
    Collection<AssociationSortAward> awards
    static hasMany = [awards: AssociationSortAward]
}

@Entity
class AssociationSortAward {
    String title
    static belongsTo = [publisher: AssociationSortPublisher]
}

@Entity
class AssociationSortCategory {
    String name
    AssociationSortCategory parent
    Collection<AssociationSortCategory> children
    static hasMany = [children: AssociationSortCategory]
    static mappedBy = [children: 'parent']

    static constraints = {
        parent nullable: true
    }
}

@Entity
class AssociationSortMixedSquad {
    String name
    Collection<AssociationSortMixedMember> regulars
    Collection<AssociationSortMixedMember> substitutes
    List<AssociationSortMixedMember> lineup
    AssociationSortLeague league
    static hasMany = [regulars: AssociationSortMixedMember, substitutes: AssociationSortMixedMember, reserves: AssociationSortMixedMember,
                      benched: AssociationSortMixedMember, lineup: AssociationSortMixedMember]
    static mappedBy = [regulars: 'squad', substitutes: 'substituteSquad', reserves: 'reserveSquad', benched: 'benchSquad', lineup: 'lineupSquad']

    static constraints = {
        league nullable: true
    }
}

@Entity
class AssociationSortMixedMember {
    String name
    AssociationSortMixedSquad squad
    AssociationSortMixedSquad substituteSquad
    AssociationSortMixedSquad reserveSquad
    AssociationSortMixedSquad benchSquad
    AssociationSortMixedSquad lineupSquad

    static constraints = {
        squad nullable: true
        substituteSquad nullable: true
        reserveSquad nullable: true
        benchSquad nullable: true
        lineupSquad nullable: true
    }
}

@Entity
class AssociationSortVenue {
    String name
    AssociationSortGround ground

    static constraints = {
        ground nullable: true
    }
}

@Entity
class AssociationSortGround {
    String name
    AssociationSortAddress address
    static embedded = ['address']
}

class AssociationSortAddress {
    String city
}

@Entity
class AssociationSortLeague {
    String league
    String name

    static mapping = {
        sort league: 'asc', name: 'desc'
    }
}

@Entity
class AssociationSortMappedSquad {
    String name
    Collection<AssociationSortMappedMember> regulars
    Collection<AssociationSortMappedMember> substitutes
    List<AssociationSortMappedMember> lineup
    static hasMany = [regulars: AssociationSortMappedMember, substitutes: AssociationSortMappedMember, reserves: AssociationSortMappedMember,
                      lineup: AssociationSortMappedMember]
    static mappedBy = [regulars: 'squad', substitutes: 'substituteSquad', reserves: 'reserveSquad', lineup: 'lineupSquad']

    static mapping = {
        regulars fetch: 'join'
    }
}

@Entity
class AssociationSortMappedMember {
    String name
    AssociationSortMappedSquad squad
    AssociationSortMappedSquad substituteSquad
    AssociationSortMappedSquad reserveSquad
    AssociationSortMappedSquad lineupSquad

    static constraints = {
        squad nullable: true
        substituteSquad nullable: true
        reserveSquad nullable: true
        lineupSquad nullable: true
    }
}

@Entity
class AssociationSortMappedSetSquad {
    String name
    Collection<AssociationSortMappedSetMember> substitutes
    static hasMany = [regulars: AssociationSortMappedSetMember, substitutes: AssociationSortMappedSetMember]
    static mappedBy = [regulars: 'squad', substitutes: 'substituteSquad']

    static mapping = {
        regulars fetch: 'join'
    }
}

@Entity
class AssociationSortMappedSetMember {
    String name
    AssociationSortMappedSetSquad squad
    AssociationSortMappedSetSquad substituteSquad

    static constraints = {
        squad nullable: true
        substituteSquad nullable: true
    }
}
