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
        Club c = new Club(name: "Manchester United").save()
        new Team(name: "First Team", club: c).save(flush:true)

        when:
        Team t = Team.where {
            def t = Team
            name == "First Team"
            exists(Club.where {
                id == t.club
            }.property('name'))

        }.find()

        then:
        t != null
    }

    @Rollback
    void "a subquery that compares the club of the enclosing alias with its own identifier keeps that comparison, in either order"() {
        given:
        Club united = new Club(name: "Manchester United").save()
        Club arsenal = new Club(name: "Arsenal").save()
        new Team(name: "United First Team", club: united).save()
        new Team(name: "Arsenal First Team", club: arsenal).save(flush: true)

        when:
        List<String> aliasFirst = Team.where {
            def t = Team
            exists(Club.where {
                name == "Manchester United"
                t.club == id
            }.property('name'))
        }.list()*.name
        List<String> idFirst = Team.where {
            def t = Team
            exists(Club.where {
                name == "Manchester United"
                id == t.club
            }.property('name'))
        }.list()*.name

        then:
        aliasFirst == ['United First Team']
        idFirst == ['United First Team']
    }

    @Rollback
    void "a subquery compares the club of the enclosing alias with its own row, by identifier or by its alias, with == and !="() {
        given: 'two United teams and one Arsenal team'
        Club united = new Club(name: "Manchester United").save()
        Club arsenal = new Club(name: "Arsenal").save()
        new Team(name: "United First Team", club: united).save()
        new Team(name: "United Second Team", club: united).save()
        new Team(name: "Arsenal First Team", club: arsenal).save(flush: true)

        when:
        List<String> notId = Team.where {
            def t = Team
            exists(Club.where { t.club != id && name == "Manchester United" }.property('name'))
        }.list()*.name.sort()
        List<String> aliasRight = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club == c && name == "Manchester United" }.property('name'))
        }.list()*.name.sort()
        List<String> aliasLeft = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c == t.club && name == "Manchester United" }.property('name'))
        }.list()*.name.sort()
        List<String> notAlias = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club != c && name == "Manchester United" }.property('name'))
        }.list()*.name.sort()

        List<String> notAliasId = Team.where {
            def t = Team
            exists(Club.where { def c = Club; t.club != c.id && name == "Manchester United" }.property('name'))
        }.list()*.name.sort()
        List<String> aliasIdNot = Team.where {
            def t = Team
            exists(Club.where { def c = Club; c.id != t.club && name == "Manchester United" }.property('name'))
        }.list()*.name.sort()
        List<String> idNot = Team.where {
            def t = Team
            exists(Club.where { id != t.club && name == "Manchester United" }.property('name'))
        }.list()*.name.sort()

        then: 'every form gives the rows of the comparison with the identifier'
        notId == ['Arsenal First Team']
        aliasRight == ['United First Team', 'United Second Team']
        aliasLeft == ['United First Team', 'United Second Team']
        notAlias == ['Arsenal First Team']
        notAliasId == ['Arsenal First Team']
        aliasIdNot == ['Arsenal First Team']
        idNot == ['Arsenal First Team']
    }
}
