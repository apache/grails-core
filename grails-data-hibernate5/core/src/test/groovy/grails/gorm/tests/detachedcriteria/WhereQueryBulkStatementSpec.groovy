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
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.dao.InvalidDataAccessResourceUsageException
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * The bulk statements of {@code updateAll} and {@code deleteAll} are written by the datastore core for both Hibernate
 * versions, so what they do with a subquery and a function is pinned here against Hibernate 5 as well.
 */
@Issue('https://github.com/apache/grails-core/pull/16816')
class WhereQueryBulkStatementSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(BulkStatementItem)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    private void seed() {
        new BulkStatementItem(name: 'a', amount: 1).save()
        new BulkStatementItem(name: 'b', amount: 2).save()
        new BulkStatementItem(name: 'c', amount: 3).save(flush: true)
    }

    private DetachedCriteria<BulkStatementItem> lowAmounts() {
        new DetachedCriteria<>(BulkStatementItem).build {
            projections { property('name') }
            le('amount', 2)
        }
    }

    private static DetachedCriteria<BulkStatementItem> criteriaOf(Query.Criterion criterion) {
        DetachedCriteria<BulkStatementItem> criteria = new DetachedCriteria<>(BulkStatementItem)
        criteria.add(criterion)
        criteria
    }

    @Rollback
    void "a criterion after a subquery in a bulk statement gets its own parameter"() {
        given:
        seed()
        DetachedCriteria<BulkStatementItem> sub = lowAmounts()

        when:
        int inListUpdated = new DetachedCriteria<>(BulkStatementItem).build { inList('name', sub); eq('amount', 1) }.updateAll(label: 'in')

        then:
        inListUpdated == 1
        BulkStatementItem.findAllByLabel('in')*.name == ['a']

        when:
        int notInUpdated = new DetachedCriteria<>(BulkStatementItem).build { notIn('name', sub); eq('amount', 3) }.updateAll(label: 'notin')

        then:
        notInUpdated == 1
        BulkStatementItem.findAllByLabel('notin')*.name == ['c']
    }

    @Rollback
    void "a function in over a subquery works in a bulk statement, and deleteAll with a function deletes"() {
        given:
        seed()

        when:
        int updated = criteriaOf(new FunctionCallingCriterion('lower', new Query.In('name', lowAmounts()))).updateAll(amount: 50)
        int deleted = BulkStatementItem.where { upper(name) == 'C' }.deleteAll()

        then:
        updated == 2
        deleted == 1
        BulkStatementItem.findAllByAmount(50)*.name.sort() == ['a', 'b']
        BulkStatementItem.count() == 2
    }

    @Rollback
    void "a bulk statement compares a function with a fraction as it is"() {
        given:
        new BulkStatementItem(name: 'abc', amount: 1).save()
        new BulkStatementItem(name: 'abcd', amount: 1).save()
        new BulkStatementItem(name: 'abcde', amount: 1).save(flush: true)

        when:
        int above = criteriaOf(new FunctionCallingCriterion('length', new Query.GreaterThan('name', 3.5))).updateAll(amount: 2)
        int equal = criteriaOf(new FunctionCallingCriterion('length', new Query.Equals('name', 3.5))).updateAll(amount: 3)
        int nearWhole = criteriaOf(new FunctionCallingCriterion('length', new Query.Equals('name', 3.0000000000000000001))).updateAll(amount: 5)
        int inList = criteriaOf(new FunctionCallingCriterion('length', new Query.In('name', [3, 3.5]))).updateAll(amount: 4)
        int deleted = criteriaOf(new FunctionCallingCriterion('length', new Query.LessThan('name', 3.5))).deleteAll()

        then:
        above == 2
        equal == 0
        nearWhole == 0
        inList == 1
        deleted == 1
        BulkStatementItem.findAllByAmount(2)*.name.sort() == ['abcd', 'abcde']
        BulkStatementItem.count() == 2
    }

    @Rollback
    void "an empty list in a function comparison matches nothing in a bulk statement"() {
        given:
        seed()
        def criterion = new FunctionCallingCriterion('upper', new Query.In('name', []))

        expect:
        criteriaOf(criterion).updateAll(label: 'x') == 0
        criteriaOf(criterion).deleteAll() == 0
        BulkStatementItem.count() == 3
    }

    @Rollback
    void "a subquery without projection is compared by the property of the function in a bulk statement"() {
        given:
        seed()
        DetachedCriteria<BulkStatementItem> named = new DetachedCriteria<>(BulkStatementItem).build { eq('name', 'a') }
        def criterion = new FunctionCallingCriterion('lower', new Query.Equals('name', named))

        expect:
        criteriaOf(criterion).updateAll(label: 'x') == 1
        BulkStatementItem.findAllByLabel('x')*.name == ['a']
        criteriaOf(criterion).deleteAll() == 1
    }

    @Rollback
    void "a function name that the where DSL does not support is rejected and writes nothing"() {
        given:
        seed()

        when:
        criteriaOf(new FunctionCallingCriterion('upper(name) = name OR 1=1 OR upper', new Query.Equals('name', 'x'))).updateAll(amount: 99)

        then:
        thrown(InvalidDataAccessResourceUsageException)
        BulkStatementItem.findAllByAmount(99).isEmpty()
    }

    @Rollback
    void "a function over an rlike is rejected in a bulk statement and a function over a one-row subquery deletes through it"() {
        given:
        seed()
        DetachedCriteria<BulkStatementItem> oneRow = new DetachedCriteria<>(BulkStatementItem).build {
            projections { property('name') }
            eq('amount', 1)
        }

        when:
        criteriaOf(new FunctionCallingCriterion('lower', new Query.RLike('name', '^a$'))).deleteAll()

        then:
        thrown(InvalidDataAccessResourceUsageException)

        when:
        int deleted = criteriaOf(new FunctionCallingCriterion('lower', new Query.Equals('name', oneRow))).deleteAll()

        then:
        deleted == 1
        BulkStatementItem.list()*.name.sort() == ['b', 'c']
    }
}

@Entity
class BulkStatementItem {

    String name
    String label
    Integer amount

    static constraints = {
        label nullable: true
        amount nullable: true
    }
}
