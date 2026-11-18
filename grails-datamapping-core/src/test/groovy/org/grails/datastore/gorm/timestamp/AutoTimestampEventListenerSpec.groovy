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

package org.grails.datastore.gorm.timestamp

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import groovy.transform.InheritConstructors
import spock.lang.Specification

import grails.gorm.annotation.CreatedBy
import grails.gorm.annotation.LastModifiedBy
import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping

class AutoTimestampEventListenerSpec extends Specification {

    private static final Set<String> BOTH_TIMESTAMPS = ['dateCreated', 'lastUpdated'] as Set<String>
    private static final Set<String> DATE_CREATED_ONLY = ['dateCreated'] as Set<String>
    private static final Set<String> LAST_UPDATED_ONLY = ['lastUpdated'] as Set<String>

    TestEventListener listener

    void setup() {
        listener = new TestEventListener(Stub(Datastore) {
            getMappingContext() >> null
        })
    }

    void "timestamps are applied on insert and update by default"() {
        expect:
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
        appliedOnUpdate(Foo).keySet() == LAST_UPDATED_ONLY
    }

    void "withoutLastUpdated disables lastUpdated for all entities only while the closure runs"() {
        given:
        Map<String, Object> fooInsertInside = null
        Map<String, Object> barUpdateInside = null

        when:
        listener.withoutLastUpdated {
            fooInsertInside = appliedOnInsert(Foo)
            barUpdateInside = appliedOnUpdate(Bar)
        }

        then:
        fooInsertInside.keySet() == DATE_CREATED_ONLY
        barUpdateInside.isEmpty()

        and: 'timestamping resumes once the closure completes'
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
        appliedOnUpdate(Bar).keySet() == LAST_UPDATED_ONLY
    }

    void "withoutLastUpdated(Class) only affects the given class"() {
        given:
        Map<String, Object> barInside = null
        Map<String, Object> fooInside = null

        when:
        listener.withoutLastUpdated(Bar) {
            barInside = appliedOnInsert(Bar)
            fooInside = appliedOnInsert(Foo)
        }

        then:
        barInside.keySet() == DATE_CREATED_ONLY
        fooInside.keySet() == BOTH_TIMESTAMPS

        and:
        appliedOnInsert(Bar).keySet() == BOTH_TIMESTAMPS
    }

    void "withoutLastUpdated(List) only affects the given classes"() {
        given:
        Map<String, Object> barInside = null
        Map<String, Object> fooBarInside = null
        Map<String, Object> fooInside = null

        when:
        listener.withoutLastUpdated([Bar, FooBar]) {
            barInside = appliedOnInsert(Bar)
            fooBarInside = appliedOnInsert(FooBar)
            fooInside = appliedOnInsert(Foo)
        }

        then:
        barInside.keySet() == DATE_CREATED_ONLY
        fooBarInside.keySet() == DATE_CREATED_ONLY
        fooInside.keySet() == BOTH_TIMESTAMPS

        and:
        appliedOnInsert(Bar).keySet() == BOTH_TIMESTAMPS
        appliedOnInsert(FooBar).keySet() == BOTH_TIMESTAMPS
    }

    void "withoutDateCreated disables dateCreated for all entities only while the closure runs"() {
        given:
        Map<String, Object> fooInside = null
        Map<String, Object> barInside = null

        when:
        listener.withoutDateCreated {
            fooInside = appliedOnInsert(Foo)
            barInside = appliedOnInsert(Bar)
        }

        then:
        fooInside.keySet() == LAST_UPDATED_ONLY
        barInside.keySet() == LAST_UPDATED_ONLY

        and:
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
    }

    void "withoutDateCreated(Class) only affects the given class"() {
        given:
        Map<String, Object> barInside = null
        Map<String, Object> fooInside = null

        when:
        listener.withoutDateCreated(Bar) {
            barInside = appliedOnInsert(Bar)
            fooInside = appliedOnInsert(Foo)
        }

        then:
        barInside.keySet() == LAST_UPDATED_ONLY
        fooInside.keySet() == BOTH_TIMESTAMPS

        and:
        appliedOnInsert(Bar).keySet() == BOTH_TIMESTAMPS
    }

