/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.specs.detachedcriteria

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class DetachedCriteriaCountSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(CountItem)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    private void createTestData() {
        (1..10).each { new CountItem(itemGroup: 1, itemValue: "a${it}").save() }
        (1..16).each { new CountItem(itemGroup: 2, itemValue: "b${it}").save() }
        (1..9).each { new CountItem(itemGroup: 3, itemValue: "c${it}").save() }
        (1..18).each { new CountItem(itemGroup: 4, itemValue: "d${it}").save() }
        (1..5).each { new CountItem(itemGroup: 5, itemValue: "e${it}").save(flush: true) }
    }

    @Rollback
    def 'count without projections returns total row count'() {
        given:
        createTestData()

        when:
        def c = new DetachedCriteria(CountItem)

        then:
        c.count() == 58
    }

    @Rollback
    def 'count with criteria filter returns filtered count'() {
        given:
        createTestData()

        when:
        def c = CountItem.where { itemGroup == 1 }

        then:
        c.count() == 10
    }

    @Rollback
    @Issue('https://github.com/apache/grails-core/issues/14569')
    def 'count with groupProperty and count projections returns number of groups'() {
        given:
        createTestData()

        when:
        def c = CountItem.where {
            projections {
                groupProperty 'itemGroup'
                count()
            }
        }
        def groups = c.list()

        then:
        groups.size() == 5

        and:
        c.count() == 5
    }

    @Rollback
    def 'count with groupProperty projection only returns number of groups'() {
        given:
        createTestData()

        when:
        def c = new DetachedCriteria(CountItem).build {
            projections {
                groupProperty 'itemGroup'
            }
        }

        then:
        c.list().size() == 5
        c.count() == 5
    }

    @Rollback
    def 'count with single aggregate projection returns 1'() {
        given:
        createTestData()

        when:
        def c = new DetachedCriteria(CountItem).build {
            projections {
                sum 'itemGroup'
            }
        }

        then:
        c.count() == 1
    }

    @Rollback
    def 'count with groupProperty and criteria filter returns filtered group count'() {
        given:
        createTestData()

        when:
        def c = CountItem.where {
            itemGroup in [1, 2, 3]
            projections {
                groupProperty 'itemGroup'
                count()
            }
        }

        then:
        c.list().size() == 3
        c.count() == 3
    }
}

@Entity
class CountItem {

    int itemGroup
    String itemValue
}
