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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

import groovy.concurrent.Awaitable
import groovy.concurrent.DataflowVariable
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import grails.async.Promise
import grails.async.PromiseList
import grails.async.decorator.PromiseDecorator
import grails.async.factory.AbstractPromiseFactory
import org.grails.async.factory.future.CompletableFuturePromise

/**
 * A {@link grails.async.PromiseFactory} built on Groovy's {@code groovy.concurrent} API.
 *
 * <p>Tasks run through {@link Awaitable}, unfulfilled promises are {@link DataflowVariable}
 * instances, and lists of promises are combined with {@link Awaitable#all}. Without an explicit
 * executor, tasks are submitted to the executor Groovy uses for {@code async} blocks,
 * {@link Awaitable#getExecutor()}, which is a virtual-thread executor on Java 21 and later.</p>
 *
 * <p>Both direct and chained {@code get()} calls report failures as {@link java.util.concurrent.ExecutionException}
 * with the original failure as the cause. Error callbacks receive the original failure. A single-promise
 * {@code onError} recovers with its callback result; the aggregate {@code onError} observes the failure
 * and leaves the returned promise failed.</p>
 *
 * @since 9.0
 */
@AutoFinal
@CompileStatic
class AwaitablePromiseFactory extends AbstractPromiseFactory {

    /**
     * The executor tasks are submitted to, or {@code null} to use {@link Awaitable#getExecutor()}
     * at submission time
     */
    final Executor executor

    AwaitablePromiseFactory() {
        this(null)
    }

    AwaitablePromiseFactory(Executor executor) {
        this.executor = executor
    }

    @Override
    <T> Promise<T> createPromise(Class<T> returnType) {
        return new AwaitablePromise<T>(this, new DataflowVariable<T>())
    }

    @Override
    Promise<Object> createPromise() {
        return new AwaitablePromise<Object>(this, new DataflowVariable<Object>())
    }

    @Override
    <T> Promise<T> createPromise(Closure<T>... closures) {
        if (closures.length == 1) {
            return createPromiseInternal(applyDecorators(closures[0], null))
        }
        PromiseList<T> promiseList = new PromiseList<>()
        for (Closure<T> closure : closures) {
            promiseList.add(createPromise(closure))
        }
        return promiseList as Promise<T>
    }

    @Override
    <T> Promise<List<T>> createPromise(List<Closure<T>> closures, List<PromiseDecorator> decorators) {
        if (closures == null) {
            return createBoundPromise(Collections.<T>emptyList())
        }
        PromiseList<T> promiseList = new PromiseList<>()
        for (Closure<T> closure : closures) {
            promiseList.add(createPromise(closure, decorators))
        }
        return promiseList
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises) {
        return (List<T>) all(promises).get()
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises, long timeout, TimeUnit units) {
        return (List<T>) all(promises).get(timeout, units)
    }

    @Override
    <T> Promise<List<T>> onComplete(List<Promise<T>> promises, Closure<T> callable) {
        Awaitable<List<T>> completed = all(promises).then((List<Object> values) -> callable.call(values) as List<T>)
        return new AwaitablePromise<List<T>>(this, completed)
    }

    @Override
    <T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
        Awaitable<List<Object>> observed = all(promises).exceptionally((Throwable failure) -> {
            callable.call(failure)
            throw failure
        })
        return new AwaitablePromise<List<T>>(this, (Awaitable<List<T>>) observed)
    }

    /**
     * Submits an already decorated closure to the configured executor, or to Groovy's default async
     * executor when none was supplied, and exposes the result as an {@link Awaitable}.
     *
     * @param callable the decorated work to run
     * @return the promise for the result
     */
    @Override
    protected <T> Promise<T> createPromiseInternal(Closure<T> callable) {
        Awaitable<T> awaitable = Awaitable.<T>from(CompletableFuture.supplyAsync(() -> callable.call(), targetExecutor()))
        return new AwaitablePromise<T>(this, awaitable)
    }

    /**
     * Combines the promises with {@link Awaitable#all}. Promises created by this factory, and any other
     * {@link Awaitable} or {@link CompletionStage}, are combined directly. Any other promise that is already
     * done is read inline; a pending one is awaited on this factory's executor so that bridging work never
     * lands on a pool the factory does not own.
     */
    protected <T> Awaitable<List<Object>> all(List<Promise<T>> promises) {
        Object[] sources = new Object[promises.size()]
        for (int i = 0; i < sources.length; i++) {
            sources[i] = toAwaitable(promises[i])
        }
        return Awaitable.all(sources)
    }

    private <T> Awaitable<T> toAwaitable(Promise<T> promise) {
        if (promise instanceof Awaitable) {
            return (Awaitable<T>) promise
        }
        if (promise instanceof CompletionStage) {
            return Awaitable.<T>from(promise)
        }
        if (promise.isDone()) {
            try {
                return Awaitable.of(promise.get())
            }
            catch (Throwable failure) {
                return Awaitable.failed(CompletableFuturePromise.unwrap(failure))
            }
        }
        CompletableFuture<T> bridge = new CompletableFuture<T>()
        targetExecutor().execute {
            try {
                bridge.complete(promise.get())
            }
            catch (Throwable failure) {
                bridge.completeExceptionally(CompletableFuturePromise.unwrap(failure))
            }
        }
        return Awaitable.<T>from(bridge)
    }

    private Executor targetExecutor() {
        return executor ?: Awaitable.executor
    }
}
