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
package grails.async

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import java.util.concurrent.TimeUnit

/**
 * A list of promises
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@AutoFinal
@CompileStatic
class PromiseList<T> implements Promise<List<T>> {

    protected List<Promise<T>> promises = []
    protected List<T> initialized

    @Override
    Promise<List<T>> accept(List<T> value) {
        initialized = value
        return this
    }

    /**
     * Add a callable to the promise list
     *
     * @param callable The callable closure
     * @return The promise list
     */
    PromiseList<T> leftShift(Closure<T> callable) {
        promises.add(Promises.createPromise(callable))
        return this
    }

    /**
     * Add a value as a bound promise to the list of values
     *
     * @param value value
     * @return The promise list
     */
    PromiseList<T> leftShift(T value) {
        promises.add(Promises.createBoundPromise(value))
        return this
    }

    /**
     * Add a promise to the promise list
     *
     * @param promise The promise
     * @return The promise list
     */
    PromiseList<T> leftShift(Promise<T> promise) {
        promises.add(promise)
        return this
    }

    /**
     * Implementation of add that adds any value as a bound promise
     * @param value The value
     * @return True if it was added
     */
    boolean add(T value) {
        return promises.add(Promises.createBoundPromise(value))
    }

    /**
     * Implementation of add that takes a closure and creates a promise, adding it to the list
     * @param closure The closure
     * @return True if it was added
     */
    boolean add(Closure<T> closure) {
        return promises.add(Promises.createPromise(closure))
    }

    /**
     * Implementation of add that takes a promise, adding it to the list
     * @param promise The promise
     * @return True if it was added
     */
    boolean add(Promise<T> promise) {
        return promises.add(promise)
    }

    /**
     * Execute the given closure when all promises are complete
     *
     * @param callable The closure
     */
    @Override
    Promise<List<T>> onComplete(Closure callable) {
        return Promises.onComplete(promises, callable)
    }

    @Override
    Promise<List<T>> onError(Closure callable) {
        return Promises.onError(promises, callable)
    }

    @Override
    Promise<List<T>> then(Closure callable) {
        return Promises.onComplete(promises, { List<T> values -> return values }).then(callable)
    }

    /**
     * Synchronously obtains all the values from all the promises
     * @return The values
     */
    @Override
    boolean cancel(boolean mayInterruptIfRunning) {
        return false
    }

    @Override
    boolean isCancelled() {
        return false
    }

    @Override
    boolean isDone() {
        return promises.every {promise -> promise.isDone() }
    }

    @Override
    List<T> get() {
        return initialized ?: Promises.waitAll(promises)
    }

    @Override
    List<T> get(long timeout, TimeUnit units) throws Throwable {
        return initialized ?: Promises.waitAll(promises, timeout, units)
    }
}
