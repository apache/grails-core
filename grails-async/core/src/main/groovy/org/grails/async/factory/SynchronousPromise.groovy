/*
 * Copyright 2012 the original author or authors.
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
 * A promise that executes synchronously, in the same thread as the creator
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class SynchronousPromise<T> implements Promise<T> {

    Closure<T> callable
    Object value
    boolean executed = false

    SynchronousPromise(Closure<T> callable) {
        this.callable = callable
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
        if (!executed) {
            executed = true
            try { value = callable.call() }
            catch (e) { value = e }
        }
        if (value instanceof Throwable) {
            throw value
        }
        return value as T
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
        try { callable.call(get()) }
        catch (Throwable ignored) {}
        return this
    }

    Promise<T> onError(Closure<T> callable) {
        try { get() }
        catch (Throwable e) { callable.call(e) }
        return this
    }

    Promise<T> then(Closure<T> callable) {
        final value = get()
        return new SynchronousPromise(callable.curry(value))
    }

    Promise<T> leftShift(Closure<T> callable) {
        then(callable)
    }
}
