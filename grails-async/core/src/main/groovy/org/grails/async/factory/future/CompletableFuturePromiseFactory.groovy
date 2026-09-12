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
package org.grails.async.factory.future

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import jakarta.annotation.PreDestroy

import grails.async.Promise
import grails.async.PromiseList
import grails.async.factory.AbstractPromiseFactory
import org.grails.async.factory.BoundPromise

/**
 * A promise factory implemented with {@link CompletableFuture}.
 *
 * @since 8.0
 */
@AutoFinal
@CompileStatic
class CompletableFuturePromiseFactory extends AbstractPromiseFactory implements Closeable {

    final Executor executor
    private final ExecutorService ownedExecutor

    CompletableFuturePromiseFactory() {
        this(Executors.newCachedThreadPool(), true)
    }

    CompletableFuturePromiseFactory(Executor executor) {
        this(executor, false)
    }

    private CompletableFuturePromiseFactory(Executor executor, boolean ownsExecutor) {
        this.executor = executor
        this.ownedExecutor = ownsExecutor ? (ExecutorService) executor : null
    }

    @Override
    <T> Promise<T> createPromise(Class<T> returnType) {
        return new BoundPromise<T>(null)
    }

    @Override
    Promise<Object> createPromise() {
        return new BoundPromise<Object>(null)
    }

    @Override
    <T> Promise<T> createPromise(Closure<T>... closures) {
        if (closures.length == 1) {
            Closure<T> decorated = applyDecorators(closures[0], null)
            CompletableFuture<T> future = CompletableFuture.supplyAsync(
                    { decorated.call() } as Supplier<T>,
                    executor)
            return CompletableFuturePromise.fromStage(future, executor, false)
        }

        PromiseList<T> promises = new PromiseList<T>()
        for (Closure<T> closure : closures) {
            promises.add(createPromise(closure))
        }
        return promises as Promise<T>
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises) {
        return promises.collect { Promise<T> promise -> promise.get() }
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises, long timeout, TimeUnit units) {
        long deadline = System.nanoTime() + units.toNanos(timeout)
        return promises.collect { Promise<T> promise ->
            long remaining = Math.max(0L, deadline - System.nanoTime())
            promise.get(remaining, TimeUnit.NANOSECONDS)
        }
    }

    @Override
    <T> Promise<List<T>> onComplete(List<Promise<T>> promises, Closure<T> callable) {
        CompletableFuture<Void> all = CompletableFuture.allOf(*promises.collect { Promise<T> promise ->
            asCompletableFuture(promise)
        } as CompletableFuture[])
        return CompletableFuturePromise.fromStage(all.thenApply {
            List<T> values = promises.collect { Promise<T> promise -> promise.get() }
            return callable.call(values) as List<T>
        }, executor)
    }

    @Override
    <T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
        CompletableFuture<Void> all = CompletableFuture.allOf(*promises.collect { Promise<T> promise ->
            asCompletableFuture(promise)
        } as CompletableFuture[])
        CompletableFuturePromise<List<T>> result = new CompletableFuturePromise<List<T>>(executor)
        all.whenComplete { Void _, Throwable failure ->
            if (failure == null) {
                result.complete(promises.collect { Promise<T> promise -> promise.get() })
            }
            else {
                Throwable cause = failure.cause ?: failure
                ExecutionException reportedFailure = new ExecutionException(cause)
                try {
                    callable.call(reportedFailure)
                }
                catch (Throwable callbackFailure) {
                    result.completeExceptionally(callbackFailure)
                    return
                }
                result.completeExceptionally(cause)
            }
        }
        return result
    }

    private <T> CompletableFuture<T> asCompletableFuture(Promise<T> promise) {
        if (promise instanceof CompletableFuture future) {
            return (CompletableFuture<T>) future
        }
        return CompletableFuture.supplyAsync({ promise.get() } as Supplier<T>, executor)
    }

    @Override
    @PreDestroy
    void close() {
        ownedExecutor?.shutdown()
    }
}
