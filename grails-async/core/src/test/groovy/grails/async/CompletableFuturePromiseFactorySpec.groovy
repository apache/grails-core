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
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.UnaryOperator
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

    void 'default builder retains the supplied application executor'() {
        when:
        def built = PromiseFactoryBuilder.build(sameThreadExecutor)

        then:
        built instanceof CompletableFuturePromiseFactory
        built.createPromise { Thread.currentThread() }.get().is(Thread.currentThread())
    }

    void 'direct and chained get preserve the original failure with its cause'() {
        given:
        def original = checked ? new IOException('outer', new IOException('inner')) : new IllegalStateException('outer', new IOException('inner'))
        def promise = factory.createPromise { throw original }
        if (chained) {
            promise = promise.then { it }
        }

        when:
        if (timed) {
            promise.get(5, TimeUnit.SECONDS)
        }
        else {
            promise.get()
        }

        then:
        def failure = thrown(ExecutionException)
        failure.cause.is(original)

        where:
        [chained, timed, checked] << [[false, true], [false, true], [false, true]].combinations()
    }

    void 'single and aggregate error callbacks receive the same original failure'() {
        given:
        def original = checked ? new IOException('outer', new IOException('inner')) : new IllegalStateException('outer', new IOException('inner'))
        def promise = factory.createPromise { throw original }
        Throwable single
        Throwable aggregate

        when:
        promise.onError { single = it }
        factory.onError([promise]) { aggregate = it }

        then:
        single.is(original)
        aggregate.is(original)

        where:
        checked << [false, true]
    }

    void 'a failing recovery callback preserves its own failure'() {
        given:
        def original = checked ? new IOException('callback', new IOException('inner')) : new IllegalStateException('callback', new IOException('inner'))

        when:
        factory.createPromise { throw new IOException('task') }.onError { throw original }.get()

        then:
        def failure = thrown(ExecutionException)
        failure.cause.is(original)

        where:
        checked << [false, true]
    }

    void 'checked failures from then and aggregate callbacks are not proxy wrapped'() {
        given:
        def original = new IOException('callback')
        def promise = aggregate
                ? factory.onComplete([factory.createPromise { 1 }]) { throw original }
                : factory.createPromise { 1 }.then { throw original }

        when:
        promise.get()

        then:
        def failure = thrown(ExecutionException)
        failure.cause.is(original)

        where:
        aggregate << [false, true]
    }

    void 'bridging a non CompletionStage promise preserves the original checked failure'() {
        given:
        def original = new IOException('io')
        def bound = factory.createBoundPromise(new ExecutionException(original))
        Throwable observed

        when:
        factory.onError([bound]) { observed = it }.get()

        then:
        def failure = thrown(ExecutionException)
        failure.cause.is(original)
        observed.is(original)
    }

    void 'closing a decorated owned executor shuts down the underlying pool'() {
        given:
        UnaryOperator<Executor> decorator = (Executor executor) -> {
            Executor decorated = (Runnable task) -> executor.execute(task)
            return decorated
        }
        def owned = new CompletableFuturePromiseFactory(decorator)
        assert owned.createPromise { 42 }.get() == 42

        when:
        owned.close()
        owned.close()
        owned.createPromise { 1 }

        then:
        thrown(RejectedExecutionException)
    }

    void 'closing a factory leaves its externally supplied executor running'() {
        given:
        def executor = Executors.newSingleThreadExecutor()
        def external = new CompletableFuturePromiseFactory(executor)

        when:
        external.close()

        then:
        external.createPromise { 42 }.get() == 42

        cleanup:
        executor.shutdownNow()
    }
}
