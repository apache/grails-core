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

import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.OptimisticLockingException
import spock.util.concurrent.PollingConditions

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
        manager.session.clear()

        when:
        o = OptLockVersioned.get(o.id)

        then:
        o != null

        when:
        def failure = new AtomicReference<Throwable>()
        Thread.start {
            try {
                OptLockVersioned.withNewSession {
                    OptLockVersioned.withTransaction {
                        def reloaded = OptLockVersioned.get(o.id)
                        assert reloaded
                        reloaded.name += ' in new session'
                        reloaded.save(flush: true)
                    }
                }
            } catch (Throwable t) {
                failure.set(t)
            }
        }.join()
        // A thread that died from an exception has also finished, so join() alone cannot tell a
        // completed write from a crashed one; surface the captured outcome with its stack trace.
        if (failure.get()) {
            throw failure.get()
        }
        // Poll until an independent session sees the committed write rather than sleeping a
        // fixed budget, so a healthy run pays nothing and a broken one fails with the observed value.
        new PollingConditions(timeout: 10, initialDelay: 0.1, delay: 0.2).eventually {
            def observedName
            OptLockVersioned.withNewSession {
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

        manager.session.clear()
        o = OptLockVersioned.get(o.id)

        then:
        ex instanceof OptimisticLockingException
        o.version == 1
        o.name == 'locked in new session'
    }

    void "Test optimistic locking disabled with 'version false'"() {

        given:
        def o = new OptLockNotVersioned(name: 'locked').save(flush: true)
        manager.session.transaction.commit()
        manager.session.clear()

        when:
        o = OptLockNotVersioned.get(o.id)

        def failure = new AtomicReference<Throwable>()
        def backgroundUpdate = Thread.start {
            try {
                OptLockNotVersioned.withNewSession {
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
        // Same completion check and polling rationale as "Test optimistic locking" above.
        if (failure.get()) {
            throw failure.get()
        }
        def nameAfterBackgroundUpdate
        new PollingConditions(timeout: 10, initialDelay: 0.1, delay: 0.2).eventually {
            OptLockNotVersioned.withNewSession {
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

        manager.session.clear()
        o = OptLockNotVersioned.get(o.id)

        then:
        // Proves the background write actually landed before the main session's blind
        // overwrite; without it, the two assertions below would pass even if the background
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
