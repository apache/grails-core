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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.support.hibernate7.SessionHolder
import org.hibernate.Session
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.resource.jdbc.spi.LogicalConnectionImplementor
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification

@Issue('https://github.com/apache/grails-core/issues/16571')
class WithNewSessionOtherDataSourceSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    Session requestSession

    void setup() {
        datastore = new HibernateDatastore([
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url': 'jdbc:h2:mem:otherDsDefaultDB;LOCK_TIMEOUT=10000',
            'dataSources.other': [url: 'jdbc:h2:mem:otherDsOtherDB;LOCK_TIMEOUT=10000', dbCreate: 'create-drop'],
        ], OtherDsAuthor, OtherDsBook, OtherDsItem)
        OtherDsAuthor.withTransaction {
            OtherDsAuthor author = new OtherDsAuthor(name: 'Stephen King').save(flush: true)
            new OtherDsBook(title: 'The Stand', author: author).save(flush: true)
        }
        requestSession = datastore.sessionFactory.openSession()
        TransactionSynchronizationManager.bindResource(datastore.sessionFactory, new SessionHolder(requestSession))
    }

    void cleanup() {
        if (TransactionSynchronizationManager.hasResource(datastore.sessionFactory)) {
            TransactionSynchronizationManager.unbindResource(datastore.sessionFactory)
        }
        requestSession?.close()
    }

    private OtherDsBook bookOfADisconnectedRequestSession() {
        OtherDsBook book = OtherDsBook.withTransaction { OtherDsBook.first() }
        LogicalConnectionImplementor logicalConnection = ((SessionImplementor) requestSession).jdbcCoordinator.logicalConnection
        if (logicalConnection.isOpen() && logicalConnection.isPhysicallyConnected()) {
            logicalConnection.manualDisconnect()
        }
        book
    }

    void "a transaction of another data source around withNewSession leaves the request session usable"() {
        given:
        OtherDsBook book = bookOfADisconnectedRequestSession()

        when:
        OtherDsItem.withTransaction {
            OtherDsItem.count()
            OtherDsBook.withNewSession {
                OtherDsBook.withTransaction {
                    assert book.author.name == 'Stephen King'
                    new OtherDsAuthor(name: 'Tenar').save(flush: true)
                }
            }
        }

        then:
        requestSession.createQuery('from OtherDsAuthor', OtherDsAuthor).list().size() == 2
    }

    void "a lazy load in withNewSession without a transaction leaves the request session usable inside a SUPPORTS scope"() {
        given:
        OtherDsBook book = bookOfADisconnectedRequestSession()

        when:
        int seen = OtherDsBook.withTransaction(propagationBehavior: TransactionDefinition.PROPAGATION_SUPPORTS, readOnly: true) {
            OtherDsBook.withNewSession {
                assert book.author.name == 'Stephen King'
            }
            requestSession.createQuery('from OtherDsAuthor', OtherDsAuthor).list().size()
        }

        then:
        seen == 1
    }

    void "a lazy load of the request session in the transaction of a nested withNewSession leaves it usable inside the outer scope"() {
        given:
        OtherDsBook book = bookOfADisconnectedRequestSession()

        when:
        int inScope = OtherDsBook.withNewSession {
            OtherDsBook.withNewSession {
                OtherDsBook.withTransaction {
                    assert book.author.name == 'Stephen King'
                }
            }
            requestSession.createQuery('from OtherDsAuthor', OtherDsAuthor).list().size()
        }

        then:
        inScope == 1
    }
}

@Entity
class OtherDsAuthor {
    String name
}

@Entity
class OtherDsBook {
    String title
    OtherDsAuthor author

    static mapping = {
        author lazy: true
    }
}

@Entity
class OtherDsItem {
    String name

    static mapping = {
        datasource 'other'
    }
}
