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
package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GormRegistryConcurrencySpec extends Specification {

    void setup() {
        GormRegistry.reset()
    }

    void cleanup() {
        GormRegistry.reset()
    }

    @Timeout(15)
    void "registry hot-paths perform without lock contention under high concurrency"() {
        given:
        def registry = GormRegistry.instance
        int numThreads = 10
        int iterationsPerThread = 20000
        ExecutorService executor = Executors.newFixedThreadPool(numThreads)
        CountDownLatch startLatch = new CountDownLatch(1)
        CountDownLatch endLatch = new CountDownLatch(numThreads)
        AtomicInteger errorCount = new AtomicInteger(0)
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>()

        // Setup some dummy state to query
        def context = Stub(MappingContext) {
            getMappingFactory() >> null
        }
        def defaultDatastore = Stub(Datastore) {
            getMappingContext() >> context
        }
        def secondaryDatastore = Stub(Datastore) {
            getMappingContext() >> context
        }
        def resolver = Stub(DatastoreResolver) {
            resolve() >> defaultDatastore
        }
        
        registry.registerDatastore(ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerDatastore("secondary", secondaryDatastore)
        registry.registerEntityDatastore(ConcurrentEntity.name, ConnectionSource.DEFAULT, defaultDatastore)
        registry.registerEntityDatastore(ConcurrentEntity.name, "secondary", secondaryDatastore)
        
        def staticApi = new GormStaticApi(ConcurrentEntity, context, [], resolver, ConnectionSource.DEFAULT, registry)
        def instanceApi = new GormInstanceApi(ConcurrentEntity, context, resolver, registry)
        def validationApi = new GormValidationApi(ConcurrentEntity, context, resolver, registry)
        
        registry.registerApi(ConcurrentEntity.name, staticApi, instanceApi, validationApi)

        when: "multiple threads access registry hot paths simultaneously"
        for (int i = 0; i < numThreads; i++) {
            executor.submit {
                try {
                    startLatch.await()
                    for (int j = 0; j < iterationsPerThread; j++) {
                        // High contention normalization paths
                        def normClass = registry.normalizeEntityKeyFromClass(ConcurrentEntity)
                        def normName = registry.normalizeEntityKey(" ${ConcurrentEntity.name} ")
                        def normQual = registry.normalizeQualifier(" secondary ")
                        
                        assert normClass == ConcurrentEntity.name
                        assert normName == ConcurrentEntity.name
                        assert normQual == "secondary"

                        // Datastore lookup paths
                        def ds1 = registry.getDatastore(ConcurrentEntity.name, ConnectionSource.DEFAULT)
                        def ds2 = registry.getDatastore(ConcurrentEntity.name, "secondary")
                        
                        assert ds1 == defaultDatastore
                        assert ds2 == secondaryDatastore

                        // API lookup paths
                        def sapi = registry.getStaticApi(ConcurrentEntity.name)
                        def iapi = registry.getInstanceApi(ConcurrentEntity.name)
                        def vapi = registry.getValidationApi(ConcurrentEntity.name)
                        
                        assert sapi != null
                        assert iapi != null
                        assert vapi != null
                    }
                } catch (Throwable e) {
                    failures << e
                    errorCount.incrementAndGet()
                } finally {
                    endLatch.countDown()
                }
            }
        }

        startLatch.countDown() // Release all threads
        // A generous budget: on a quiescent machine this completes in low single-digit seconds,
        // but shared/noisy CI runners (e.g. macos-latest under concurrent load) need headroom so
        // the assertion measures a genuine hang rather than ordinary scheduling jitter.
        boolean completedInTime = endLatch.await(10, TimeUnit.SECONDS)
        if (!completedInTime) {
            executor.shutdownNow()
        } else {
            executor.shutdown()
        }

        then: "all threads completed within the timeout and no errors occurred"
        completedInTime
        errorCount.get() == 0
        failures.isEmpty()
    }

    static class ConcurrentEntity {}
}
