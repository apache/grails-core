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
package grails.gorm.specs

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.transactions.Rollback

import jakarta.persistence.criteria.JoinType

/**
 * Tests for where-query bug fixes in PR 2.
 */
class WhereQueryBugFixSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
        WqAuthor, WqBookItem
    )
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    @Rollback
    @Issue("https://github.com/apache/grails-core/issues/14485")
    def "#14485 - LEFT JOIN in DetachedCriteria subquery should not be downgraded to INNER JOIN"() {
        given: "authors with and without books"
        def authorWithBooks = new WqAuthor(name: 'Author A').save(flush: true, failOnError: true)
        def authorNoBooks = new WqAuthor(name: 'Author B').save(flush: true, failOnError: true)
        def authorWithBio = new WqAuthor(name: 'Author C').save(flush: true, failOnError: true)
        new WqBookItem(title: 'Novel', wqAuthor: authorWithBooks).save(flush: true, failOnError: true)
        new WqBookItem(title: 'Biography', wqAuthor: authorWithBio).save(flush: true, failOnError: true)

        when: "querying authors using a subquery with LEFT JOIN on books"
        def subquery = WqAuthor.where {
            join('wqBookItems', JoinType.LEFT)
            wqBookItems {
                or {
                    isNull('title')
                    ilike('title', '%biography%')
                }
            }
        }.id()

        def results = WqAuthor.where {
            'in'('id', subquery)
        }.list()

        then: "both authors without books (NULL from LEFT JOIN) and with biography are found"
        results.size() == 2
        results*.name.sort() == ['Author B', 'Author C']
    }

    @Rollback
    @Issue("https://github.com/apache/grails-core/issues/14485")
    def "#14485 - direct LEFT JOIN where query returns authors without books"() {
        given: "authors with and without books"
        def authorWithBooks = new WqAuthor(name: 'Writer A').save(flush: true, failOnError: true)
        def authorNoBooks = new WqAuthor(name: 'Writer B').save(flush: true, failOnError: true)
        new WqBookItem(title: 'A Novel', wqAuthor: authorWithBooks).save(flush: true, failOnError: true)

        when: "querying with LEFT JOIN directly (not as subquery)"
        def results = WqAuthor.where {
            join('wqBookItems', JoinType.LEFT)
            wqBookItems {
                isNull('title')
            }
        }.list()

        then: "author without books is found via LEFT JOIN null match"
        results.size() == 1
        results[0].name == 'Writer B'
    }
}

@Entity
class WqAuthor implements HibernateEntity<WqAuthor> {
    String name
    static hasMany = [wqBookItems: WqBookItem]
}

@Entity
class WqBookItem implements HibernateEntity<WqBookItem> {
    String title
    static belongsTo = [wqAuthor: WqAuthor]
}
