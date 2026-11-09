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
                                      AssociationSortVenue, AssociationSortGround])
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

    void 'list sorted by a nested association property keeps a player whose team has no club'() {
        when:
        final results = Player.list(sort: 'team.club.name', order: 'desc')

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void 'list sorted by a map sorts by every entry of the map'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = Team.list(sort: ['club.name': 'asc', name: 'desc'])

        then:
        results*.name - 'Unattached First Team' == ['Arsenal Second Team', 'Arsenal First Team', 'Chelsea First Team', 'MU First Team']
    }

    void 'a paged list sorted by an association property counts a team without a club'() {
        when:
        final results = Team.list(sort: 'club.name', max: 2)

        then:
        results.size() == 2
        results.totalCount == 4
    }

    void 'findAll by example sorted by an association property keeps a team without a club'() {
        when:
        final results = Team.findAll(new Team(), [sort: 'club.name'])

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
    }

    void 'findAllWhere sorted by an association property finds a team without a club'() {
        expect:
        Team.findAllWhere([name: 'Unattached First Team'], [sort: 'club.name'])*.name == ['Unattached First Team']
    }

    void 'findWhere sorted by an association property finds a team without a club'() {
        expect:
        Team.findWhere([name: 'Unattached First Team'], [sort: 'club.name'])?.name == 'Unattached First Team'
    }

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

    void 'a criteria query with pagination arguments sorted by a nested association property keeps a player whose team has no club'() {
        when:
        final results = Player.createCriteria().list(sort: 'team.club.name', max: 10) {
            like('name', '%Player%')
        }

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['Arsenal Player', 'Chelsea Player', 'MU Player']

        and: 'the count joins the nested association as the list does'
        results.totalCount == 4
    }

    void 'a criteria query with pagination arguments sorted by a nested association property reuses a join of the criteria'() {
        when:
        final results = Player.createCriteria().list(sort: 'team.club.name', order: 'desc', max: 10) {
            team {
                like('name', '%First%')
            }
        }

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']
        results.totalCount == 4
    }

    void 'a criteria query with pagination arguments sorted by a nested association property reuses a join that the criteria make through an alias'() {
        when:
        final results = Player.createCriteria().list(sort: 'team.club.name', order: 'desc', max: 10) {
            createAlias('team', 't')
            createAlias('t.club', 'c')
            like('c.name', '%e%')
        }

        then:
        results*.name == ['MU Player', 'Chelsea Player', 'Arsenal Player']
        results.totalCount == 3
    }

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

    void 'a criteria query ordered by an association property #direction keeps a team without a club'() {
        when:
        final results = Team.createCriteria().list {
            like('name', '%First%')
            order('club.name', direction)
        }

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == expected

        where:
        direction || expected
        'asc'     || ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'desc'    || ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'a criteria query can be ordered by an association property with #description'() {
        when:
        final results = Team.createCriteria().list {
            like('name', '%First%')
            order(sortOrder)
        }

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == expected

        where:
        description             | sortOrder                     || expected
        'the name of the path'  | 'club.name'                   || ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'an order for the path' | Query.Order.desc('club.name') || ['MU First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'a criteria query ordered by a nested association property keeps a player whose team has no club'() {
        when:
        final fromRoot = Player.createCriteria().list {
            order('team.club.name', 'desc')
        }
        final fromAssociation = Player.createCriteria().list {
            team {
                order('club.name', 'desc')
            }
        }

        then:
        'Unattached Player' in fromRoot*.name
        fromRoot*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']

        and: 'an order inside the criteria of an association is relative to that association'
        fromAssociation*.name - 'Unattached Player' == ['MU Player', 'Chelsea Player', 'Arsenal Player']
    }

    void 'a paged criteria query ordered by an association property counts a team without a club'() {
        when:
        final results = Team.createCriteria().list(max: 10) {
            like('name', '%First%')
            order('club.name')
        }

        then:
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        results.totalCount == 4
    }

    void 'a criteria query ordered by an association property uses the join that the criteria make #description'() {
        when:
        final results = Player.createCriteria().list(criteria)

        then:
        results*.name == ['MU Player', 'Chelsea Player', 'Arsenal Player']

        where:
        description                            | criteria
        'for the association'                  | { team { club { like('name', '%e%') } }; order('team.club.name', 'desc') }
        'for the association, after the order' | { order('team.club.name', 'desc'); team { club { like('name', '%e%') } } }
        'through an alias'                     | { createAlias('team', 't'); createAlias('t.club', 'c'); like('c.name', '%e%'); order('team.club.name', 'desc') }
        'through an alias, after the order'    | { order('team.club.name', 'desc'); createAlias('team', 't'); createAlias('t.club', 'c'); like('c.name', '%e%') }
    }

    void 'a paged criteria query can be sorted through the alias that the criteria give an association: #sort'() {
        when:
        final results = Player.createCriteria().list(sort: sort, order: 'desc', max: 10) {
            createAlias('team', 't')
            createAlias('t.club', 'c')
        }

        then:
        results*.name == ['MU Player', 'Chelsea Player', 'Arsenal Player']

        where:
        sort << ['t.club.name', 'team.club.name']
    }

    void 'a paged criteria query can be sorted by a map of properties'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = Team.createCriteria().list(sort: ['club.name': 'asc', name: 'desc'], max: 10) {
            isNotNull('club')
        }

        then:
        results*.name == ['Arsenal Second Team', 'Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        results.totalCount == 4
    }

    void 'a paged criteria query sorted by a nested path is not resolved through an alias that has the name of the first association'() {
        given:
        saveEmployees()

        when:
        final results = AssociationSortEmployee.createCriteria().list(max: 10, sort: 'manager.department.name') {
            createAlias('department', 'd')
            createAlias('d.manager', 'manager')
        }

        then: 'sorted by the department of the manager of each employee, not by that of the manager of their department'
        results*.name == ['e2', 'e1']
        results.totalCount == 2
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

    void 'an inner join requested for a nested path is not made through an alias that has the name of the first association'() {
        given:
        saveEmployees()
        final departmentZ = new AssociationSortDepartment(name: 'Z dept', manager: AssociationSortEmployee.findByName('m1')).save(failOnError: true)
        final managerThree = new AssociationSortEmployee(name: 'm3').save(failOnError: true)
        new AssociationSortEmployee(name: 'e3', manager: managerThree, department: departmentZ).save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final names = AssociationSortEmployee.createCriteria().list {
            createAlias('department', 'd')
            createAlias('d.manager', 'manager')
            join('manager.department', JoinType.INNER)
        }*.name.sort()

        then: 'e3, whose own manager has no department, is left out'
        names == ['e1', 'e2']
    }

    void '#api sorted by a collection keeps the elements of another collection of the same class that the query fetches'() {
        given:
        final squad = new AssociationSortSquad(name: 'Squad').save(failOnError: true)
        new AssociationSortMember(name: 'Regular', squad: squad).save(failOnError: true)
        new AssociationSortMember(name: 'Reserve', reserveSquad: squad).save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final List<AssociationSortSquad> results = query.call()

        then:
        results*.name == ['Squad']
        Hibernate.isInitialized(results.first().reserves)
        results.first().reserves*.name == ['Reserve']
        results.first().players*.name == ['Regular']

        where:
        api                         | query
        'a where query'             | { AssociationSortSquad.where { name == 'Squad' }.join('reserves').list(sort: 'players.name') }
        'a paged criteria query'    | { AssociationSortSquad.createCriteria().list(sort: 'players.name', max: 10) { join('reserves') } }
        'order in a criteria query' | { AssociationSortSquad.createCriteria().list { join('reserves'); order('players.name') } }
        'findAll by example'        | { AssociationSortSquad.findAll(new AssociationSortSquad(name: 'Squad'), [sort: 'players.name', fetch: [reserves: 'join']]) }
        'a dynamic finder'          | { AssociationSortSquad.findAllByName('Squad', [sort: 'players.name', fetch: [reserves: 'join']]) }
    }

    void '#api sorted by a bag while it fetches another bag throws an exception, since Hibernate does not fetch two bags in a query'() {
        given:
        saveSquadWithASubstituteOnly()

        when:
        query.call()

        then:
        final UnsupportedOperationException e = thrown()
        e.message.contains('[regulars]')
        e.message.contains('[substitutes]')
        e.message.contains('more than one bag')
        e.message.contains('Declare both of them')

        where:
        api                         | query
        'a where query'             | { AssociationSortBagSquad.where { name == 'Squad' }.join('substitutes').list(sort: 'regulars.name') }
        'order in a criteria query' | { AssociationSortBagSquad.createCriteria().list { join('substitutes'); order('regulars.name') } }
        'a paged criteria query'    | { AssociationSortBagSquad.createCriteria().list(sort: 'regulars.name', max: 10) { join('substitutes') } }
        'findAll by example'        | { AssociationSortBagSquad.findAll(new AssociationSortBagSquad(), [sort: 'regulars.name', fetch: [substitutes: 'join']]) }
        'a dynamic finder'          | { AssociationSortBagSquad.findAllByName('Squad', [sort: 'regulars.name', fetch: [substitutes: 'join']]) }
        'a list'                    | { AssociationSortBagSquad.list(sort: 'regulars.name', fetch: [substitutes: 'join']) }
        'a paged list'              | { AssociationSortBagSquad.list(sort: 'regulars.name', max: 10, fetch: [substitutes: 'join']) }
        'a list with a join type'   | { AssociationSortBagSquad.list(sort: 'regulars.name', fetch: [substitutes: JoinType.LEFT]) }
    }

    void '#api sorted by an empty bag keeps the instance when the query fetches no other bag'() {
        given:
        saveSquadWithASubstituteOnly()

        expect:
        query.call()*.name.unique() == ['Squad']

        where:
        api                                         | query
        'a where query'                             | { AssociationSortBagSquad.where { name == 'Squad' }.list(sort: 'regulars.name') }
        'a where query that fetches the sorted bag' | { AssociationSortBagSquad.where { name == 'Squad' }.join('regulars').list(sort: 'regulars.name') }
        'order in a criteria query'                 | { AssociationSortBagSquad.createCriteria().list { eq('name', 'Squad'); order('regulars.name') } }
        'a list'                                    | { AssociationSortBagSquad.list(sort: 'regulars.name') }
        'a list that fetches the sorted bag'        | { AssociationSortBagSquad.list(sort: 'regulars.name', fetch: [regulars: 'join']) }
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
    void '#api sorted by #sort while it fetches #fetched: #outcome'() {
        given: 'a full squad with two members in each collection, and a squad with a substitute only'
        final full = new AssociationSortMixedSquad(name: 'Full squad').save(failOnError: true)
        FETCHED_ELEMENTS.regulars.each { new AssociationSortMixedMember(name: it, squad: full).save(failOnError: true) }
        FETCHED_ELEMENTS.substitutes.each { new AssociationSortMixedMember(name: it, substituteSquad: full).save(failOnError: true) }
        FETCHED_ELEMENTS.reserves.each { new AssociationSortMixedMember(name: it, reserveSquad: full).save(failOnError: true) }
        FETCHED_ELEMENTS.benched.each { new AssociationSortMixedMember(name: it, benchSquad: full).save(failOnError: true) }
        FETCHED_ELEMENTS.lineup.each { full.addToLineup(new AssociationSortMixedMember(name: it)) }
        full.save(failOnError: true)
        final empty = new AssociationSortMixedSquad(name: 'Empty squad').save(failOnError: true)
        new AssociationSortMixedMember(name: 'Lone substitute', substituteSquad: empty).save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        List<AssociationSortMixedSquad> results = null
        UnsupportedOperationException rejection = null
        try {
            results = query.call(sort, fetched)
        }
        catch (UnsupportedOperationException e) {
            rejection = e
        }

        then:
        (rejection != null) == (outcome == 'rejected')

        and: 'otherwise every squad is returned, and a fetched collection holds its own elements'
        outcome == 'rejected' || results*.name.unique().sort() == ['Empty squad', 'Full squad']
        outcome == 'rejected' || fetched == 'nothing' || results.every { Hibernate.isInitialized(it."$fetched") }
        outcome == 'rejected' || fetched == 'nothing' || results.find { it.name == 'Full squad' }."$fetched"*.name.sort() == FETCHED_ELEMENTS[fetched]

        where:
        [api, sort, fetched, outcome] << [
                ['a where query', 'order in a criteria query', 'a list'],
                [
                        ['reserves.name', 'nothing', 'kept'],
                        ['reserves.name', 'benched', 'kept'],
                        ['reserves.name', 'lineup', 'kept'],
                        ['lineup.name', 'reserves', 'kept'],
                        ['reserves.name', 'regulars', 'rejected'],
                        ['regulars.name', 'nothing', 'kept'],
                        ['regulars.name', 'reserves', 'rejected'],
                        ['regulars.name', 'substitutes', 'rejected'],
                ]
        ].combinations().collect { List row -> [row[0]] + row[1] }
        query = MIXED_QUERIES[api]
    }

    void '#api that fetches the collection it sorts by is not rejected next to a bag, since the sort makes no join of its own'() {
        given:
        final squad = new AssociationSortMixedSquad(name: 'Squad').save(failOnError: true)
        new AssociationSortMixedMember(name: 'Regular', squad: squad).save(failOnError: true)
        new AssociationSortMixedMember(name: 'Reserve', reserveSquad: squad).save(failOnError: true, flush: true)
        manager.session.clear()

        expect:
        query.call()*.name.unique() == ['Squad']

        where:
        api                         | query
        'a where query'             | { AssociationSortMixedSquad.where { name == 'Squad' }.join('reserves').join('regulars').list(sort: 'reserves.name') }
        'order in a criteria query' | { AssociationSortMixedSquad.createCriteria().list { join('reserves'); join('regulars'); order('reserves.name') } }
        'a dynamic finder'          | { AssociationSortMixedSquad.findAllByName('Squad', [sort: 'reserves.name', fetch: [reserves: 'join', regulars: 'join']]) }
        'a list'                    | { AssociationSortMixedSquad.list(sort: 'reserves.name', fetch: [reserves: 'join', regulars: 'join']) }
    }

    void '#api sorted by an association to a single instance is not rejected next to a fetched bag'() {
        given:
        final squad = new AssociationSortMixedSquad(name: 'Squad', league: new AssociationSortLeague(league: 'L', name: 'League').save(failOnError: true)).save(failOnError: true)
        new AssociationSortMixedMember(name: 'Regular', squad: squad).save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final results = query.call()

        then:
        results*.name == ['Squad']
        results.first().regulars*.name == ['Regular']

        where:
        api                         | query
        'a where query'             | { AssociationSortMixedSquad.where { name == 'Squad' }.join('regulars').list(sort: 'league.name') }
        'order in a criteria query' | { AssociationSortMixedSquad.createCriteria().list { join('regulars'); order('league.name') } }
        'a list'                    | { AssociationSortMixedSquad.list(sort: 'league.name', fetch: [regulars: 'join']) }
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

    void 'the total of a list that fetches a collection counts each instance once when a sort joins another collection'() {
        given:
        saveSquadsToPage()

        when:
        final results = AssociationSortSquad.list(sort: 'players.name', max: 10, fetch: [reserves: 'join'])

        then:
        results*.name.sort() == ['First squad', 'Second squad']
        results.totalCount == 2
    }

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

    void 'the total of a list sorted through an association that it fetches keeps an instance without the association: #fetch'() {
        given: 'an author whose publisher has two awards, and an author without a publisher'
        saveAuthorAndCategories()
        new AssociationSortAuthor(name: 'No publisher').save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final results = AssociationSortAuthor.list(max: 10, sort: 'publisher.awards.title', fetch: [publisher: fetch])

        then:
        results.size() == rows
        results.totalCount == rows

        where:
        fetch          || rows
        'join'         || 3
        JoinType.LEFT  || 3
        JoinType.INNER || 2
    }

    void '#api sorted by a collection while it fetches #fetched is rejected, since the bag is read from repeated rows'() {
        given:
        saveAuthorAndCategories()

        when:
        query.call()

        then:
        final UnsupportedOperationException e = thrown()
        e.message.contains("[$fetched]")

        where:
        api                                 | fetched            | query
        'a where query'                     | 'publisher.awards' | { AssociationSortAuthor.where { name ==~ '%' }.list(sort: 'books.title', fetch: ['publisher.awards': JoinType.LEFT]) }
        'a where query that joins the path' | 'publisher.awards' | { AssociationSortAuthor.where { name ==~ '%' }.join('publisher').join('publisher.awards').list(sort: 'books.title') }
        'order in a criteria query'         | 'publisher.awards' | { AssociationSortAuthor.createCriteria().list { join('publisher'); join('publisher.awards'); order('books.title') } }
        'a dynamic finder'                  | 'publisher.awards' | { AssociationSortAuthor.findAllByNameLike('%', [sort: 'books.title', fetch: ['publisher.awards': JoinType.LEFT]]) }
        'findAll by example'                | 'publisher.awards' | { AssociationSortAuthor.findAll(new AssociationSortAuthor(), [sort: 'books.title', fetch: ['publisher.awards': JoinType.LEFT]]) }
        'a list'                            | 'publisher.awards' | { AssociationSortAuthor.list(sort: 'books.title', fetch: ['publisher.awards': JoinType.LEFT]) }
        'a where query on a self-reference' | 'parent.children'  | { AssociationSortCategory.where { name == 'Left' }.list(sort: 'children.name', fetch: ['parent.children': JoinType.LEFT]) }
        'a list on a self-reference'        | 'parent.children'  | { AssociationSortCategory.list(sort: 'children.name', fetch: ['parent.children': JoinType.LEFT]) }
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

    void 'a criteria query ordered by the identifier of a hasOne association keeps a player without it'() {
        given:
        new Player(name: 'Player without contract', team: Team.findByName('MU First Team')).save(validate: false, flush: true)
        manager.session.clear()

        when:
        final names = Player.createCriteria().list { order('contract.id') }*.name

        then:
        names.size() == 5
        'Player without contract' in names
    }

    void 'a projection of a property of an embedded component of an association uses the join of the association'() {
        given:
        final ground = new AssociationSortGround(name: 'Ground', address: new AssociationSortAddress(city: 'London')).save(failOnError: true)
        new AssociationSortVenue(name: 'Venue', ground: ground).save(failOnError: true)
        new AssociationSortVenue(name: 'Venue without a ground').save(failOnError: true, flush: true)
        manager.session.clear()

        expect:
        AssociationSortVenue.where { name ==~ 'Venue%' }.property('ground.address.city').list() as Set == ['London', null] as Set
        AssociationSortVenue.where { name ==~ 'Venue%' }.list(sort: 'ground.address.city')*.name as Set == ['Venue', 'Venue without a ground'] as Set
    }

    void 'a criteria query adds its orders in the sequence in which it requests them: #description'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = Team.createCriteria().list(criteria)

        then:
        results*.name == expected

        where:
        description                              | criteria                                                              || expected
        'an association, then a Hibernate order' | { isNotNull('club'); order('club.name'); order(Order.desc('name')) } || ['Arsenal Second Team', 'Arsenal First Team', 'Chelsea First Team', 'MU First Team']
        'a Hibernate order, then an association' | { isNotNull('club'); order(Order.desc('name')); order('club.name') } || ['MU First Team', 'Chelsea First Team', 'Arsenal Second Team', 'Arsenal First Team']
        'a property, then an association'        | { isNotNull('club'); order('name', 'desc'); order('club.name') }     || ['MU First Team', 'Chelsea First Team', 'Arsenal Second Team', 'Arsenal First Team']
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

    void 'an order with a Query.Order inside the criteria of an association is relative to that association'() {
        given:
        savePlayer('Zed Player', new Team(name: 'A Team').save(failOnError: true))

        when:
        final results = Player.createCriteria().list {
            team {
                order(Query.Order.asc('name'))
            }
        }

        then:
        results*.name == ['Zed Player', 'Arsenal Player', 'Chelsea Player', 'MU Player', 'Unattached Player']
    }

    void 'a criteria query ordered by a property of a collection does not fetch the collection'() {
        given:
        saveSquad()

        when:
        final results = Team.createCriteria().list {
            eq('name', 'Squad')
            order('players.name')
            maxResults(2)
        }

        then: 'the row limit does not leave players out of the collection'
        !Hibernate.isInitialized(results.first().players)
        results.first().players.size() == 3
    }

    void 'a criteria query ordered by a property of a collection fetches the collection when #description'() {
        given:
        saveSquad()

        when:
        final List<Team> results = query.call()

        then:
        results*.name.unique() == ['Squad']
        results.every { Hibernate.isInitialized(it.players) }
        results.first().players.size() == 3

        where:
        description                          | query
        'join requests it before the order'  | { Team.createCriteria().list { eq('name', 'Squad'); join('players'); order('players.name') } }
        'join requests it after the order'   | { Team.createCriteria().list { eq('name', 'Squad'); order('players.name'); join('players') } }
        'a fetch mode requests it'           | { Team.createCriteria().list { eq('name', 'Squad'); fetchMode('players', FetchMode.JOIN); order('players.name') } }
        'the fetch argument requests it'     | { Team.createCriteria().list(fetch: [players: 'join']) { eq('name', 'Squad'); order('players.name') } }
        'listDistinct requests it with join' | { Team.createCriteria().listDistinct { eq('name', 'Squad'); join('players'); order('players.name') } }
        'join requests it for a Query.Order' | { Team.createCriteria().list { eq('name', 'Squad'); join('players'); order(Query.Order.asc('players.name')) } }
    }

    void '#api sorted by a property of a collection fetches the collection when the fetch argument requests it'() {
        given:
        saveSquad()

        when:
        final Team team = query.call()

        then:
        team.name == 'Squad'
        Hibernate.isInitialized(team.players)
        team.players.size() == 3

        where:
        api                                | query
        'a dynamic finder'                 | { Team.findAllByName('Squad', [sort: 'players.name', fetch: [players: 'join']]).first() }
        'findAll by example'               | { Team.findAll(new Team(name: 'Squad'), [sort: 'players.name', fetch: [players: 'join']]).first() }
        'findAllWhere'                     | { Team.findAllWhere([name: 'Squad'], [sort: 'players.name', fetch: [players: 'join']]).first() }
        'a criteria query with pagination' | { Team.createCriteria().list(sort: 'players.name', fetch: [players: 'join']) { eq('name', 'Squad') }.first() }
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

    void '#api sorted by a property of a collection keeps a team without players'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Playerless First Team').save(failOnError: true, flush: true)

        when:
        final List<Team> results = query.call()

        then:
        'Playerless First Team' in results*.name
        results*.name - 'Playerless First Team' == ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Unattached First Team']

        where:
        api                                | query
        'list'                             | { Team.list(sort: 'players.name') }
        'a dynamic finder'                 | { Team.findAllByNameLike('%First%', [sort: 'players.name']) }
        'findAll by example'               | { Team.findAll(new Team(), [sort: 'players.name']) }
        'a criteria query with pagination' | { Team.createCriteria().list(sort: 'players.name', max: 10) { like('name', '%First%') } }
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
