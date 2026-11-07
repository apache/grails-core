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
import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

import jakarta.persistence.criteria.JoinType

/**
 * A sort by a property of an association must not leave out the instances whose association is null.
 */
class AssociationSortSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    void setupSpec() {
        manager.registerDomainClasses(Club, Team, Player, Contract, AssociationSortBook, AssociationSortAuthor,
                AssociationSortOwner, AssociationSortScore, AssociationSortPet)
    }

    void setup() {
        ['Manchester United': 'Bruno', 'Arsenal': 'Bukayo', 'Chelsea': 'Cole'].each { clubName, playerName ->
            final club = new Club(name: clubName).save()
            final team = new Team(club: club, name: "${clubName} First Team").save()
            new Player(name: playerName, team: team, contract: new Contract(salary: 1)).save()
        }
        final team = new Team(name: 'Unattached First Team').save(failOnError: true)
        new Player(name: 'Unattached Player', team: team, contract: new Contract(salary: 1)).save(failOnError: true)
        final king = new AssociationSortAuthor(name: 'King').save(failOnError: true)
        final adams = new AssociationSortAuthor(name: 'Adams').save(failOnError: true)
        new AssociationSortBook(title: 'It', details: new AssociationSortBookDetails(isbn: '1', author: king)).save(failOnError: true)
        new AssociationSortBook(title: 'Guide', details: new AssociationSortBookDetails(isbn: '2', author: adams)).save(failOnError: true)
        new AssociationSortBook(title: 'Anonymous', details: new AssociationSortBookDetails(isbn: '3')).save(failOnError: true)
        [Ann: [1, 9], Bob: [9, 1]].each { ownerName, scores ->
            final owner = new AssociationSortOwner(name: ownerName)
            owner.addToScores(round: 'first', score: scores[0])
            owner.addToScores(round: 'second', score: scores[1])
            owner.save(failOnError: true)
            new AssociationSortPet(name: "${ownerName}'s pet", owner: owner).save(failOnError: true)
        }
        manager.session.flush()
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
        'club.name' || ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        'club.id'   || ['Manchester United First Team', 'Arsenal First Team', 'Chelsea First Team']
    }

    void 'list sorted by a nested association property keeps a player whose team has no club'() {
        when:
        final results = Player.list(sort: 'team.club.name', order: 'desc')

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['Bruno', 'Cole', 'Bukayo']
    }

    void 'list sorted by a map sorts by every entry of the map'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = Team.list(sort: ['club.name': 'asc', name: 'desc'])

        then:
        results*.name - 'Unattached First Team' == ['Arsenal Second Team', 'Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }

    void 'a paged list sorted by an association property returns the teams that it counts'() {
        when:
        final results = Team.list(sort: 'club.name', max: 10)

        then:
        results.size() == 4
        results.totalCount == 4
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
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        results.totalCount == 4
    }

    void 'a criteria query with pagination arguments sorted by a nested association property keeps a player whose team has no club'() {
        when:
        final results = Player.createCriteria().list(sort: 'team.club.name', max: 10) {
            like('name', '%')
        }

        then:
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['Bukayo', 'Cole', 'Bruno']
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
        results*.name - 'Unattached Player' == ['Bruno', 'Cole', 'Bukayo']
        results.totalCount == 4
    }

    void 'an order by an association property in a criteria query keeps a team without a club'() {
        when:
        final results = Team.createCriteria().list {
            like('name', '%First%')
            order('club.name')
        }

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }

    void 'an order inside an association block of a criteria query sorts by the property of the association'() {
        when:
        final results = Team.withCriteria {
            club {
                order('name', direction)
            }
        }

        then: 'the block joins the association with an inner join'
        results*.name == expected

        where:
        direction || expected
        'asc'     || ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        'desc'    || ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'an order inside an association block of #junction sorts by the property of the association'() {
        when:
        final results = Team.withCriteria {
            "$junction" {
                club {
                    like('name', pattern)
                    order('name', 'desc')
                }
            }
        }

        then:
        results*.name == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']

        where:
        junction | pattern
        'and'    | '%'
        'or'     | '%'
        'not'    | 'no club has this name'
    }

    void 'an order inside a nested association block of a junction sorts by the property of the association'() {
        when:
        final results = Player.withCriteria {
            or {
                team {
                    club {
                        like('name', '%')
                        order('name', 'desc')
                    }
                }
            }
        }

        then:
        results*.name == ['Bruno', 'Cole', 'Bukayo']
    }

    void 'an order by an alias inside an association block of a junction sorts by that alias'() {
        when:
        final results = Player.withCriteria {
            and {
                team {
                    createAlias('club', 'c')
                    like('c.name', '%')
                    order('c.name', 'desc')
                }
            }
        }

        then:
        results*.name == ['Bruno', 'Cole', 'Bukayo']
    }

    void 'an order inside a junction of an association block sorts by the property of the association'() {
        when:
        final results = Player.withCriteria {
            team {
                or {
                    club {
                        like('name', '%')
                        order('name', 'desc')
                    }
                }
            }
        }

        then:
        results*.name == ['Bruno', 'Cole', 'Bukayo']
    }

    void 'an order inside an association block of #junction comes before an order that is declared after the block'() {
        when:
        final results = Team.withCriteria {
            "$junction" {
                club {
                    like('name', pattern)
                    order('name', 'desc')
                }
                order('name')
            }
        }

        then: 'the orders keep the place they were declared in, as they do outside a junction'
        results*.name == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']

        where:
        junction | pattern
        'and'    | '%'
        'or'     | '%'
        'not'    | 'no club has this name'
    }

    void 'an order inside an association block of a junction comes after an order that is declared before the block'() {
        when:
        final results = Team.withCriteria {
            and {
                order('name')
                club {
                    like('name', '%')
                    order('name', 'desc')
                }
            }
        }

        then:
        results*.name == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }

    void 'the orders of nested association blocks of a junction keep the place they were declared in'() {
        when:
        final results = Player.withCriteria {
            or {
                team {
                    club {
                        like('name', '%')
                        order('name', 'desc')
                    }
                    order('name')
                }
                order('name')
            }
        }

        then:
        results*.name == ['Bruno', 'Cole', 'Bukayo']
    }

    void 'a junction restricts by an association and by a property next to it'() {
        expect: 'the builder evaluates the closure of a junction as it evaluates any other closure'
        Team.withCriteria {
            or {
                club {
                    eq('name', 'Arsenal')
                }
                eq('name', 'Chelsea First Team')
            }
            order('name')
        }*.name == ['Arsenal First Team', 'Chelsea First Team']
    }

    void 'a #api sorted by #sort keeps a player without a contract'() {
        given: 'the contract of a player has the key, so the player has no column to sort by'
        new Player(name: 'Free Agent', team: Team.findByName('Arsenal First Team')).save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final results = api == 'list' ? Player.list(sort: sort) : Player.withCriteria { order(sort) }

        then: 'the contract is joined with a left join, also for its identifier'
        'Free Agent' in results*.name
        results.size() == 5

        where:
        [api, sort] << [['list', 'criteria query'], ['contract.id', 'contract.salary']].combinations()
    }

    void 'an order by the identifier of an association in a criteria query keeps the instances without it'() {
        expect: 'the identifier is the foreign key, which is read without a join'
        Team.withCriteria {
            order('club.id', 'desc')
        }*.name.sort() == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team', 'Unattached First Team']
        Team.withCriteria {
            isNotNull('club')
            order('club.id', 'desc')
        }*.name == ['Chelsea First Team', 'Arsenal First Team', 'Manchester United First Team']

        and: 'the association before it on the path is joined with a left join'
        Player.withCriteria {
            order('team.club.id')
        }*.name.sort() == ['Bruno', 'Bukayo', 'Cole', 'Unattached Player']
    }

    void 'an order inside an association block of a detached criteria sorts by the property of the association'() {
        when:
        final results = new DetachedCriteria(Team).build {
            club {
                order('name', 'desc')
            }
        }.list()

        then:
        results*.name == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'an order inside an association block of a detached criteria comes after the orders of the criteria'() {
        given:
        new Team(club: Club.findByName('Arsenal'), name: 'Arsenal Second Team').save(failOnError: true, flush: true)

        when:
        final results = new DetachedCriteria(Team).build {
            club {
                order('name')
            }
            order('name', 'desc')
        }.list()

        then: 'the criteria keep their own orders in front, since the block is only read when the query is built'
        results*.name == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal Second Team', 'Arsenal First Team']
    }

    void 'an order by an alias inside an association block of a detached criteria sorts by that alias'() {
        when:
        final results = new DetachedCriteria(Player).build {
            team {
                createAlias('club', 'c')
                order('c.name', 'desc')
            }
        }.list()

        then:
        results*.name == ['Bruno', 'Cole', 'Bukayo']
    }

    void 'an order in an association block by an alias of the enclosing criteria sorts by that alias'() {
        when:
        final results = Player.withCriteria {
            createAlias('team', 't')
            contract {
                order('t.name', 'desc')
            }
        }

        then:
        results*.name == ['Unattached Player', 'Bruno', 'Cole', 'Bukayo']
    }

    void 'an order in a nested association block by an alias of the root criteria sorts by that alias'() {
        when:
        final results = Contract.withCriteria {
            createAlias('player', 'p')
            player {
                team {
                    order('p.name', 'desc')
                }
            }
        }

        then:
        results*.player*.name == ['Unattached Player', 'Cole', 'Bukayo', 'Bruno']
    }

    void 'a distinct list is ordered by a property of an association'() {
        when:
        final results = Team.createCriteria().listDistinct {
            order('club.name', 'desc')
        }

        then: 'the query is not rejected for an order that is not in its select list'
        results.size() == 4
        results*.name - 'Unattached First Team' == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']
    }

    void 'a distinct list is ordered by an order in an association block'() {
        when:
        final byClub = Team.createCriteria().listDistinct {
            club {
                order('name', 'desc')
            }
        }
        final byPlayer = Team.createCriteria().listDistinct {
            players {
                order('name')
            }
        }

        then:
        byClub*.name == ['Manchester United First Team', 'Chelsea First Team', 'Arsenal First Team']

        and: 'a team is listed once, although the players are joined'
        byPlayer*.name == ['Manchester United First Team', 'Arsenal First Team', 'Chelsea First Team', 'Unattached First Team']
    }

    void 'a distinct list ordered by a collection lists an instance once'() {
        given:
        new Player(name: 'Aaron', team: Team.findByName('Chelsea First Team')).save(failOnError: true, flush: true)
        manager.session.clear()

        when:
        final results = Team.createCriteria().listDistinct {
            order('players.name')
        }

        then:
        results*.name == ['Chelsea First Team', 'Manchester United First Team', 'Arsenal First Team', 'Unattached First Team']
    }

    void 'an order by #property that does not resolve is rejected'() {
        when:
        Team.withCriteria {
            order(property)
        }

        then:
        final e = thrown(IllegalArgumentException)
        e.message == 'Invalid sort property'

        where:
        property << ['bogus', 'club.bogus', 'bogus.name']
    }

    void 'a criteria query restricts by an association inside an embedded component'() {
        expect:
        AssociationSortBook.withCriteria {
            details {
                author {
                    eq('name', 'King')
                }
            }
        }*.title == ['It']
    }

    void 'an order inside an association block of an embedded component sorts by the property of the association'() {
        when:
        final results = AssociationSortBook.withCriteria {
            details {
                author {
                    order('name', 'desc')
                }
            }
        }

        then: 'the block joins the author with an inner join'
        results*.title == ['It', 'Guide']
    }

    void 'an order by an alias that the block of an embedded component declares sorts by that alias'() {
        when:
        final results = AssociationSortBook.withCriteria {
            details {
                createAlias('author', 'a', joinType)
                order('a.name', 'desc')
            }
        }

        then:
        results*.title - 'Anonymous' == ['It', 'Guide']
        ('Anonymous' in results*.title) == kept

        where:
        joinType || kept
        0        || false
        1        || true
    }

    void 'a paged list with a join type requested for an association counts the rows that it returns'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(sort: 'club.name', fetch: [club: JoinType.INNER], max: 10)

        then:
        results.size() == 3
        results.totalCount == 3
    }

    void 'a #query with an inner join requested for an association leaves out a team without a club'() {
        expect:
        resultOf(query) == expected

        where:
        query                  || expected
        'count'                || 3
        'property projection'  || ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        'criteria count'       || 3
    }

    private static Object resultOf(String query) {
        switch (query) {
            case 'count':
                return Team.where {
                    name ==~ '%First%'
                }.join('club', JoinType.INNER).count()
            case 'property projection':
                return Team.where {
                    name ==~ '%First%'
                }.join('club', JoinType.INNER).property('name').list().sort()
            default:
                return Team.createCriteria().count {
                    like('name', '%First%')
                    join('club', JoinType.INNER)
                }
        }
    }

    void 'a dynamic finder sorted by an association property keeps a team without a club'() {
        when:
        final results = Team.findAllByNameLike('%First%', [sort: 'club.name'])

        then:
        'Unattached First Team' in results*.name
        results*.name - 'Unattached First Team' == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
    }

    void 'an order by an alias that an association block declares sorts by the association of the alias'() {
        when:
        final results = Player.withCriteria {
            team {
                createAlias('club', 'c')
                order('c.name', direction)
            }
        }

        then: 'the alias joins the club with an inner join'
        results*.name == expected

        where:
        direction || expected
        'asc'     || ['Bukayo', 'Cole', 'Bruno']
        'desc'    || ['Bruno', 'Cole', 'Bukayo']
    }

    void 'an order by an alias of a nested association block sorts by that alias'() {
        when:
        final results = Contract.withCriteria {
            player {
                team {
                    createAlias('club', 'c')
                    order('c.name', direction)
                }
            }
        }

        then:
        results*.player*.name == expected

        where:
        direction || expected
        'asc'     || ['Bukayo', 'Cole', 'Bruno']
        'desc'    || ['Bruno', 'Cole', 'Bukayo']
    }

    void 'an order by the #alias alias of a collection that has two aliases sorts by that alias'() {
        when:
        final results = AssociationSortOwner.withCriteria {
            createAlias('scores', 'first')
            createAlias('scores', 'second')
            eq('first.round', 'first')
            eq('second.round', 'second')
            order("${alias}.score")
        }

        then:
        results*.name == expected

        where:
        alias    || expected
        'first'  || ['Ann', 'Bob']
        'second' || ['Bob', 'Ann']
    }

    void 'an order in an association block by the #alias alias of a collection that has two aliases sorts #direction by that alias'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                createAlias('scores', 'first')
                createAlias('scores', 'second')
                eq('first.round', 'first')
                eq('second.round', 'second')
                order("${alias}.score", direction)
            }
        }

        then: 'the order does not sort by the other alias of the collection'
        results*.name == expected

        where:
        alias    | direction || expected
        'first'  | 'asc'     || ["Ann's pet", "Bob's pet"]
        'first'  | 'desc'    || ["Bob's pet", "Ann's pet"]
        'second' | 'asc'     || ["Bob's pet", "Ann's pet"]
        'second' | 'desc'    || ["Ann's pet", "Bob's pet"]
    }

    void 'an order by the path of a collection that has two aliases sorts by the join that the path shares with the first alias'() {
        when:
        final results = AssociationSortOwner.withCriteria {
            createAlias('scores', 'first')
            createAlias('scores', 'second')
            eq('first.round', 'first')
            eq('second.round', 'second')
            order('scores.score', direction)
        }

        then:
        results*.name == expected

        where:
        direction || expected
        'asc'     || ['Ann', 'Bob']
        'desc'    || ['Bob', 'Ann']
    }

    void 'an order in an association block by the path of a collection that has two aliases sorts by the join that the path shares with the first alias'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                createAlias('scores', 'first')
                createAlias('scores', 'second')
                eq('first.round', 'first')
                eq('second.round', 'second')
                order('scores.score', direction)
            }
        }

        then:
        results*.name == expected

        where:
        direction || expected
        'asc'     || ["Ann's pet", "Bob's pet"]
        'desc'    || ["Bob's pet", "Ann's pet"]
    }

    void 'an order in the second of two blocks of an association sorts #direction by the joins of its own block'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                scores {
                    eq('round', 'first')
                }
            }
            owner {
                scores {
                    eq('round', 'second')
                }
                order('scores.score', direction)
            }
        }

        then: 'the order does not sort by the scores that the first block joins'
        results*.name == expected

        where:
        direction || expected
        'asc'     || ["Bob's pet", "Ann's pet"]
        'desc'    || ["Ann's pet", "Bob's pet"]
    }

    void 'an order in the first of two blocks of an association sorts by the joins of its own block'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                scores {
                    eq('round', 'first')
                }
                order('scores.score')
            }
            owner {
                scores {
                    eq('round', 'second')
                }
            }
        }

        then:
        results*.name == ["Ann's pet", "Bob's pet"]
    }

    void 'an order in a nested block of the second of two blocks of an association sorts by the join of its own block'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                scores {
                    eq('round', 'first')
                }
            }
            owner {
                scores {
                    eq('round', 'second')
                    order('score')
                }
            }
        }

        then:
        results*.name == ["Bob's pet", "Ann's pet"]
    }

    void 'an order by a path in the second of two blocks that each declare an alias for it sorts by the join of its own block'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                createAlias('scores', 'first')
                eq('first.round', 'first')
            }
            owner {
                createAlias('scores', 'second')
                eq('second.round', 'second')
                order('scores.score')
            }
        }

        then:
        results*.name == ["Bob's pet", "Ann's pet"]
    }

    void 'an order in a block that joins nothing itself sorts by the join that the query has for the association'() {
        when:
        final results = AssociationSortPet.withCriteria {
            owner {
                scores {
                    eq('round', 'second')
                }
            }
            owner {
                order('scores.score')
            }
        }

        then:
        results*.name == ["Bob's pet", "Ann's pet"]
    }

    void 'an order by the identifier of an association in an association block keeps the instances without it'() {
        when:
        final results = Player.withCriteria {
            team {
                order('club.id', 'desc')
            }
        }

        then: 'the foreign key is read from the table of the block, so the club is not joined'
        'Unattached Player' in results*.name
        results*.name - 'Unattached Player' == ['Cole', 'Bukayo', 'Bruno']
    }

    void 'an order by an alias of an association block sorts by that alias when a projection selects the path of the alias'() {
        when:
        final results = AssociationSortPet.withCriteria {
            projections {
                property('owner.scores.score')
            }
            owner {
                createAlias('scores', 'first')
                createAlias('scores', 'second')
                eq('first.round', 'first')
                eq('second.round', 'second')
                order('second.score')
            }
        }

        then: 'the projection selects the score of the first alias, and the order still sorts by the second'
        results == [9, 1]
    }

    void 'an order in an association block is left out of a query that can only be ordered by what it selects'() {
        when: 'the database would reject an order by the name of the club in each of these queries'
        final distinctNames = Team.withCriteria {
            projections {
                distinct('name')
            }
            club {
                like('name', '%')
                order('name', 'desc')
            }
        }
        final lastName = Team.withCriteria {
            projections {
                max('name')
            }
            club {
                like('name', '%')
                order('name', 'desc')
            }
        }

        then: 'they run as they did when no order of a block reached the query'
        distinctNames.sort() == ['Arsenal First Team', 'Chelsea First Team', 'Manchester United First Team']
        lastName == ['Manchester United First Team']
    }

    void 'an order in an association block sorts a distinct query that selects the property of the order'() {
        when:
        final results = Team.withCriteria {
            projections {
                distinct('club.name')
            }
            club {
                order('name', 'desc')
            }
        }

        then:
        results == ['Manchester United', 'Chelsea', 'Arsenal']
    }

    void 'a criteria query with pagination arguments is ordered by an alias of an association block'() {
        when:
        final results = AssociationSortPet.createCriteria().list(max: 10) {
            owner {
                createAlias('scores', 'first')
                createAlias('scores', 'second')
                eq('first.round', 'first')
                eq('second.round', 'second')
                order('second.score')
            }
        }

        then:
        results*.name == ["Bob's pet", "Ann's pet"]
        results.totalCount == 2
    }

    void 'an order in an association block that names neither a property nor an alias has no effect'() {
        when:
        final results = Player.withCriteria {
            team {
                order('unknown.name')
            }
        }

        then: 'it is left out of the query, as every order of a block was before'
        results.size() == 4
    }

    void 'a criteria query that selects a distinct association property is ordered by that property'() {
        when:
        final results = Team.withCriteria {
            projections {
                distinct('club.name')
            }
            order('club.name', direction)
        }

        then: 'the order sorts by the selected expression and not through a join of its own'
        results == expected

        where:
        direction || expected
        'asc'     || ['Arsenal', 'Chelsea', 'Manchester United']
        'desc'    || ['Manchester United', 'Chelsea', 'Arsenal']
    }

    void 'a criteria query that selects a distinct nested association property is ordered by that property'() {
        when:
        final results = Player.withCriteria {
            projections {
                distinct('team.club.name')
            }
            order('team.club.name')
        }

        then:
        results == ['Arsenal', 'Chelsea', 'Manchester United']
    }

    void 'a criteria query that groups by an association property is ordered by that property'() {
        when:
        final results = Team.withCriteria {
            projections {
                groupProperty('club.name')
                count()
            }
            order('club.name', 'desc')
        }

        then:
        results.collect { it[0] } == ['Manchester United', 'Chelsea', 'Arsenal']
    }

    void 'a criteria query with pagination arguments that selects a distinct association property is sorted by that property'() {
        when:
        final results = Team.createCriteria().list(max: 10, sort: 'club.name') {
            projections {
                distinct('club.name')
            }
        }

        then:
        results.toList() == ['Arsenal', 'Chelsea', 'Manchester United']
    }

    void 'a where query that selects a distinct association property is sorted by that property'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.distinct('club.name').sort('club.name').list()

        then:
        results == ['Arsenal', 'Chelsea', 'Manchester United']
    }

    void 'a criteria query that selects distinct associations is ordered by a property of the association'() {
        when:
        final results = Team.withCriteria {
            projections {
                distinct('club')
            }
            order('club.name', direction)
        }

        then: 'the order sorts by the association that the projection selects and not through a join of its own'
        results*.name == expected

        where:
        direction || expected
        'asc'     || ['Arsenal', 'Chelsea', 'Manchester United']
        'desc'    || ['Manchester United', 'Chelsea', 'Arsenal']
    }

    void 'a criteria query that selects distinct nested associations is ordered by a property of the association'() {
        when:
        final results = Player.withCriteria {
            projections {
                distinct('team.club')
            }
            order('team.club.name')
        }

        then:
        results*.name == ['Arsenal', 'Chelsea', 'Manchester United']
    }

    void 'a where query that selects distinct associations is sorted by a property of the association'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.distinct('club').sort('club.name').list()

        then:
        results*.name == ['Arsenal', 'Chelsea', 'Manchester United']
    }

    void 'a criteria query that selects #selection is ordered by a property of the association'() {
        when:
        final results = Team.withCriteria {
            projections {
                "$projection"('club')
                if (grouped) {
                    count()
                }
            }
            order('club.name', 'desc')
        }

        then:
        results.collect { grouped ? it[0].name : it.name } == ['Manchester United', 'Chelsea', 'Arsenal']

        where:
        selection                 | projection      | grouped
        'an association'          | 'property'      | false
        'a grouped association'   | 'groupProperty' | true
    }

    void 'an order in an association block sorts a query that selects distinct instances of the association'() {
        when:
        final results = Team.withCriteria {
            projections {
                distinct('club')
            }
            club {
                order('name', 'desc')
            }
        }

        then:
        results*.name == ['Manchester United', 'Chelsea', 'Arsenal']
    }

    void 'a #api sorted through an embedded component keeps a book whose component has no author'() {
        when:
        final results = booksSortedByAuthor(api)

        then: 'the association inside the component is joined with a left join'
        'Anonymous' in results*.title

        and: 'the other books are sorted by their author'
        results*.title - 'Anonymous' == ['Guide', 'It']

        where:
        api << ['where query', 'list', 'criteria query', 'dynamic finder']
    }

    void 'a paged list sorted through an embedded component returns the books that it counts'() {
        when:
        final results = AssociationSortBook.where {
            title ==~ '%'
        }.list(sort: 'details.author.name', max: 10)

        then:
        results.size() == 3
        results.totalCount == 3
    }

    void 'a sort by a property of an embedded component sorts by that property'() {
        expect:
        AssociationSortBook.list(sort: 'details.isbn', order: 'desc')*.title == ['Anonymous', 'Guide', 'It']
    }

    void 'an order inside a block of an embedded component sorts by the property of the component'() {
        when:
        final results = AssociationSortBook.withCriteria {
            details {
                order('isbn', direction)
            }
        }

        then:
        results*.title == expected

        where:
        direction || expected
        'asc'     || ['It', 'Guide', 'Anonymous']
        'desc'    || ['Anonymous', 'Guide', 'It']
    }

    void 'an order inside a block of an embedded component sorts through an association of the component'() {
        when:
        final results = AssociationSortBook.withCriteria {
            details {
                order('author.name')
            }
        }

        then: 'the association inside the component is joined with a left join'
        'Anonymous' in results*.title
        results*.title - 'Anonymous' == ['Guide', 'It']
    }

    void 'an order in a block of an embedded component that names no property of the component stays in the block'() {
        when:
        final results = AssociationSortBook.withCriteria {
            details {
                order('unknown')
            }
        }

        then:
        results.size() == 3
    }

    private static List<AssociationSortBook> booksSortedByAuthor(String api) {
        switch (api) {
            case 'where query':
                return AssociationSortBook.where {
                    title ==~ '%'
                }.list(sort: 'details.author.name')
            case 'list':
                return AssociationSortBook.list(sort: 'details.author.name')
            case 'criteria query':
                return AssociationSortBook.withCriteria {
                    order('details.author.name')
                }
            default:
                return AssociationSortBook.findAllByTitleLike('%', [sort: 'details.author.name'])
        }
    }
}

@Entity
class AssociationSortBook {
    String title
    AssociationSortBookDetails details
    static embedded = ['details']
}

class AssociationSortBookDetails {
    String isbn
    AssociationSortAuthor author
    static constraints = {
        author nullable: true
    }
}

@Entity
class AssociationSortAuthor {
    String name
}

@Entity
class AssociationSortOwner {
    String name
    static hasMany = [scores: AssociationSortScore]
}

@Entity
class AssociationSortScore {
    String round
    Integer score
    static belongsTo = [owner: AssociationSortOwner]
}

@Entity
class AssociationSortPet {
    String name
    AssociationSortOwner owner
}
