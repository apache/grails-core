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
import grails.gorm.tests.entities.Club
import grails.gorm.tests.entities.Contract
import grails.gorm.tests.entities.Player
import grails.gorm.tests.entities.Team
import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * A sort by a property of an association must not leave out the instances whose association is null.
 */
class AssociationSortSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    void setupSpec() {
        manager.registerDomainClasses(Club, Team, Player, Contract, AssociationSortBook, AssociationSortAuthor)
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

    void 'an order in an association block that names neither a property nor an alias of the block stays in the block'() {
        when:
        final results = Player.withCriteria {
            team {
                order('unknown.name')
            }
        }

        then: 'it does not reach the query, as no order of a block did before'
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
