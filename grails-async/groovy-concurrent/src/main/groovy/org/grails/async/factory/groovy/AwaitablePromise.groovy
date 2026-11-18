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
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Function

import groovy.concurrent.Awaitable
import groovy.concurrent.DataflowVariable
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import grails.async.Promise
import grails.async.PromiseFactory

/**
 * A Grails {@link Promise} backed by a Groovy {@link Awaitable}.
 *
 * <p>The promise is itself an {@code Awaitable}, so it can be handed to Groovy's
 * {@code await} and to the {@link Awaitable} combinators such as {@link Awaitable#all}
 * without any conversion.</p>
 *
 * @param <T> the promised value type
 * @since 9.0
 */
@AutoFinal
@CompileStatic
class AwaitablePromise<T> implements Promise<T>, Awaitable<T> {

    private final PromiseFactory promiseFactory

    /**
     * The Groovy awaitable this promise delegates to
     */
    final Awaitable<T> awaitable

    AwaitablePromise(PromiseFactory promiseFactory, Awaitable<T> awaitable) {
        this.promiseFactory = promiseFactory
        this.awaitable = awaitable
    }

    @Override
    boolean cancel(boolean mayInterruptIfRunning) {
        return awaitable.cancel()
    }

    @Override
    boolean cancel() {
        return awaitable.cancel()
    }

    @Override
    boolean isCancelled() {
        return awaitable.isCancelled()
    }

    @Override
    boolean isDone() {
        return awaitable.isDone()
    }

    @Override
    boolean isCompletedExceptionally() {
        return awaitable.isCompletedExceptionally()
    }

    @Override
    T get() throws InterruptedException, ExecutionException {
        return awaitable.get()
    }

    @Override
    T get(long timeout, TimeUnit units) throws InterruptedException, ExecutionException, TimeoutException {
        return awaitable.get(timeout, units)
    }

    /**
     * Binds the value of an unfulfilled promise. A promise created without work is a
     * {@link DataflowVariable}, so binding it twice fails with {@link IllegalStateException}.
     * A promise that carries work completes with the given value if that work has not finished,
     * in which case the work's own result is discarded, and ignores the value once it has.
     */
    @Override
    Promise<T> accept(T value) {
        if (awaitable instanceof DataflowVariable) {
            ((DataflowVariable<T>) awaitable).bind(value)
        }
        else {
            awaitable.toCompletableFuture().complete(value)
        }
        return this
    }

    @Override
    Promise<T> onComplete(Closure<T> callable) {
        Closure<T> decorated = promiseFactory.applyDecorators(callable, null)
        Awaitable<T> completed = awaitable.then((T value) -> decorated.call(value))
        return new AwaitablePromise<T>(promiseFactory, completed)
    }

    @Override
    Promise<T> onError(Closure<T> callable) {
        Closure<T> decorated = promiseFactory.applyDecorators(callable, null)
        Awaitable<T> recovered = awaitable.exceptionally((Throwable failure) -> decorated.call(failure))
        return new AwaitablePromise<T>(promiseFactory, recovered)
    }

    @Override
    Promise<T> then(Closure<T> callable) {
        return onComplete(callable)
    }

    Promise<T> leftShift(Closure<T> callable) {
        return then(callable)
    }

    @Override
    <U> Awaitable<U> then(Function<? super T, ? extends U> function) {
        return awaitable.then(function)
    }

    @Override
    <U> Awaitable<U> thenCompose(Function<? super T, ? extends Awaitable<U>> function) {
        return awaitable.thenCompose(function)
    }

    @Override
    Awaitable<T> exceptionally(Function<Throwable, ? extends T> function) {
        return awaitable.exceptionally(function)
    }

    @Override
    CompletableFuture<T> toCompletableFuture() {
        return awaitable.toCompletableFuture()
    }

    @Override
    String toString() {
        return "AwaitablePromise[${awaitable}]"
    }
}
