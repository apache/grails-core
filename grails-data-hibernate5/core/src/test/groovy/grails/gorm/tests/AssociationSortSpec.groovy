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

import jakarta.persistence.criteria.JoinType

/**
 * A sort by a property of an association must not leave out the instances whose association is null,
 * and must not change what the query fetches.
 */
class AssociationSortSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
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

    private static void savePlayer(String name, Team team) {
        final player = new Player(name: name, team: team)
        player.contract = new Contract(salary: 1000G, player: player)
        player.save(failOnError: true, flush: true)
    }

    private void saveSquad() {
        final squad = new Team(club: Club.findByName('Arsenal'), name: 'Squad').save(failOnError: true)
        ['Squad Player A', 'Squad Player B', 'Squad Player C'].each { savePlayer(it, squad) }
        manager.session.clear()
    }

    void 'list sorted by a map sorts by every entry of the map before paging'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = Team.list(sort: ['club.name': 'asc', name: 'desc'])

        then:
        results*.name - 'Unattached First Team' == ['Arsenal Second Team', 'Arsenal First Team', 'Chelsea First Team', 'MU First Team']

        and:
        Team.list(sort: ['club.name': 'asc', name: 'desc'], max: 2, offset: 1)*.name == ['Arsenal First Team', 'Chelsea First Team']
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

    private void saveEmployees() {
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
    private void saveMappedSquads(Class squadClass) {
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

    private static Map<String, List<List<String>>> mappedSquadContents(List squads, String fetched) {
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

    private static Map mappedSquadArguments(String sort, String fetched) {
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

    private void saveSquadsToPage() {
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

    private void saveMappedAuthors() {
        final publisher = new AssociationSortMappedPublisher(name: 'Publisher').save(failOnError: true)
        ['W1', 'W2'].each { new AssociationSortMappedAward(title: it, publisher: publisher).save(failOnError: true) }
        final author = new AssociationSortMappedAuthor(name: 'Mapped', publisher: publisher).save(failOnError: true)
        ['B1', 'B2'].each { new AssociationSortMappedBook(title: it, author: author).save(failOnError: true) }
        final eager = new AssociationSortEagerAuthor(name: 'Eager', publisher: publisher).save(failOnError: true)
        ['B1', 'B2'].each { new AssociationSortEagerBook(title: it, author: eager).save(failOnError: true, flush: true) }
        manager.session.clear()
    }

    private void saveAuthorAndCategories() {
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

    private void saveSquadWithASubstituteOnly() {
        final squad = new AssociationSortBagSquad(name: 'Squad').save(failOnError: true)
        new AssociationSortBagMember(name: 'Substitute', substituteSquad: squad).save(failOnError: true, flush: true)
        manager.session.clear()
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
