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

package org.grails.async.factory.rxjava2

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

import grails.async.Promise
import grails.async.PromiseList
import grails.async.factory.AbstractPromiseFactory
import org.grails.async.factory.BoundPromise

/**
 * An RxJava {@link grails.async.PromiseFactory} implementation
 *
 * @author Graeme Rocher
 * @since 3.3
 *
 * @deprecated RxJava 2 is no longer maintained. Use the RxJava 3 implementation in
 * {@code grails-async-rxjava3} ({@code org.grails.async.factory.rxjava3.RxPromiseFactory}) instead.
 * This class, and the {@code grails-async-rxjava2} module that provides it, will be removed in a future release.
 */
@AutoFinal
@CompileStatic
@Deprecated(since = '9.0', forRemoval = true)
class RxPromiseFactory extends AbstractPromiseFactory {

    private static final Logger LOG = LoggerFactory.getLogger(RxPromiseFactory)

    private static final AtomicBoolean DEPRECATION_LOGGED = new AtomicBoolean()

    RxPromiseFactory() {
        if (DEPRECATION_LOGGED.compareAndSet(false, true)) {
            LOG.warn('The RxJava 2 promise factory (grails-async-rxjava2) is deprecated and will be removed in a future ' +
                    'release of Grails. Replace it with grails-async-rxjava3.')
        }
    }

    @Override
    <T> Promise<T> createPromise(Class<T> returnType) {
        new RxPromise<T>(this, Single.just(null))
    }

    @Override
    Promise<Object> createPromise() {
        new RxPromise<Object>(this, Single.just(null))
    }

    @Override
    <T> Promise<T> createPromise(Closure<T>[] closures) {
        if (closures.length == 1) {
            return new RxPromise<T>(this, closures[0], Schedulers.io())
        }
        else {
            def promiseList = new PromiseList()
            for (Closure closure : closures) {
                promiseList.add(closure)
            }
            return promiseList
        }
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises) {
        return promises.collect { Promise<T> p -> p.get() }
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises, long timeout, TimeUnit units) {
        promises.collect { Promise<T> p -> p.get(timeout, units) }
    }

    @Override
    <T> Promise<T> onComplete(List<Promise<T>> promises, Closure<T> callable) {
        new RxPromise<T>(this, Observable.concat(
            promises.collect { Promise p ->
                if (p instanceof BoundPromise) {
                    return Observable.just(((BoundPromise) p).value) as Observable<T>
                }
                else {
                    return ((RxPromise) p).toObservable() as Observable<T>
                }
            }
        ).toList())
         .onComplete(callable)
    }

    @Override
    <T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
        new RxPromise(this, Observable.concat(
                promises.collect { ((RxPromise<T>) it).toObservable() }
        ).toList())
        .onError(callable) as Promise<List<T>>
    }
}
