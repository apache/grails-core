/*
 * Copyright 2013 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.async.factory.gpars

import grails.async.Promise
import grails.async.PromiseFactory
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovyx.gpars.dataflow.Dataflow

import java.util.concurrent.TimeUnit

/**
 * Implementation of {@link Promise} interface for Gpars
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@AutoFinal
@CompileStatic
class GparsPromise<T> implements Promise<T> {

    private final PromiseFactory promiseFactory

    groovyx.gpars.dataflow.Promise<T> internalPromise

    GparsPromise(PromiseFactory promiseFactory, groovyx.gpars.dataflow.Promise internalPromise) {
        this.promiseFactory = promiseFactory
        this.internalPromise = internalPromise
    }

    GparsPromise(PromiseFactory promiseFactory, Closure callable) {
        this(promiseFactory, Dataflow.task(callable))
    }

    @Override
    boolean cancel(boolean mayInterruptIfRunning) {
        if(isDone()) return false
        throw new UnsupportedOperationException('Cancellation not supported')
    }

    @Override
    boolean isCancelled() {
        return false
    }

    @Override
    boolean isDone() {
        return internalPromise.isBound() || internalPromise.isError()
    }

    @Override
    T get() {
        return internalPromise.get()
    }

    @Override
    T get(long timeout, TimeUnit units) throws Throwable {
        return internalPromise.get(timeout, units)
    }

    @Override
    Promise<T> accept(T value) {
        internalPromise = Dataflow.task({value})
        return this
    }

    Promise<T> leftShift(Closure<T> callable) {
        then(callable)
    }

    @Override
    Promise<T> onComplete(Closure<T> callable) {
        def decoratedCallable= promiseFactory.applyDecorators(callable, null)
        internalPromise.whenBound { T value ->
            if (!(value instanceof Throwable)) {
                return decoratedCallable.call(value)
            }
            return null
        }
        return this
    }

    @Override
    Promise<T> onError(Closure<T> callable) {
        def decoratedCallable = promiseFactory.applyDecorators(callable, null)
        internalPromise.whenBound { T value ->
            if (value instanceof Throwable) {
                return decoratedCallable.call(value)
            }
            return null
        }
        return this
    }

    @Override
    Promise<T> then(Closure<T> callable) {
        def decoratedCallable = promiseFactory.applyDecorators(callable, null)
        return new GparsPromise(promiseFactory, internalPromise.then(decoratedCallable))
    }
}