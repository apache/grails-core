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

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import jakarta.annotation.PreDestroy

import grails.async.Promise
import grails.async.PromiseList
import grails.async.factory.AbstractPromiseFactory
import org.grails.async.factory.BoundPromise

/**
 * PromiseFactory implementation backed by Java virtual threads.
 *
 * @since 8.0
 */
@AutoFinal
@CompileStatic
class VirtualThreadPromiseFactory extends AbstractPromiseFactory implements Closeable {

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()

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
            Closure<T> decoratedCallable = applyDecorators(closures[0], null)
            FutureTaskPromise<T> promise = new FutureTaskPromise<T>(this, decoratedCallable as Callable<T>)
            executorService.execute(promise)
            return promise
        }

        PromiseList<T> list = new PromiseList<>()
        for (Closure<T> closure : closures) {
            list.add(closure)
        }
        return list as Promise<T>
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises) {
        return promises.collect { Promise<T> promise -> promise.get() }
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises, long timeout, TimeUnit units) {
        return promises.collect { Promise<T> promise -> promise.get(timeout, units) }
    }

    @Override
    <T> Promise<List<T>> onComplete(List<Promise<T>> promises, Closure<T> callable) {
        // callable's return value is intentionally discarded: the resolved value of the
        // returned Promise is the waited-on values themselves (matching Promise<List<T>>),
        // not whatever the T-typed callback happens to return.
        FutureTaskPromise<List<T>> promise = new FutureTaskPromise<List<T>>(this, {
            List<T> values = waitAll(promises)
            callable.call(values)
            return values
        } as Callable<List<T>>)
        executorService.execute(promise)
        return promise
    }

    @Override
    <T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
        FutureTaskPromise<List<T>> promise = new FutureTaskPromise<List<T>>(this, {
            try {
                return waitAll(promises)
            }
            catch (Throwable e) {
                callable.call(e)
                return Collections.<T> emptyList()
            }
        } as Callable<List<T>>)
        executorService.execute(promise)
        return promise
    }

    @Override
    @PreDestroy
    void close() {
        if (!executorService.isShutdown()) {
            executorService.shutdown()
        }
    }
}
