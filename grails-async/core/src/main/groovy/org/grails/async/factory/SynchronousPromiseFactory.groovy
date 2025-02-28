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
import grails.async.PromiseList
import grails.async.factory.AbstractPromiseFactory
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import java.util.concurrent.TimeUnit

/**
 * A {@link grails.async.PromiseFactory} implementation that constructors promises that execute synchronously.
 * Useful for testing environments.
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@AutoFinal
@CompileStatic
class SynchronousPromiseFactory extends AbstractPromiseFactory {

    @Override
    <T> Promise<T> createPromise(Class<T> returnType) {
        throw new UnsupportedOperationException('synchronous factory does not support unfulfilled promises')
    }

    @Override
    Promise<Void> createPromise() {
        throw new UnsupportedOperationException('synchronous factory does not support unfulfilled promises')
    }

    @Override
    <T> Promise<T> createPromise(Closure<T>... closures) {
        Promise<T> promise
        if (closures.length == 1) {
            promise = new SynchronousPromise<T>(closures[0])
        } else {
            def promiseList = new PromiseList()
            for(p in closures) {
                promiseList << p
            }
            promise = promiseList
        }

        try { promise.get() }
        catch (Throwable ignored) {}

        return promise
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises) {
        return promises.collect { Promise<T> p -> p.get() }
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises, long timeout, TimeUnit units) {
        return promises.collect { Promise<T> p -> p.get() }
    }

    @Override
    <T> Promise<T> onComplete(List<Promise<T>> promises, Closure<T> callable) {
        try {
            List<T> values = promises.collect { Promise<T> p -> p.get() }
            return new BoundPromise(callable.call(values))
        } catch (Throwable e) {
            return new BoundPromise(e)
        }
    }

    @Override
    <T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
        try {
            List<T> values = promises.collect() { Promise<T> p -> p.get() }
            return new BoundPromise<List<T>>(values)
        } catch (Throwable e) {
            callable.call(e)
            return new BoundPromise(e)
        }
    }
}
