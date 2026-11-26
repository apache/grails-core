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

/**
 * A subquery of a where query that names a nested path of the alias of the enclosing query, {@code t.club.id},
 * is compared through the association of that alias. It used to throw a MissingPropertyException because the
 * transform left {@code t.club.id} as an expression on the class.
 */
@Issue('https://github.com/apache/grails-core/issues/16813')
class NestedAliasPathSubqueryHibernateSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Club, Team])
    }

    void setup() {
        def united = new Club(name: 'Manchester United').save(failOnError: true)
        def arsenal = new Club(name: 'Arsenal').save(failOnError: true)
        new Team(club: united, name: 'United First Team').save(failOnError: true)
        new Team(club: united, name: 'United Reserves').save(failOnError: true)
        new Team(club: arsenal, name: 'Arsenal First Team').save(flush: true, failOnError: true)
        manager.session.clear()
    }

    void "a subquery compares with the id reached through an association of the enclosing alias"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id == t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then: 'only the teams of the club the subquery names are returned'
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery compares with the association of the enclosing alias"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id == t.club && name == 'Arsenal' }.property('name'))
        }.list()

        then:
        teams*.name == ['Arsenal First Team']
    }

    void "a subquery that aliases its own root compares it with a nested path of the enclosing alias"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id == t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then: 'the comparison is kept, so only the teams of the club the subquery names are returned'
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery that aliases its own root compares it with the association of the enclosing alias"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id == t.club && name == 'Arsenal' }.property('name'))
        }.list()

        then:
        teams*.name == ['Arsenal First Team']
    }

    void "the order of the two sides of the comparison does not matter for a subquery that aliases its own root"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club.id == c.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a comparison with a nested path of the enclosing alias is kept next to other criteria of the subquery"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id == t.club.id && name != 'Arsenal' }.property('name'))
        }.list()

        then: 'the comparison and the second criterion both apply'
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the id of the subquery on the right keeps the comparison =="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { t.club.id == id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the id of the subquery on the right keeps the comparison !="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { t.club.id != id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the id of the subquery on the right keeps the comparison <"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { t.club.id < id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == []
    }

    void "a subquery with the nested path of the enclosing alias on the left and the id of the subquery on the right keeps the comparison <="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { t.club.id <= id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the id of the subquery on the right keeps the comparison >"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { t.club.id > id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the id of the subquery on the right keeps the comparison >="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { t.club.id >= id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team', 'United First Team', 'United Reserves']
    }

    void "a subquery with the id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison =="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id == t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery with the id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison !="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id != t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team']
    }

    void "a subquery with the id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison >"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id > t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == []
    }

    void "a subquery with the id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison >="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id >= t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery with the id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison <"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id < t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team']
    }

    void "a subquery with the id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison <="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { id <= t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team', 'United First Team', 'United Reserves']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the aliased id of the subquery on the right keeps the comparison =="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club.id == c.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the aliased id of the subquery on the right keeps the comparison !="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club.id != c.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the aliased id of the subquery on the right keeps the comparison <"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club.id < c.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == []
    }

    void "a subquery with the nested path of the enclosing alias on the left and the aliased id of the subquery on the right keeps the comparison <="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club.id <= c.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the aliased id of the subquery on the right keeps the comparison >"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club.id > c.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team']
    }

    void "a subquery with the nested path of the enclosing alias on the left and the aliased id of the subquery on the right keeps the comparison >="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club.id >= c.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team', 'United First Team', 'United Reserves']
    }

    void "a subquery with the aliased id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison =="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id == t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery with the aliased id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison !="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id != t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team']
    }

    void "a subquery with the aliased id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison >"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id > t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == []
    }

    void "a subquery with the aliased id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison >="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id >= t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery with the aliased id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison <"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id < t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team']
    }

    void "a subquery with the aliased id of the subquery on the left and the nested path of the enclosing alias on the right keeps the comparison <="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id <= t.club.id && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['Arsenal First Team', 'United First Team', 'United Reserves']
    }

    void "an alias declared in one method does not turn a local variable of a later method into an alias"() {
        when: 'a Map named t, while an earlier method of this class declared t as an alias'
        Map t = [club: [name: 'Arsenal']]
        def clubs = Club.where { name == t.club.name }.list()

        then:
        clubs*.name == ['Arsenal']
    }

    void "a subquery compares its own property with a property of the association of the enclosing alias, in either order"() {
        when:
        def subqueryFirst = Team.where {
            def t = Team
            exists(Club.where { name == t.club.name && name == 'Arsenal' }.property('name'))
        }.list()
        def aliasFirst = Team.where {
            def t = Team
            exists(Club.where { t.club.name == name && name == 'Arsenal' }.property('name'))
        }.list()

        then: 'the club of the team is joined in the enclosing query, so its name can be compared'
        subqueryFirst*.name == ['Arsenal First Team']
        aliasFirst*.name == ['Arsenal First Team']
    }

    void "a subquery compares a property of the association of the enclosing alias with !="() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { name != t.club.name && name == 'Arsenal' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "a subquery that aliases its own root compares it with a property of the association of the enclosing alias"() {
        when:
        def teams = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.name == t.club.name && name == 'Manchester United' }.property('name'))
        }.list()

        then:
        teams*.name.sort() == ['United First Team', 'United Reserves']
    }

    void "the join for a property of the association of the enclosing alias keeps the rows where the association is null"() {
        given: 'a team without a club'
        new Team(name: 'Clubless').save(flush: true, failOnError: true)
        manager.session.clear()

        when: 'no Arsenal club has the name of the club of the team'
        def teams = Team.where {
            def t = Team
            notExists(Club.where { name == t.club.name && name == 'Arsenal' }.property('name'))
        }.list()

        then: 'the team without a club is kept, so the join is a left join'
        teams*.name.sort() == ['Clubless', 'United First Team', 'United Reserves']
    }

    void "a property of a collection of the enclosing alias is not joined, and the query fails as before"() {
        when: 'a join of a collection would repeat the rows of the enclosing query'
        Team.where {
            def t = Team
            exists(Club.where { name == t.players.name }.property('name'))
        }.list()

        then:
        Exception e = thrown()
        (e.message + e.cause?.message).contains('could not resolve property')
    }
}
