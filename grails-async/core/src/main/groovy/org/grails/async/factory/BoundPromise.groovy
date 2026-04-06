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
package org.grails.async.factory

import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic

import grails.async.Promise

/**
 * A bound promise is a promise which is already resolved and doesn't require any asynchronous processing to calculate the value
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class BoundPromise<T> implements Promise<T> {

    Object value

    BoundPromise(T value) {
        this.value = value
    }

    BoundPromise(Throwable error) {
        this.value = error
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

    @SuppressWarnings('unchecked')
    T get() throws Throwable {
        Object v = value
        if (v instanceof Throwable) {
            throw v
        }
        return (T) v
    }

    T get(long timeout, TimeUnit units) throws Throwable {
        return get()
    }

    @Override
    Promise<T> accept(T value) {
        this.value = value
        return this
    }

    @SuppressWarnings('unchecked')
    Promise<T> onComplete(Closure<T> callable) {
        Object v = value
        if (v instanceof Throwable) {
            return this
        }
        return new BoundPromise<T>(callable.call((T) v))
    }

    Promise<T> onError(Closure<T> callable) {
        Object v = value
        if (!(v instanceof Throwable)) {
            return this
        }
        return new BoundPromise<T>(callable.call((Throwable) v))
    }

    @SuppressWarnings('unchecked')
    Promise<T> then(Closure<T> callable) {
        Object v = value
        if (v instanceof Throwable) {
            return this
        }
        try {
            return new BoundPromise<T>(callable.call((T) v))
        } catch (Throwable e) {
            return new BoundPromise<T>(e)
        }
    }

    Promise<T> leftShift(Closure<T> callable) {
        then(callable)
    }
}
