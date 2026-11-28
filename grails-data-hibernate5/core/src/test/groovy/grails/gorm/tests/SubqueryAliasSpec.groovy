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
import spock.lang.Issue
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
    @Issue('https://github.com/apache/grails-core/issues/17177')
    void "a subquery that uses the enclosing alias is one side of an or or an and"() {
        given:
        Club united = new Club(name: "Manchester United").save(failOnError: true)
        Club arsenal = new Club(name: "Arsenal").save(failOnError: true)
        new Team(name: "United First Team", club: united).save(failOnError: true)
        new Team(name: "United Reserves", club: united).save(failOnError: true)
        new Team(name: "Arsenal First Team", club: arsenal).save(flush: true, failOnError: true)

        when:
        List<String> either = Team.where {
            def t = Team
            name == "United Reserves" || exists(Club.where { id == t.club && name == "Arsenal" }.property('name'))
        }.list()*.name.sort()
        List<String> both = Team.where {
            def t = Team
            name != "United Reserves" && exists(Club.where { id == t.club && name == "Manchester United" }.property('name'))
        }.list()*.name.sort()

        then: 'it compiles, and each side of the or keeps its rows'
        either == ['Arsenal First Team', 'United Reserves']
        both == ['United First Team']
    }
}
