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
package grails.async

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

import org.grails.async.factory.PromiseFactoryBuilder
import org.grails.async.factory.future.CompletableFuturePromise
import org.grails.async.factory.future.CompletableFuturePromiseFactory
import spock.lang.Specification

class CompletableFuturePromiseFactorySpec extends Specification {

    private final Executor sameThreadExecutor = { Runnable task -> task.run() }
    private CompletableFuturePromiseFactory factory

    void setup() {
        factory = new CompletableFuturePromiseFactory(sameThreadExecutor)
        Promises.promiseFactory = factory
    }

    void cleanup() {
        Promises.promiseFactory = null
    }

    void 'uses a supplied executor and exposes JDK completion-stage behavior'() {
        when:
        Promise<Integer> promise = factory.createPromise { 21 * 2 }

        then:
        promise instanceof CompletableFuturePromise
        ((CompletableFuturePromise<Integer>) promise).thenApply { it + 1 }.get() == 43
    }

    void 'preserves the supplied executor across asynchronous completion stages'() {
        given:
        AtomicInteger executions = new AtomicInteger()
        Executor executor = { Runnable task ->
            executions.incrementAndGet()
            task.run()
        }
        CompletableFuturePromiseFactory executorFactory = new CompletableFuturePromiseFactory(executor)

        when:
        CompletableFuturePromise<Integer> promise = (CompletableFuturePromise<Integer>) executorFactory.createPromise { 42 }
        CompletableFuture<Integer> chained = promise.thenApplyAsync { it + 1 }
        CompletableFuturePromise<Integer> future = (CompletableFuturePromise<Integer>) chained

        then:
        chained instanceof CompletableFuturePromise
        future.get() == 43
        future.defaultExecutor().is(executor)
        executions.get() == 2
    }

    void 'exposes Java 21 Future state and immediate result APIs'() {
        when:
        CompletableFuturePromise<Integer> successful = (CompletableFuturePromise<Integer>) factory.createPromise { 42 }
        CompletableFuturePromise<Integer> failed = (CompletableFuturePromise<Integer>) factory.createPromise {
            throw new IllegalStateException('bad')
        }

        then:
        successful.state() == Future.State.SUCCESS
        successful.resultNow() == 42
        failed.state() == Future.State.FAILED
        failed.exceptionNow() instanceof IllegalStateException
    }

    void 'preserves falsey values'() {
        expect:
        factory.createPromise(work).get() == expected

        where:
        work          || expected
        ({ false })   || false
        ({ 0 })       || 0
        ({ '' })      || ''
        ({ [] })      || []
        ({ null })    || null
    }

    void 'chains completion callbacks without custom callback queues'() {
        expect:
        factory.createPromise { 2 }.then { it * 4 }.then { it + 2 }.get() == 10
    }

    void 'invokes error callbacks and recovers with the callback result'() {
        given:
        Throwable observed

        when:
        Integer result = factory.createPromise { throw new IllegalStateException('bad') }
                .onError { Throwable failure ->
                    observed = failure
                    return 42
                }.get()

        then:
        result == 42
        observed instanceof IllegalStateException
    }

    void 'uses the modern factory by default'() {
        expect:
        new PromiseFactoryBuilder().build() instanceof CompletableFuturePromiseFactory
    }
}
