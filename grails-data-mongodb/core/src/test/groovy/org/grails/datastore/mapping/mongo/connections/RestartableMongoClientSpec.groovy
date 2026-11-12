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
package org.grails.datastore.mapping.mongo.connections

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

import com.mongodb.client.MongoClient
import spock.lang.Specification

/**
 * The driver connects as soon as a {@code MongoClient} exists, so what matters here is when a driver client is
 * built and when it is closed. The driver clients are mocks: the handle is what is under test, and what it does
 * with a driver client is call it.
 */
class RestartableMongoClientSpec extends Specification {

    void 'nothing is built until the client is used'() {
        given:
        AtomicInteger built = new AtomicInteger()
        MongoClient driver = Mock()
        def client = new RestartableMongoClient('reporting', { built.incrementAndGet(); driver } as Supplier<MongoClient>)

        expect: 'naming it, printing it and comparing it are not uses'
        client.name == 'reporting'
        client.toString().contains('reporting')
        client == client
        client.hashCode() == System.identityHashCode(client)

        and: 'so nothing has connected'
        !client.connected
        built.get() == 0
    }

    void 'the first use builds the driver client, and every later use goes to the same one'() {
        given:
        AtomicInteger built = new AtomicInteger()
        MongoClient driver = Mock()
        def client = new RestartableMongoClient('default', { built.incrementAndGet(); driver } as Supplier<MongoClient>)

        when:
        client.listDatabaseNames()
        client.getDatabase('books')

        then:
        1 * driver.listDatabaseNames()
        1 * driver.getDatabase('books')
        built.get() == 1
        client.connected
    }

    void 'starting builds the driver client straight away, and starting again builds nothing more'() {
        given:
        AtomicInteger built = new AtomicInteger()
        MongoClient driver = Mock()
        def client = new RestartableMongoClient('default', { built.incrementAndGet(); driver } as Supplier<MongoClient>)

        when:
        client.start()

        then:
        client.connected
        built.get() == 1

        when:
        client.start()

        then:
        built.get() == 1
    }

    void 'stopping closes the driver client, and the same handle works again once it is started'() {
        given:
        MongoClient first = Mock()
        MongoClient second = Mock()
        LinkedList<MongoClient> drivers = [first, second] as LinkedList<MongoClient>
        def client = new RestartableMongoClient('reporting', { drivers.poll() } as Supplier<MongoClient>)
        client.start()

        when: 'it is stopped for a checkpoint'
        client.stop()

        then: 'the driver client is closed, which releases its sockets'
        1 * first.close()
        !client.connected

        when: 'something uses it while it is stopped'
        client.getDatabase('books')

        then: 'it is refused rather than reconnected, which would put a socket back before the checkpoint'
        IllegalStateException refused = thrown()
        refused.message.contains('[reporting]')
        refused.message.contains('stopped')
        0 * first._

        when: 'it is started again after the restore'
        client.start()
        client.getDatabase('books')

        then: 'a new driver client serves the same handle'
        1 * second.getDatabase('books')
        0 * first._
        client.connected
    }

    void 'stopping a client that was never used builds nothing, and it is refused until it is started'() {
        given:
        AtomicInteger built = new AtomicInteger()
        MongoClient driver = Mock()
        def client = new RestartableMongoClient('default', { built.incrementAndGet(); driver } as Supplier<MongoClient>)

        when:
        client.stop()

        then:
        built.get() == 0

        when:
        client.listDatabaseNames()

        then:
        thrown(IllegalStateException)
        built.get() == 0

        when:
        client.start()
        client.listDatabaseNames()

        then:
        1 * driver.listDatabaseNames()
        built.get() == 1
    }

    void 'closing is final'() {
        given:
        MongoClient driver = Mock()
        def client = new RestartableMongoClient('reporting', { driver } as Supplier<MongoClient>)
        client.start()

        when:
        client.close()

        then:
        1 * driver.close()
        !client.connected

        when:
        client.getDatabase('books')

        then:
        IllegalStateException used = thrown()
        used.message.contains('[reporting]')
        used.message.contains('closed')

        when:
        client.start()

        then:
        thrown(IllegalStateException)

        when: 'closing again, or stopping, does nothing'
        client.close()
        client.stop()

        then:
        0 * driver._
    }

    void 'a client closed after it was stopped does not close its driver client twice'() {
        given:
        MongoClient driver = Mock()
        def client = new RestartableMongoClient('default', { driver } as Supplier<MongoClient>)
        client.start()

        when:
        client.stop()
        client.close()

        then:
        1 * driver.close()
    }

    void 'a driver client that cannot be built leaves the handle as it was'() {
        given:
        MongoClient driver = Mock()
        LinkedList<Supplier<MongoClient>> attempts = [
                { throw new IllegalArgumentException('no') } as Supplier<MongoClient>,
                { driver } as Supplier<MongoClient>
        ] as LinkedList<Supplier<MongoClient>>
        def client = new RestartableMongoClient('default', { attempts.poll().get() } as Supplier<MongoClient>)

        when:
        client.listDatabaseNames()

        then:
        thrown(IllegalArgumentException)
        !client.connected

        when: 'so the next use tries again'
        client.listDatabaseNames()

        then:
        1 * driver.listDatabaseNames()
    }

    void 'threads using it for the first time at once share one driver client'() {
        given:
        AtomicInteger built = new AtomicInteger()
        MongoClient driver = Mock()
        def client = new RestartableMongoClient('default', { built.incrementAndGet(); driver } as Supplier<MongoClient>)
        int threads = 16
        CountDownLatch go = new CountDownLatch(1)
        ExecutorService executor = Executors.newFixedThreadPool(threads)

        when:
        List<Future<?>> uses = (1..threads).collect {
            executor.submit({
                go.await()
                client.getDatabase('books')
            } as Callable<Object>)
        }
        go.countDown()
        uses*.get(10, TimeUnit.SECONDS)

        then:
        built.get() == 1

        cleanup:
        executor.shutdownNow()
    }

    void 'every method of MongoClient is passed to the driver client'() {
        given: 'a driver client that records what it is asked'
        List<Method> received = []
        MongoClient driver = (MongoClient) Proxy.newProxyInstance(getClass().classLoader, [MongoClient] as Class[],
                { Object proxy, Method method, Object[] args ->
                    received << method
                    null
                } as InvocationHandler)
        def client = new RestartableMongoClient('default', { driver } as Supplier<MongoClient>)

        and: 'closing is covered on its own, since it is the one call that ends the handle'
        List<Method> methods = MongoClient.methods.findAll { Method method ->
            !Modifier.isStatic(method.modifiers) && !method.isDefault() && method.name != 'close'
        }

        expect:
        methods.size() > 30

        and:
        methods.every { Method method ->
            received.clear()
            method.invoke(client, method.parameterTypes.collect { Class<?> type -> argumentFor(type) } as Object[])
            received == [method]
        }
    }

    private static Object argumentFor(Class<?> type) {
        if (!type.primitive) {
            return null
        }
        if (type == long) {
            return 0L
        }
        throw new IllegalArgumentException("No argument for a parameter of type ${type}")
    }
}