    void "withoutDateCreated(List) only affects the given classes"() {
        given:
        Map<String, Object> barInside = null
        Map<String, Object> fooBarInside = null
        Map<String, Object> fooInside = null

        when:
        listener.withoutDateCreated([Bar, FooBar]) {
            barInside = appliedOnInsert(Bar)
            fooBarInside = appliedOnInsert(FooBar)
            fooInside = appliedOnInsert(Foo)
        }

        then:
        barInside.keySet() == LAST_UPDATED_ONLY
        fooBarInside.keySet() == LAST_UPDATED_ONLY
        fooInside.keySet() == BOTH_TIMESTAMPS

        and:
        appliedOnInsert(Bar).keySet() == BOTH_TIMESTAMPS
        appliedOnInsert(FooBar).keySet() == BOTH_TIMESTAMPS
    }

    void "withoutTimestamps disables all timestamp handling only while the closure runs"() {
        given:
        Map<String, Object> fooInsertInside = null
        Map<String, Object> fooUpdateInside = null
        Map<String, Object> barInsertInside = null

        when:
        listener.withoutTimestamps {
            fooInsertInside = appliedOnInsert(Foo)
            fooUpdateInside = appliedOnUpdate(Foo)
            barInsertInside = appliedOnInsert(Bar)
        }

        then:
        fooInsertInside.isEmpty()
        fooUpdateInside.isEmpty()
        barInsertInside.isEmpty()

        and:
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
        appliedOnUpdate(Foo).keySet() == LAST_UPDATED_ONLY
    }

    void "withoutTimestamps(Class) only affects the given class"() {
        given:
        Map<String, Object> barInside = null
        Map<String, Object> fooInside = null

        when:
        listener.withoutTimestamps(Bar) {
            barInside = appliedOnInsert(Bar)
            fooInside = appliedOnInsert(Foo)
        }

        then:
        barInside.isEmpty()
        fooInside.keySet() == BOTH_TIMESTAMPS

        and:
        appliedOnInsert(Bar).keySet() == BOTH_TIMESTAMPS
    }

    void "withoutTimestamps(List) only affects the given classes"() {
        given:
        Map<String, Object> barInside = null
        Map<String, Object> fooBarInside = null
        Map<String, Object> fooInside = null

        when:
        listener.withoutTimestamps([Bar, FooBar]) {
            barInside = appliedOnInsert(Bar)
            fooBarInside = appliedOnInsert(FooBar)
            fooInside = appliedOnInsert(Foo)
        }

        then:
        barInside.isEmpty()
        fooBarInside.isEmpty()
        fooInside.keySet() == BOTH_TIMESTAMPS

        and:
        appliedOnInsert(Bar).keySet() == BOTH_TIMESTAMPS
        appliedOnInsert(FooBar).keySet() == BOTH_TIMESTAMPS
    }

    void "nested disabling restores the enclosing scope"() {
        given:
        Map<String, Object> fooInner = null
        Map<String, Object> fooOuter = null
        Map<String, Object> barOuter = null

        when:
        listener.withoutLastUpdated(Foo) {
            listener.withoutTimestamps {
                fooInner = appliedOnInsert(Foo)
            }
            fooOuter = appliedOnInsert(Foo)
            barOuter = appliedOnInsert(Bar)
        }

        then: 'everything is disabled inside the nested closure'
        fooInner.isEmpty()

        and: 'only the outer suppression remains after the nested closure completes'
        fooOuter.keySet() == DATE_CREATED_ONLY
        barOuter.keySet() == BOTH_TIMESTAMPS

        and: 'all timestamping resumes after the outer closure completes'
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
    }

    void "overlapping scopes disabling the same class restore the enclosing scope"() {
        given:
        Map<String, Object> fooInner = null
        Map<String, Object> barInner = null
        Map<String, Object> fooBetween = null
        Map<String, Object> barBetween = null

        when:
        listener.withoutDateCreated(Foo) {
            listener.withoutDateCreated([Foo, Bar]) {
                fooInner = appliedOnInsert(Foo)
                barInner = appliedOnInsert(Bar)
            }
            fooBetween = appliedOnInsert(Foo)
            barBetween = appliedOnInsert(Bar)
        }

        then: 'both classes are disabled inside the inner scope'
        fooInner.keySet() == LAST_UPDATED_ONLY
        barInner.keySet() == LAST_UPDATED_ONLY

        and: 'Foo stays disabled in the outer scope after the inner scope exits'
        fooBetween.keySet() == LAST_UPDATED_ONLY
        barBetween.keySet() == BOTH_TIMESTAMPS

        and: 'everything is restored after the outer scope exits'
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
    }

