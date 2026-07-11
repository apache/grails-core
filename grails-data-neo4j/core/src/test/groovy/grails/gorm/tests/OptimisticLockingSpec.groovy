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


import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.neo4j.Neo4jTransaction
import org.grails.datastore.mapping.core.OptimisticLockingException
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.SessionHolder
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Transaction
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * @author Burt Beckwith
 */
class OptimisticLockingSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(OptLockNotVersioned, OptLockVersioned)
    }

    void "Test versioning"() {

        given:
        def o = new OptLockVersioned(name: 'locked')

        when:
        o.save flush: true

        then:
        o.version == 0

        when:
        manager.session.clear()
        o = OptLockVersioned.get(o.id)
        o.name = 'Fred'
        o.save flush: true

        then:
        o.version == 1

        when:
        manager.session.clear()
        o = OptLockVersioned.get(o.id)

        then:
        o.name == 'Fred'
        o.version == 1
    }

    void "Test optimistic locking"() {

        given:
        def o = new OptLockVersioned(name: 'locked').save(flush: true)
        manager.session.transaction.commit()
        manager.session.transaction.nativeTransaction.close()
        manager.session.clear()

        def neo4jSession = (org.neo4j.driver.Session) manager.session.getNativeInterface()
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(manager.session.getDatastore());
//        sessionHolder.setTransaction( new Neo4jTransaction(neo4jSession))

        when:
        o = OptLockVersioned.get(o.id)

        then:
        o != null

        when:
        Thread.start {
            OptLockVersioned.withNewSession { s ->
                OptLockVersioned.withTransaction {
                    def reloaded = OptLockVersioned.get(o.id)
                    assert reloaded
                    reloaded.name += ' in new manager.session'
                    reloaded.save(flush: true)
                }
            }
        }.join()
        // The background thread's save is already synchronized via join() above; this sleep is
        // headroom for the embedded Neo4j harness's own write durability, not thread completion.
        // A noisy/loaded CI runner can push that past a couple of seconds - give it more room
        // rather than risk a spurious failure (heisenbug).
        sleep 5000

        o.name += ' in main manager.session'
        def ex
        try {
            o.save(flush: true)
        }
        catch (e) {
            ex = e
            e.printStackTrace()
        }

        manager.session.clear()
        o = OptLockVersioned.get(o.id)

        then:
        ex instanceof OptimisticLockingException
        o.version == 1
        o.name == 'locked in new manager.session'
    }

    void "Test optimistic locking disabled with 'version false'"() {

        given:
        def o = new OptLockNotVersioned(name: 'locked').save(flush: true)
        manager.session.clear()

        when:
        o = OptLockNotVersioned.get(o.id)

        try {
            Thread.start {
                OptLockNotVersioned.withNewSession { s ->
                    def reloaded = OptLockNotVersioned.get(o.id)
                    reloaded.name += ' in new manager.session'
                    reloaded.save(flush: true)
                }
            }.join(2000)
        } catch (InterruptedException e) {
            // ignore
        }
        // Same headroom rationale as "Test optimistic locking" above.
        sleep 5000

        o.name += ' in main manager.session'
        def ex
        try {
            o.save(flush: true)
        }
        catch (e) {
            ex = e
            e.printStackTrace()
        }

        manager.session.clear()
        o = OptLockNotVersioned.get(o.id)

        then:
        ex == null
        o.name == 'locked in main manager.session'
    }
}

@Entity
class OptLockVersioned implements Serializable {
    Long id
    Long version

    String name
}

@Entity
class OptLockNotVersioned implements Serializable {
    Long id
    Long version

    String name

    static mapping = {
        version false
    }
}
