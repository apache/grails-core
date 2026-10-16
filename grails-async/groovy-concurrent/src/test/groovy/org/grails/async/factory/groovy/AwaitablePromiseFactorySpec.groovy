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
package org.grails.async.factory.groovy

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import groovy.concurrent.Awaitable
import groovy.concurrent.Pool

import grails.async.Promise
import grails.async.PromiseFactory
import grails.async.Promises
import grails.async.decorator.PromiseDecorator
import org.grails.async.factory.PromiseFactoryBuilder
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class AwaitablePromiseFactorySpec extends Specification {

    private final PollingConditions conditions = new PollingConditions(timeout: 5, delay: 0.2)

    void setup() {
        Promises.promiseFactory = null
    }

    void cleanup() {
        Promises.promiseFactory = null
    }

    void 'the promise factory is discovered from the classpath'() {
        expect:
        Promises.promiseFactory instanceof AwaitablePromiseFactory
        new PromiseFactoryBuilder().build() instanceof AwaitablePromiseFactory
    }

    void 'promises are Groovy awaitables'() {
        when:
        Promise<Integer> promise = Promises.createPromise { 21 * 2 }

        then:
        promise instanceof Awaitable
        Awaitable.all(promise, Promises.createPromise { 1 }).get() == [42, 1]
        ((Awaitable<Integer>) promise).then { int value -> value + 1 }.get() == 43
        ((Awaitable<Integer>) promise).toCompletableFuture().join() == 42
    }

    void 'tasks run on the supplied executor'() {
        given:
        ExecutorService executor = Executors.newSingleThreadExecutor { Runnable task -> new Thread(task, 'promise-worker') }
        PromiseFactory factory = new AwaitablePromiseFactory(executor)

        expect:
        factory.createPromise { Thread.currentThread().name }.get() == 'promise-worker'
        factory.createPromise([{ Thread.currentThread().name }, { Thread.currentThread().name }]).get() == ['promise-worker', 'promise-worker']

        cleanup:
        executor.shutdownNow()
    }

    void 'tasks run on a Groovy pool'() {
        given:
        Pool pool = Pool.fixed(2)
        PromiseFactory factory = new AwaitablePromiseFactory(pool)

        expect:
        factory.createPromise { Thread.currentThread() }.get() != Thread.currentThread()

        cleanup:
        pool.close()
    }

    void 'tasks run on the Groovy default executor by default'() {
        given:
        Executor original = Awaitable.executor
        ExecutorService executor = Executors.newSingleThreadExecutor { Runnable task -> new Thread(task, 'groovy-async-worker') }
        Awaitable.executor = executor

        expect:
        new AwaitablePromiseFactory().executor == null
        Promises.createPromise { Thread.currentThread().name }.get() == 'groovy-async-worker'

        cleanup:
        Awaitable.executor = original
        executor.shutdownNow()
    }

    void 'an unfulfilled promise is a dataflow variable completed by accept'() {
        given:
        Promise<String> promise = Promises.createPromise(String)
        def observed = null
        promise.onComplete { observed = it }

        expect:
        !promise.done

        when:
        promise.accept('bound')

        then:
        promise.done
        promise.get() == 'bound'
        conditions.eventually {
            assert observed == 'bound'
        }
    }

    void 'Test add promise decorator'() {
        when: 'a decorator is added'
        def decorator = { Closure c -> return { "*${c.call(*it)}*" } } as PromiseDecorator
        def promise = Promises.createPromise({ 10 }, [decorator])
        def result = promise.get()

        then: 'the result is decorated'
        result == '*10*'
    }

    void 'lookup strategy decorators apply to tasks and callbacks'() {
        given:
        def factory = new AwaitablePromiseFactory()
        def decorator = { Closure c -> return { Object... args -> "[${c.call(*args)}]" } } as PromiseDecorator
        factory.addPromiseDecoratorLookupStrategy { [decorator] }

        expect:
        factory.createPromise { 'task' }.then { "${it}-then" }.get() == '[[task]-then]'

        and: 'list and explicit-decorator forms decorate each task exactly once'
        factory.createPromise([{ 'a' }, { 'b' }]).get() == ['[a]', '[b]']
        factory.createPromise({ 'c' }, [decorator]).get() == '[[c]]'
    }

    void 'binding an unfulfilled promise twice fails'() {
        given:
        Promise<String> promise = Promises.createPromise(String)
        promise.accept('first')

        when:
        promise.accept('second')

        then:
        thrown(IllegalStateException)
        promise.get() == 'first'
    }

    void 'promises that are not awaitables are bridged on the factory executor'() {
        given:
        ExecutorService executor = Executors.newSingleThreadExecutor { Runnable task -> new Thread(task, 'bridge-worker') }
        PromiseFactory factory = new AwaitablePromiseFactory(executor)
        def blocking = new SlowPromise<String>(value: 'slow')

        when:
        def result = factory.waitAll([blocking, factory.createBoundPromise('bound')])

        then:
        result == ['slow', 'bound']
        blocking.thread.name == 'bridge-worker'

        cleanup:
        executor.shutdownNow()
    }

    void 'Test promise timeout handling'() {
        when: 'a promise that takes longer than the timeout'
        def promise = Promises.createPromise {
            sleep 1000
            'completed op'
        }
        promise.get(50, TimeUnit.MILLISECONDS)

        then: 'a timeout error occurs'
        thrown TimeoutException
    }

    void 'waiting for a list of promises honours the timeout'() {
        when:
        Promises.waitAll([Promises.createPromise { sleep 1000; 1 }], 50, TimeUnit.MILLISECONDS)

        then:
        thrown TimeoutException
    }

    void 'Test promise map handling'() {
        when: 'a promise map is created'
        def map = Promises.createPromise(one: { 1 }, two: { 1 + 1 }, four: { 2 * 2 })
        def result = map.get()

        then: 'the map is valid'
        result == [one: 1, two: 2, four: 4]
    }

    void 'Test promise list handling'() {
        when: 'a promise list is created from two promises'
        def promiseList = Promises.createPromise(
                Promises.createPromise({ 1 + 1 }),
                Promises.createPromise({ 2 + 2 })
        )
        def result = null
        promiseList.onComplete { List v -> result = v }

        then: 'the result is correct'
        conditions.eventually {
            assert result == [2, 4]
        }

        when: 'a promise list is created from two closures'
        promiseList = Promises.createPromise({ 1 + 1 }, { 2 + 2 })
        promiseList.onComplete { List v -> result = v }

        then: 'The result is correct'
        conditions.eventually {
            assert result == [2, 4]
        }

        and: 'waiting for the list returns the values'
        promiseList.get() == [2, 4]
        Promises.waitAll(Promises.createPromise({ 1 }), Promises.createPromise({ 2 })) == [1, 2]
    }

    void 'Test promise onComplete handling'() {
        when: 'a promise is executed with an onComplete handler'
        def promise = Promises.createPromise({ 1 + 1 })
        def result = null
        def hasError = false
        promise.onComplete { result = it }
        promise.onError { hasError = true }

        then: 'the onComplete handler is invoked and the onError handler is ignored'
        conditions.eventually {
            assert result == 2
            assert hasError == false
        }
    }

    void 'Test promise onError handling'() {
        when: 'a promise that throws an exception is executed with an onError handler'
        def promise = Promises.createPromise({ throw new RuntimeException('bad') })
        def result = null
        Throwable error = null
        promise.onComplete { result = it }
        promise.onError { error = it }

        then: 'the onError handler is invoked and the onComplete handler is ignored'
        conditions.eventually {
            assert !result
            assert error
            assert error.message == 'bad'
        }
    }

    void 'Test promise chaining'() {
        when: 'a promise is chained'
        def promise = Promises.createPromise({ 1 + 1 })
        promise = promise.then { it * 2 } then { it + 6 }
        def value = promise.get()

        then: 'the chain is executed'
        value == 10
    }

    void 'Test promise chaining with exception'() {
        when: 'a promise with an exception is chained'
        def promise = Promises.createPromise({ 1 + 1 })
        promise = promise.then { it * 2 } then { throw new RuntimeException('bad') } then { it + 6 }
        promise.get()

        then: 'the failure is reported through the Future contract'
        def failure = thrown(ExecutionException)
        failure.cause instanceof RuntimeException
        failure.cause.message == 'bad'
    }

    void 'direct and chained get preserve the original failure with its cause'() {
        given:
        def original = checked ? new IOException('outer', new IOException('inner')) : new IllegalStateException('outer', new IOException('inner'))
        def promise = Promises.createPromise { throw original }
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

    void 'a single onError recovers with the callback result'() {
        given:
        Throwable observed

        when:
        def result = Promises.createPromise { throw new IllegalStateException('bad') }
                .onError { Throwable failure ->
                    observed = failure
                    return 42
                }.get()

        then:
        result == 42
        observed instanceof IllegalStateException
    }

    void 'a failing recovery callback preserves its own failure'() {
        given:
        def original = new IOException('callback')

        when:
        Promises.createPromise { throw new IOException('task') }.onError { throw original }.get()

        then:
        def failure = thrown(ExecutionException)
        failure.cause.is(original)
    }

    void 'single and aggregate error callbacks receive the same original failure'() {
        given:
        def original = checked ? new IOException('outer') : new IllegalStateException('outer')
        def promise = Promises.createPromise { throw original }
        Throwable single
        Throwable aggregate

        when:
        promise.onError { single = it }
        def observed = Promises.onError([promise]) { aggregate = it }

        and:
        observed.get()

        then:
        def failure = thrown(ExecutionException)
        failure.cause.is(original)
        single.is(original)
        aggregate.is(original)

        where:
        checked << [false, true]
    }

    void 'aggregate onComplete resolves to the callback result'() {
        expect:
        Promises.onComplete([Promises.createPromise { 1 }, Promises.createPromise { 2 }]) { List values -> values*.multiply(10) }.get() == [10, 20]
    }

    void 'aggregate callbacks bridge promises that are not awaitables'() {
        given:
        def bound = Promises.createBoundPromise(new ExecutionException(new IOException('io')))
        Throwable observed

        when:
        Promises.onError([bound]) { observed = it }.get()

        then:
        def failure = thrown(ExecutionException)
        failure.cause instanceof IOException
        observed instanceof IOException
        Promises.waitAll(Promises.createBoundPromise(1), Promises.createPromise { 2 }) == [1, 2]
    }

    void 'completed promises are resolved inline so a single-thread pool does not deadlock'() {
        given:
        Pool pool = Pool.fixed(1)
        PromiseFactory factory = new AwaitablePromiseFactory(pool)

        when:
        def result = factory.createPromise {
            factory.waitAll([factory.createBoundPromise(1), factory.createBoundPromise(2)])
        }.get(5, TimeUnit.SECONDS)

        then:
        result == [1, 2]

        cleanup:
        pool.close()
    }

    void 'bridged failures are unwrapped like awaitable failures'() {
        given:
        def original = new IOException('io')
        def bound = Promises.createBoundPromise(new ExecutionException(new java.util.concurrent.CompletionException(original)))
        Throwable observed

        when:
        Promises.onError([bound]) { observed = it }.get()

        then:
        def failure = thrown(ExecutionException)
        failure.cause.is(original)
        observed.is(original)
    }

    static class SlowPromise<T> extends org.grails.async.factory.BoundPromise<T> {

        Thread thread

        SlowPromise(Map args) {
            super((T) args.value)
        }

        @Override
        boolean isDone() {
            return thread != null
        }

        @Override
        T get() {
            thread = Thread.currentThread()
            sleep 50
            return super.get()
        }
    }

    void 'preserves falsey values'() {
        expect:
        Promises.createPromise(work).get() == expected

        where:
        work          || expected
        ({ false })   || false
        ({ 0 })       || 0
        ({ '' })      || ''
        ({ [] })      || []
        ({ null })    || null
    }

    void 'a promise can be cancelled before it runs'() {
        given:
        ExecutorService executor = Executors.newSingleThreadExecutor()
        PromiseFactory factory = new AwaitablePromiseFactory(executor)
        def blocker = factory.createPromise { sleep 2000 }
        def promise = factory.createPromise { 'never' }

        when:
        boolean cancelled = promise.cancel(true)

        then:
        cancelled
        promise.cancelled
        promise.done

        cleanup:
        blocker.cancel(true)
        executor.shutdownNow()
    }
}