    void "no suppression remains when registering a class list fails part way"() {
        when: 'the second element blows up after the first has already been registered'
        listener.withoutTimestamps([Foo, null]) {
            throw new IllegalStateException('never reached')
        }

        then:
        thrown(NullPointerException)
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
        appliedOnUpdate(Foo).keySet() == LAST_UPDATED_ONLY
    }

    void "timestamp processing is restored when the closure throws"() {
        when:
        listener.withoutTimestamps {
            throw new IllegalStateException('failure inside withoutTimestamps')
        }

        then:
        thrown(IllegalStateException)
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
        appliedOnUpdate(Foo).keySet() == LAST_UPDATED_ONLY
    }

    void "disabling timestamps on one thread does not affect other threads"() {
        given:
        CountDownLatch entered = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        Thread worker = new Thread({
            listener.withoutTimestamps {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        })

        when: 'this thread persists while the worker holds an open withoutTimestamps window'
        worker.start()
        boolean workerEntered = entered.await(10, TimeUnit.SECONDS)
        Map<String, Object> insertApplied = appliedOnInsert(Foo)
        Map<String, Object> updateApplied = appliedOnUpdate(Foo)
        release.countDown()
        worker.join(10000)

        then:
        workerEntered
        insertApplied.keySet() == BOTH_TIMESTAMPS
        updateApplied.keySet() == LAST_UPDATED_ONLY
    }

    void "overlapping windows on different threads restore independently"() {
        given:
        CountDownLatch aEntered = new CountDownLatch(1)
        CountDownLatch bEntered = new CountDownLatch(1)
        CountDownLatch aExited = new CountDownLatch(1)
        Map<String, Object> insideB = null
        Map<String, Object> insideBAfterAExited = null
        Map<String, Object> afterA = null

        Thread threadA = new Thread({
            listener.withoutTimestamps(Foo) {
                aEntered.countDown()
                bEntered.await(10, TimeUnit.SECONDS)
            }
            afterA = appliedOnInsert(Foo)
            aExited.countDown()
        })
        Thread threadB = new Thread({
            aEntered.await(10, TimeUnit.SECONDS)
            listener.withoutTimestamps(Foo) {
                insideB = appliedOnInsert(Foo)
                bEntered.countDown()
                aExited.await(10, TimeUnit.SECONDS)
                insideBAfterAExited = appliedOnInsert(Foo)
            }
        })

        when:
        threadA.start()
        threadB.start()
        threadA.join(15000)
        threadB.join(15000)

        then: 'thread B stays disabled for its whole window, even after thread A restores'
        insideB.isEmpty()
        insideBAfterAExited.isEmpty()

        and: 'thread A is re-enabled as soon as its own window closes'
        afterA.keySet() == BOTH_TIMESTAMPS

        and: 'the test thread was never affected'
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
    }

    void "suppression stays thread local under concurrent use"() {
        given:
        int threadCount = 8
        int iterations = 25
        ExecutorService pool = Executors.newFixedThreadPool(threadCount)
        CountDownLatch start = new CountDownLatch(1)

        when:
        List<Future<Boolean>> outcomes = (1..threadCount).collect { int n ->
            pool.submit({
                start.await(10, TimeUnit.SECONDS)
                boolean consistent = true
                iterations.times {
                    if (n % 2 == 0) {
                        listener.withoutTimestamps {
                            consistent &= appliedOnInsert(Foo).isEmpty()
                        }
                        consistent &= appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
                    } else {
                        consistent &= appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
                        consistent &= appliedOnUpdate(Foo).keySet() == LAST_UPDATED_ONLY
                    }
                }
                consistent
            } as Callable<Boolean>)
        }
        start.countDown()
        List<Boolean> results = outcomes.collect { it.get(30, TimeUnit.SECONDS) }

        then:
        results.every { it }

        cleanup:
        pool.shutdownNow()
    }

    void "captured suppression can be applied on another thread"() {
        given:
        AutoTimestampEventListener.TimestampSuppression suppression = null
        listener.withoutTimestamps(Foo) {
            suppression = listener.captureTimestampSuppression()
        }
        Map<String, Object> fooInside = null
        Map<String, Object> barInside = null
        Map<String, Object> fooAfter = null

        when: 'a worker thread applies the state captured inside the window'
        Thread worker = new Thread({
            listener.withTimestampSuppression(suppression) {
                fooInside = appliedOnInsert(Foo)
                barInside = appliedOnInsert(Bar)
            }
            fooAfter = appliedOnInsert(Foo)
        })
        worker.start()
        worker.join(10000)

        then: 'the captured Foo suppression applies on the worker thread'
        fooInside.isEmpty()
        barInside.keySet() == BOTH_TIMESTAMPS

        and: 'the worker thread is restored once the block completes'
        fooAfter.keySet() == BOTH_TIMESTAMPS

        and: 'the capturing thread is unaffected'
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
    }

    void "captured suppression outlives the originating window and is reusable"() {
        given:
        AutoTimestampEventListener.TimestampSuppression suppression = null
        listener.withoutLastUpdated {
            suppression = listener.captureTimestampSuppression()
        }
        Map<String, Object> firstUse = null
        Map<String, Object> secondUse = null

        when:
        listener.withTimestampSuppression(suppression) {
            firstUse = appliedOnInsert(Foo)
        }
        listener.withTimestampSuppression(suppression) {
            secondUse = appliedOnInsert(Foo)
        }

        then:
        firstUse.keySet() == DATE_CREATED_ONLY
        secondUse.keySet() == DATE_CREATED_ONLY

        and:
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
    }

    void "an empty captured suppression temporarily replaces the thread's own suppression"() {
        given:
        AutoTimestampEventListener.TimestampSuppression noSuppression = listener.captureTimestampSuppression()
        Map<String, Object> insideCapture = null
        Map<String, Object> afterCapture = null

        when:
        listener.withoutTimestamps {
            listener.withTimestampSuppression(noSuppression) {
                insideCapture = appliedOnInsert(Foo)
            }
            afterCapture = appliedOnInsert(Foo)
        }

        then: 'the empty snapshot re-enables timestamping while installed'
        insideCapture.keySet() == BOTH_TIMESTAMPS

        and: 'the enclosing window is restored once the snapshot block completes'
        afterCapture.isEmpty()
    }

    void "the previous suppression state is restored when the propagated runnable throws"() {
        given:
        AutoTimestampEventListener.TimestampSuppression suppression = null
        listener.withoutTimestamps {
            suppression = listener.captureTimestampSuppression()
        }

        when:
        listener.withTimestampSuppression(suppression) {
            throw new IllegalStateException('failure inside withTimestampSuppression')
        }

        then:
        thrown(IllegalStateException)
        appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
        appliedOnUpdate(Foo).keySet() == LAST_UPDATED_ONLY
    }

    void "nested scopes inside an applied suppression do not corrupt the snapshot"() {
        given:
        AutoTimestampEventListener.TimestampSuppression suppression = null
        listener.withoutDateCreated(Foo) {
            suppression = listener.captureTimestampSuppression()
        }
        Map<String, Object> barNested = null
        Map<String, Object> secondUseFoo = null
        Map<String, Object> secondUseBar = null

        when: 'the first application opens and closes a nested scope for another class'
        listener.withTimestampSuppression(suppression) {
            listener.withoutDateCreated(Bar) {
                barNested = appliedOnInsert(Bar)
            }
        }
        listener.withTimestampSuppression(suppression) {
            secondUseFoo = appliedOnInsert(Foo)
            secondUseBar = appliedOnInsert(Bar)
        }

        then:
        barNested.keySet() == LAST_UPDATED_ONLY

        and: 'the second application still reflects only the captured state'
        secondUseFoo.keySet() == LAST_UPDATED_ONLY
        secondUseBar.keySet() == BOTH_TIMESTAMPS
    }

    void "one captured suppression can be applied concurrently on multiple threads"() {
        given:
        AutoTimestampEventListener.TimestampSuppression suppression = null
        listener.withoutTimestamps(Foo) {
            suppression = listener.captureTimestampSuppression()
        }
        int threadCount = 8
        ExecutorService pool = Executors.newFixedThreadPool(threadCount)
        CountDownLatch start = new CountDownLatch(1)

        when:
        List<Future<Boolean>> outcomes = (1..threadCount).collect {
            pool.submit({
                start.await(10, TimeUnit.SECONDS)
                boolean consistent = true
                25.times {
                    listener.withTimestampSuppression(suppression) {
                        consistent &= appliedOnInsert(Foo).isEmpty()
                        listener.withoutTimestamps(Bar) {
                            consistent &= appliedOnInsert(Bar).isEmpty()
                        }
                        consistent &= appliedOnInsert(Bar).keySet() == BOTH_TIMESTAMPS
                    }
                    consistent &= appliedOnInsert(Foo).keySet() == BOTH_TIMESTAMPS
                }
                consistent
            } as Callable<Boolean>)
        }
        start.countDown()
        List<Boolean> results = outcomes.collect { it.get(30, TimeUnit.SECONDS) }

        then:
        results.every { it }

        cleanup:
        pool.shutdownNow()
    }

    void "a snapshot captured inside an empty class-list window suppresses nothing"() {
        given:
        AutoTimestampEventListener.TimestampSuppression suppression = null
        Map<String, Object> insideWindow = null
        Map<String, Object> applied = null

        when:
        listener.withoutTimestamps([]) {
            insideWindow = appliedOnInsert(Foo)
            suppression = listener.captureTimestampSuppression()
        }
        listener.withTimestampSuppression(suppression) {
            applied = appliedOnInsert(Foo)
        }

        then: 'an empty class list disables nothing inside the window'
        insideWindow.keySet() == BOTH_TIMESTAMPS

        and: 'the captured snapshot is empty and suppresses nothing when applied'
        applied.keySet() == BOTH_TIMESTAMPS
    }

    void "auditor properties registered via persistentEntityAdded are populated on insert and update"() {
        given:
        listener.setAuditorAware([getCurrentAuditor: { -> Optional.of('testUser') }] as AuditorAware)
        listener.persistentEntityAdded(auditedEntity())

        when:
        Map<String, Object> inserted = appliedOnInsert(Audited)
        Map<String, Object> updated = appliedOnUpdate(Audited)

        then:
        inserted == [createdBy: 'testUser', lastModifiedBy: 'testUser']
        updated == [lastModifiedBy: 'testUser']
    }

    private PersistentEntity auditedEntity() {
        PersistentEntity entity = null
        PersistentProperty createdBy = auditedProperty('createdBy') { -> entity }
        PersistentProperty lastModifiedBy = auditedProperty('lastModifiedBy') { -> entity }
        ClassMapping classMapping = [getMappedForm: { -> null }] as ClassMapping
        entity = [
                getName                : { -> Audited.name },
                isInitialized          : { -> true },
                getMapping             : { -> classMapping },
                getJavaClass           : { -> Audited },
                getPersistentProperties: { -> [createdBy, lastModifiedBy] }
        ] as PersistentEntity
        entity
    }

    private static PersistentProperty auditedProperty(String name, Closure<PersistentEntity> owner) {
        Property mappedForm = new Property()
        PropertyMapping mapping = [getMappedForm: { -> mappedForm }] as PropertyMapping
        [
                getName   : { -> name },
                getType   : { -> String },
                getMapping: { -> mapping },
                getOwner  : { -> owner.call() }
        ] as PersistentProperty
    }

    private Map<String, Object> appliedOnInsert(Class clazz) {
        Map<String, Object> applied = new ConcurrentHashMap<>()
        listener.beforeInsert(entityFor(clazz), recordingAccess(applied))
        applied
    }

    private Map<String, Object> appliedOnUpdate(Class clazz) {
        Map<String, Object> applied = new ConcurrentHashMap<>()
        listener.beforeUpdate(entityFor(clazz), recordingAccess(applied))
        applied
    }

    private static PersistentEntity entityFor(Class clazz) {
        [getName: { -> clazz.name }] as PersistentEntity
    }

    private static EntityAccess recordingAccess(Map<String, Object> applied) {
        [
                getPropertyValue: { String name -> null },
                getPropertyType : { String name -> Date },
                setProperty     : { String name, Object value -> applied.put(name, value) }
        ] as EntityAccess
    }
}

class Foo {

}

class Audited {

    @CreatedBy
    String createdBy

    @LastModifiedBy
    String lastModifiedBy

}

class Bar {

}

class FooBar {

}

@InheritConstructors
class TestEventListener extends AutoTimestampEventListener {

    protected void initForMappingContext(MappingContext mappingContext) {
        [Foo, Bar, FooBar].each {
            entitiesWithLastUpdated.put(it.getName(), Optional.of(['lastUpdated'] as Set<String>))
            entitiesWithDateCreated.put(it.getName(), Optional.of(['dateCreated'] as Set<String>))
        }
    }
}
