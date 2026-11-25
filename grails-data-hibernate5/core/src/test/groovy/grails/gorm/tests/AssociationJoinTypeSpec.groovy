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
import org.hibernate.Hibernate

import jakarta.persistence.criteria.JoinType

class AssociationJoinTypeSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    private static final List<String> TEAMS_WITH_A_CLUB = ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Playerless First Team']
    private static final List<String> TEAMS_WITH_PLAYERS = ['Arsenal First Team', 'Chelsea First Team', 'MU First Team', 'Unattached First Team']
    private static final List<String> ALL_TEAMS = (TEAMS_WITH_A_CLUB + 'Unattached First Team').sort()

    void setupSpec() {
        manager.domainClasses.addAll([Club, Team, Player, Contract, AssociationJoinTypeCategory, AssociationJoinTypeShelf, AssociationJoinTypeTag,
                                      AssociationJoinTypeFace, AssociationJoinTypeNose, AssociationJoinTypePost, AssociationJoinTypeAuthor,
                                      AssociationJoinTypeBook, AssociationJoinTypeSquad, AssociationJoinTypeSquadMember, AssociationJoinTypeTrophy, AssociationJoinTypeMedal,
                                      AssociationJoinTypePrize, AssociationJoinTypeRibbon])
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

    private static void savePlayer(String name, Team team) {
        final player = new Player(name: name, team: team)
        player.contract = new Contract(salary: 1000G, player: player)
        player.save(failOnError: true, flush: true)
    }

    private void saveTeamWithAPlayerWithoutContract() {
        final squad = new Team(club: Club.findByName('Arsenal'), name: 'Squad Team').save(failOnError: true)
        savePlayer('Squad Player A', squad)
        savePlayer('Squad Player B', squad)
        new Player(name: 'Squad Player without contract', team: squad).save(validate: false, flush: true)
        manager.session.clear()
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

    private void saveShelves() {
        new AssociationJoinTypeShelf(name: 'First tagged shelf').addToTags(new AssociationJoinTypeTag(name: 'x')).save(failOnError: true)
        new AssociationJoinTypeShelf(name: 'First untagged shelf').save(failOnError: true)
        new AssociationJoinTypeShelf(name: 'Other shelf').addToTags(new AssociationJoinTypeTag(name: 'y')).save(failOnError: true, flush: true)
        manager.session.clear()
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

    void 'a criteria query with an inner join that fetch requests returns the same rows with a sort'() {
        when:
        final results = Team.createCriteria().list(sort: 'club.name', fetch: [club: JoinType.INNER]) {
            like('name', '%First%')
        }

        then:
        results*.name.sort() == TEAMS_WITH_A_CLUB
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
        'findAll by example' | { Team.findAll(new Team(), [sort: 'club.name', fetch: [club: JoinType.INNER]]) }
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

    void 'a join type requested for a property that is not an association is ignored'() {
        when:
        final results = Team.where {
            name ==~ '%First%'
        }.list(fetch: [name: JoinType.INNER])

        then:
        results*.name.sort() == ALL_TEAMS
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

    void 'a projection with a left join of a collection that criteria use returns its values'() {
        given:
        saveSquads()

        when:
        final names = AssociationJoinTypeSquad.where { players { kind == null } }.join('players', JoinType.LEFT).join('reserves').property('name').list()

        then:
        names == ['Bench']
    }

    private void saveSquads() {
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
