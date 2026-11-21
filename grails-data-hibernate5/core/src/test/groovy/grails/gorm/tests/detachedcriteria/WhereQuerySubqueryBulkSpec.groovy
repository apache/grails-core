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
package grails.gorm.tests.detachedcriteria

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The bulk statements of {@code updateAll} and {@code deleteAll} are written by the datastore core, and a subquery in
 * one of them takes the parameter positions after it, joins its criteria with AND, and has no WHERE without criteria.
 */
class WhereQuerySubqueryBulkSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(SubqueryBulkItem)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    private void seed() {
        new SubqueryBulkItem(name: 'a', amount: 1).save()
        new SubqueryBulkItem(name: 'b', amount: 2).save()
        new SubqueryBulkItem(name: 'c', amount: 3).save(flush: true)
    }

    private DetachedCriteria<SubqueryBulkItem> lowAmounts() {
        new DetachedCriteria<>(SubqueryBulkItem).build {
            projections { property('name') }
            le('amount', 2)
        }
    }

    @Rollback
    void "a criterion after a subquery in a bulk statement gets its own parameter"() {
        given:
        seed()
        DetachedCriteria<SubqueryBulkItem> sub = lowAmounts()

        when:
        int inListUpdated = new DetachedCriteria<>(SubqueryBulkItem).build { inList('name', sub); eq('amount', 1) }.updateAll(label: 'in')

        then:
        inListUpdated == 1
        SubqueryBulkItem.findAllByLabel('in')*.name == ['a']

        when:
        int notInUpdated = new DetachedCriteria<>(SubqueryBulkItem).build { notIn('name', sub); eq('amount', 3) }.updateAll(label: 'notin')

        then:
        notInUpdated == 1
        SubqueryBulkItem.findAllByLabel('notin')*.name == ['c']
    }

    @Rollback
    void "a subquery with two criteria in a bulk statement requires both"() {
        given:
        seed()
        DetachedCriteria<SubqueryBulkItem> sub = new DetachedCriteria<>(SubqueryBulkItem).build {
            projections { property('name') }
            le('amount', 2)
            ge('amount', 2)
        }

        when:
        int updated = new DetachedCriteria<>(SubqueryBulkItem).build { inList('name', sub) }.updateAll(label: 'both')

        then: 'only the item whose amount is both at most and at least 2'
        updated == 1
        SubqueryBulkItem.findAllByLabel('both')*.name == ['b']
    }

    @Rollback
    void "a subquery without criteria in a bulk statement selects every row"() {
        given:
        seed()
        DetachedCriteria<SubqueryBulkItem> all = new DetachedCriteria<>(SubqueryBulkItem).build {
            projections { property('name') }
        }

        when:
        int updated = new DetachedCriteria<>(SubqueryBulkItem).build { inList('name', all) }.updateAll(label: 'all')

        then:
        updated == 3
    }

    @Rollback
    void "deleteAll with a subquery and a criterion after it deletes only the rows both select"() {
        given:
        seed()
        DetachedCriteria<SubqueryBulkItem> sub = lowAmounts()

        when:
        int deleted = new DetachedCriteria<>(SubqueryBulkItem).build { inList('name', sub); eq('amount', 2) }.deleteAll()

        then:
        deleted == 1
        SubqueryBulkItem.count() == 2
    }
}

@Entity
class SubqueryBulkItem {

    String name
    String label
    Integer amount

    static constraints = {
        label nullable: true
        amount nullable: true
    }
}
