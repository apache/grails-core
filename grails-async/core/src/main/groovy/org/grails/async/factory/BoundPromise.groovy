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
package org.grails.async.factory

import grails.async.Promise
import groovy.transform.CompileStatic

import java.util.concurrent.TimeUnit

/**
 * A bound promise is a promise which is already resolved and doesn't require any asynchronous processing to calculate the value
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class BoundPromise<T> implements Promise<T> {

    T value

    BoundPromise(T value) {
        this.value = value
    }

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
        return true
    }

    T get() throws Throwable {
        if (value instanceof Throwable) {
            throw value
        }
        return value
    }

    T get(long timeout, TimeUnit units) throws Throwable {
        return get()
    }

    @Override
    Promise<T> accept(T value) {
        this.value = value
        return this
    }

    Promise<T> onComplete(Closure<T> callable) {
        if (!(value instanceof Throwable)) {
            return new BoundPromise(callable.call(value))
        }
        return this
    }

    Promise<T> onError(Closure<T> callable) {
        if (value instanceof Throwable) {
            return new BoundPromise(callable.call(value))
        }
        return this
    }

    Promise<T> then(Closure<T> callable) {
        if (!(value instanceof Throwable)) {
            try {
                return new BoundPromise(callable.call(value))
            } catch (Throwable e) {
                return new BoundPromise(e)
            }
        }
        else {
            return this
        }
    }

    Promise<T> leftShift(Closure<T> callable) {
        then(callable)
    }
}
