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
package grails.gorm.tests.hibernatequery

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import spock.lang.Specification

import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.query.PredicateGenerator

/**
 * The handler providers are discovered on first use. A query that arrives while another thread discovers them must
 * wait for the discovery instead of deciding, with the handlers still unregistered, that an order has no handler.
 * {@link OrderHandlerDiscoveryProvider} is on the test classpath through {@code META-INF/services} and blocks inside
 * its discovery while the spec holds its gate.
 */
class OrderHandlerDiscoverySpec extends Specification {

    void cleanup() {
        OrderHandlerDiscoveryProvider.gate.countDown()
        OrderHandlerDiscoveryProvider.gate = new CountDownLatch(0)
        PredicateGenerator.clearCustomOrderHandlers()
    }

    void "a query that arrives while the providers load waits for them and gets the discovered order handler"() {
        given: "discovery has not run, and the provider blocks until the spec releases it"
        PredicateGenerator.clearCustomOrderHandlers()
        OrderHandlerDiscoveryProvider.gate = new CountDownLatch(1)
        OrderHandlerDiscoveryProvider.entered = new CountDownLatch(1)
        ExecutorService executor = Executors.newFixedThreadPool(2)
        Callable<PredicateGenerator.OrderHandler> lookup = { PredicateGenerator.orderHandler(OrderHandlerDiscoveryOrder) }

        when: "one thread starts the discovery and blocks inside the provider, and a second asks for the handler meanwhile"
        Future<PredicateGenerator.OrderHandler> first = executor.submit(lookup)
        boolean providerEntered = OrderHandlerDiscoveryProvider.entered.await(10, TimeUnit.SECONDS)
        Future<PredicateGenerator.OrderHandler> second = executor.submit(lookup)
        boolean secondAnsweredDuringDiscovery = true
        try {
            second.get(500, TimeUnit.MILLISECONDS)
        } catch (TimeoutException ignored) {
            secondAnsweredDuringDiscovery = false
        }
        OrderHandlerDiscoveryProvider.gate.countDown()

        then: "the second caller waited for the discovery, and both callers get the discovered handler"
        providerEntered
        !secondAnsweredDuringDiscovery
        first.get(10, TimeUnit.SECONDS) != null
        second.get(10, TimeUnit.SECONDS) != null

        cleanup:
        executor.shutdownNow()
    }

    void "a handler registered in code is kept over the discovered one"() {
        given:
        PredicateGenerator.clearCustomOrderHandlers()
        PredicateGenerator.OrderHandler registered = { query, root, cb, order, context -> null }
        PredicateGenerator.registerOrderHandler(OrderHandlerDiscoveryOrder, registered)

        expect:
        PredicateGenerator.orderHandler(OrderHandlerDiscoveryOrder).is(registered)
    }

}

class OrderHandlerDiscoveryOrder extends Query.Order {

    OrderHandlerDiscoveryOrder() {
        super('discovered')
    }

}

class OrderHandlerDiscoveryProvider implements PredicateGenerator.OrderHandlerProvider {

    static volatile CountDownLatch gate = new CountDownLatch(0)
    static volatile CountDownLatch entered = new CountDownLatch(0)

    @Override
    Class<? extends Query.Order> orderType() {
        OrderHandlerDiscoveryOrder
    }

    @Override
    PredicateGenerator.OrderHandler orderHandler() {
        entered.countDown()
        try {
            gate.await()
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new IllegalStateException('Interrupted while the test held the provider', e)
        }
        return { query, root, cb, order, context -> null } as PredicateGenerator.OrderHandler
    }

}
