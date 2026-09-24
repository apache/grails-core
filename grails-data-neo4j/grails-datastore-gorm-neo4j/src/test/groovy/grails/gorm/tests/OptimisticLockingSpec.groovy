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

import java.util.concurrent.atomic.AtomicReference

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.neo4j.Neo4jTransaction
import org.grails.datastore.mapping.core.OptimisticLockingException
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.SessionHolder
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Transaction
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.util.concurrent.PollingConditions

/**
 * @author Burt Beckwith
 */
class OptimisticLockingSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [OptLockNotVersioned, OptLockVersioned]
    }

    void "Test versioning"() {

        given:
        def o = new OptLockVersioned(name: 'locked')

        when:
        o.save flush: true

        then:
        o.version == 0

        when:
        session.clear()
        o = OptLockVersioned.get(o.id)
        o.name = 'Fred'
        o.save flush: true

        then:
        o.version == 1

        when:
        session.clear()
        o = OptLockVersioned.get(o.id)

        then:
        o.name == 'Fred'
        o.version == 1
    }

    void "Test optimistic locking"() {

        given:
        def o = new OptLockVersioned(name: 'locked').save(flush: true)
        session.transaction.commit()
        session.transaction.nativeTransaction.close()
        session.clear()

        def neo4jSession = (org.neo4j.driver.Session) session.getNativeInterface()
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(session.getDatastore());
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
                    reloaded.name += ' in new session'
                    reloaded.save(flush: true)
                }
            }
        }.join()
        // The background thread's save is already synchronized via join() above; poll (rather
        // than sleep a fixed duration) until an independent session observes it, since the
        // embedded Neo4j harness's own write-durability/visibility lag can outlast any fixed
        // guess (heisenbug) - a noisy/loaded CI runner has been seen pushing past 2s, and this
        // adapts instead of gambling on a bigger number.
        new PollingConditions(timeout: 10, initialDelay: 0.1, delay: 0.2).eventually {
            def observedName
            OptLockVersioned.withNewSession { s ->
                observedName = OptLockVersioned.get(o.id).name
            }
            assert observedName == 'locked in new session'
        }

        o.name += ' in main session'
        def ex
        try {
            o.save(flush: true)
        }
        catch (e) {
            ex = e
            e.printStackTrace()
        }

        session.clear()
        o = OptLockVersioned.get(o.id)

        then:
        ex instanceof OptimisticLockingException
        o.version == 1
        o.name == 'locked in new session'
    }

    void "Test optimistic locking disabled with 'version false'"() {

        given:
        def o = new OptLockNotVersioned(name: 'locked').save(flush: true)
        session.transaction.commit()
        session.transaction.nativeTransaction.close()
        session.clear()

        when:
        o = OptLockNotVersioned.get(o.id)

        def failure = new AtomicReference<Throwable>()
        def backgroundUpdate = Thread.start {
            try {
                OptLockNotVersioned.withNewSession { s ->
                    OptLockNotVersioned.withTransaction {
                        def reloaded = OptLockNotVersioned.get(o.id)
                        assert reloaded
                        reloaded.name += ' in new session'
                        reloaded.save(flush: true)
                    }
                }
            } catch (Throwable t) {
                failure.set(t)
            }
        }
        backgroundUpdate.join()
        // A thread that dies from an uncaught exception is also no longer alive, so join()
        // alone can't distinguish a completed write from a crashed one; assert the captured
        // outcome explicitly.
        assert failure.get() == null
        // Same cross-session visibility-lag rationale as "Test optimistic locking" above.
        def nameAfterBackgroundUpdate
        new PollingConditions(timeout: 10, initialDelay: 0.1, delay: 0.2).eventually {
            OptLockNotVersioned.withNewSession { s ->
                nameAfterBackgroundUpdate = OptLockNotVersioned.get(o.id).name
            }
            assert nameAfterBackgroundUpdate == 'locked in new session'
        }

        o.name += ' in main session'
        def ex
        try {
            o.save(flush: true)
        }
        catch (e) {
            ex = e
            e.printStackTrace()
        }

        session.clear()
        o = OptLockNotVersioned.get(o.id)

        then:
        // Proves the background write actually landed before the main session's blind
        // overwrite below; without it, these assertions would pass even if the background
        // thread never ran.
        nameAfterBackgroundUpdate == 'locked in new session'
        ex == null
        o.name == 'locked in main session'
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
