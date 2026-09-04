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
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic

import grails.async.Promise

/**
 * A Grails {@link Promise} backed by the JDK completion-stage implementation.
 *
 * @param <T> the promised value type
 * @since 8.0
 */
@CompileStatic
class CompletableFuturePromise<T> extends CompletableFuture<T> implements Promise<T> {

    final Executor executor
    private final boolean unwrapFailureOnGet

    CompletableFuturePromise(boolean unwrapFailureOnGet = false) {
        this(null, unwrapFailureOnGet)
    }

    CompletableFuturePromise(Executor executor, boolean unwrapFailureOnGet = false) {
        this.executor = executor
        this.unwrapFailureOnGet = unwrapFailureOnGet
    }

    @Override
    Executor defaultExecutor() {
        return executor ?: super.defaultExecutor()
    }

    @Override
    <U> CompletableFuturePromise<U> newIncompleteFuture() {
        return new CompletableFuturePromise<U>(executor, unwrapFailureOnGet)
    }

    @Override
    Promise<T> accept(T value) {
        complete(value)
        return this
    }

    @Override
    Promise<T> onComplete(Closure<T> callable) {
        return fromStage(thenApply(callable), executor)
    }

    @Override
    Promise<T> onError(Closure<T> callable) {
        CompletableFuturePromise<T> child = new CompletableFuturePromise<T>(executor)
        whenComplete { T value, Throwable failure ->
            if (failure == null) {
                child.complete(value)
            }
            else {
                try {
                    child.complete(callable.call(unwrap(failure)) as T)
                }
                catch (Throwable callbackFailure) {
                    child.completeExceptionally(callbackFailure)
                }
            }
        }
        return child
    }

    @Override
    Promise<T> then(Closure<T> callable) {
        return onComplete(callable)
    }

    @Override
    T get() throws InterruptedException, ExecutionException {
        try {
            return super.get()
        }
        catch (ExecutionException failure) {
            if (unwrapFailureOnGet) {
                throw failure.cause
            }
            throw failure
        }
    }

    @Override
    T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return super.get(timeout, unit)
        }
        catch (ExecutionException failure) {
            if (unwrapFailureOnGet) {
                throw failure.cause
            }
            throw failure
        }
    }

    static <T> CompletableFuturePromise<T> fromStage(
            CompletionStage<T> stage,
            Executor executor = null,
            boolean unwrapFailureOnGet = true) {
        Executor stageExecutor = executor
        if (stageExecutor == null && stage instanceof CompletableFuturePromise) {
            stageExecutor = ((CompletableFuturePromise<?>) stage).executor
        }
        CompletableFuturePromise<T> promise = new CompletableFuturePromise<T>(stageExecutor, unwrapFailureOnGet)
        stage.whenComplete { T value, Throwable failure ->
            if (failure == null) {
                promise.complete(value)
            }
            else {
                promise.completeExceptionally(unwrap(failure))
            }
        }
        return promise
    }

    private static Throwable unwrap(Throwable failure) {
        return failure.cause ?: failure
    }
}
