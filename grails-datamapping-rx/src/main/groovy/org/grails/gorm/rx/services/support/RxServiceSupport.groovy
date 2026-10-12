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

package org.grails.gorm.rx.services.support

import groovy.transform.CompileStatic
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.concurrent.Callable

/**
 * Helper class for creating observables that run of the IO scheduler for blocking GORM operations
 *
 * @since 6.1
 */
@CompileStatic
class RxServiceSupport {

    /**
     * Create an observable from the given callable
     *
     * @param callable The callable
     * @return The {@link Observable}
     */
    static <T> Observable<T> create(Callable<T> callable) {
        Scheduler scheduler = Schedulers.io()
        create(scheduler, callable)
    }

    /**
     * Create an observable from the given callable on the given Scheduler
     *
     * @param callable The callable
     * @return The {@link Observable}
     */
    static <T> Observable<T> create(Scheduler scheduler, Callable<T> callable) {
        Observable<T> observable = Observable.defer {
            def result = callable.call()
            if (result == null) {
                return Observable.<T>empty()
            }
            else if (result instanceof Iterable) {
                return Observable.fromIterable((Iterable<T>) result)
            }
            else {
                return Observable.just(result)
            }
        }
        observable.observeOn(scheduler)
    }

    /**
     * Create an observable from the given callable
     *
     * @param callable The callable
     * @return The {@link Observable}
     */
    static <T> Single<T> createSingle(Callable<T> callable) {
        Scheduler scheduler = Schedulers.io()
        createSingle(scheduler, callable)
    }

    /**
     * Create an observable from the given callable on the given scheduler
     *
     * @param callable The callable
     * @return The {@link Observable}
     */
    static <T> Single<T> createSingle(Scheduler scheduler, Callable<T> callable) {
        Single<T> single = Single.defer {
            T result = callable.call()
            if (result == null) {
                // A Single cannot complete without a value: report a missing result the way
                // RxJava's own firstOrError() does
                return Single.<T>error(new NoSuchElementException('The operation produced no result'))
            }
            return Single.just(result)
        }
        single.observeOn(scheduler)
    }
}
