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
import org.hibernate.SessionFactory
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Issue

/**
 * A criteria call that opens its own session closes it whether the call returns or throws, and leaves the session
 * of a caller that it only participates in as it was.
 */
@Issue('https://github.com/apache/grails-core/issues/16808')
class CriteriaSessionCleanupSpec extends HibernateGormDatastoreSpec {

    private SessionHolder requestHolder

    void setupSpec() {
        manager.registerDomainClasses(CscPerson)
    }

    void setup() {
        new CscPerson(name: 'Ann', age: 30).save()
        new CscPerson(name: 'Bob', age: 40).save(flush: true)
        manager.session.clear()
    }

    private SessionFactory sessionFactory() {
        manager.hibernateDatastore.sessionFactory
    }

    private void withNoSessionBound(Closure<?> block) {
        requestHolder = (SessionHolder) TransactionSynchronizationManager.unbindResource(sessionFactory())
        try {
            block.call()
        } finally {
            if (TransactionSynchronizationManager.hasResource(sessionFactory())) {
                TransactionSynchronizationManager.unbindResource(sessionFactory())
            }
            TransactionSynchronizationManager.bindResource(sessionFactory(), requestHolder)
        }
    }

    void "a get that finds more than one row closes the session it opened"() {
        expect:
        withNoSessionBound {
            try {
                CscPerson.createCriteria().get { gt('age', 18) }
            } catch (Exception expected) {
                // more than one row
            }
            assert !TransactionSynchronizationManager.hasResource(sessionFactory())
        }
    }

    void "a get that finds more than one row says so"() {
        when:
        withNoSessionBound {
            CscPerson.createCriteria().get { gt('age', 18) }
        }

        then:
        thrown(Exception)
    }

    void "a list with a sort that names no property closes the session it opened"() {
        expect:
        withNoSessionBound {
            try {
                CscPerson.createCriteria().list(max: 10, sort: 'noSuchProperty') { gt('age', 18) }
            } catch (Exception expected) {
                // the sort is refused
            }
            assert !TransactionSynchronizationManager.hasResource(sessionFactory())
        }
    }

    void "a criteria closure that throws closes the session it opened"() {
        expect:
        withNoSessionBound {
            try {
                CscPerson.createCriteria().list { throw new IllegalStateException('from the closure') }
            } catch (IllegalStateException expected) {
                // thrown by the closure
            }
            assert !TransactionSynchronizationManager.hasResource(sessionFactory())
        }
    }

    void "a call that returns closes the session it opened, as before"() {
        expect:
        withNoSessionBound {
            assert CscPerson.createCriteria().list { gt('age', 35) }*.name == ['Bob']
            assert !TransactionSynchronizationManager.hasResource(sessionFactory())
        }
    }

    void "a get that throws leaves the session of the caller bound and open"() {
        given:
        SessionHolder bound = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory())

        when:
        CscPerson.createCriteria().get { gt('age', 18) }

        then:
        thrown(Exception)
        TransactionSynchronizationManager.getResource(sessionFactory()).is(bound)
        bound.session.isOpen()
        CscPerson.count() == 2
    }
}

@Entity
class CscPerson {
    String name
    Integer age
}
