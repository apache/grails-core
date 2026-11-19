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

import grails.gorm.tests.entities.Club
import grails.gorm.tests.entities.Team
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 01/03/2017.
 */
@ApplyDetachedCriteriaTransform
class SubqueryAliasSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore datastore = new HibernateDatastore(
            Club, Team
    )

    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Rollback
    void "Test subquery with root alias"() {
        given:
        Club c1 = new Club(name: "Manchester United").save()
        Club c2 = new Club(name: "Arsenal").save()
        new Team(name: "First Team", club: c1).save(flush: true)
        new Team(name: "Second Team", club: c2).save(flush: true)

        when:
        List<Team> teams = Team.where {
            def t = Team
            exists(Club.where {
                id == t.club.id && name == "Manchester United"
            }.property('name'))

        }.list()

        then:
        teams.size() == 1
        teams[0].name == "First Team"
    }

    @Rollback
    void "Test subquery comparing an id with an association of the root alias"() {
        given:
        Club c1 = new Club(name: "Manchester United").save()
        Club c2 = new Club(name: "Arsenal").save()
        new Team(name: "First Team", club: c1).save(flush: true)
        new Team(name: "Second Team", club: c2).save(flush: true)

        when:
        List<Team> teams = Team.where {
            def t = Team
            exists(Club.where {
                id == t.club && name == "Manchester United"
            }.property('name'))

        }.list()

        then:
        teams*.name == ["First Team"]
    }

    @Rollback
    void "Test a derived criteria keeps the root alias"() {
        given:
        Club c1 = new Club(name: "Manchester United").save()
        Club c2 = new Club(name: "Arsenal").save()
        new Team(name: "First Team", club: c1).save(flush: true)
        new Team(name: "Second Team", club: c2).save(flush: true)

        when:
        List<Team> teams = Team.where {
            def t = Team
            exists(Club.where {
                id == t.club.id && name == "Manchester United"
            }.property('name'))

        }.sort('name').list()

        then:
        teams.size() == 1
        teams[0].name == "First Team"
    }

    @Rollback
    void "Test a lazy subquery with a root alias"() {
        given:
        Club c1 = new Club(name: "Manchester United").save()
        Club c2 = new Club(name: "Arsenal").save()
        new Team(name: "First Team", club: c1).save(flush: true)
        new Team(name: "Second Team", club: c2).save(flush: true)

        when:
        List<Team> teams = Team.whereLazy {
            def t = Team
            exists(Club.where {
                id == t.club.id && name == "Manchester United"
            }.property('name'))

        }.list()

        then:
        teams.size() == 1
        teams[0].name == "First Team"
    }

    @Rollback
    void "Test a subquery root alias is registered for nested subqueries"() {
        given:
        Club c1 = new Club(name: "Manchester United").save()
        Club c2 = new Club(name: "Arsenal").save()
        new Team(name: "First Team", club: c1).save(flush: true)
        new Team(name: "Second Team", club: c2).save(flush: true)

        when:
        List<Team> teams = Team.where {
            def t = Team
            exists(Club.where {
                def subClub = Club
                id == t.club.id && exists(Club.where {
                    id == subClub.id && name == "Manchester United"
                }.id())
            }.id())
        }.list()

        then:
        teams.size() == 1
        teams[0].name == "First Team"
    }

    @Rollback
    void "Test nested alias path on left-hand PropertyExpression comparison"() {
        given:
        Club c1 = new Club(name: "Manchester United").save()
        Club c2 = new Club(name: "Arsenal").save()
        new Team(name: "Manchester United", club: c1).save(flush: true)
        new Team(name: "Second Team", club: c2).save(flush: true)

        when:
        List<Team> teams = Team.where {
            def t = Team
            def c = club
            c.name == t.club.name && name == t.club.name
        }.list()

        then:
        teams.size() == 1
        teams[0].name == "Manchester United"
    }
}
