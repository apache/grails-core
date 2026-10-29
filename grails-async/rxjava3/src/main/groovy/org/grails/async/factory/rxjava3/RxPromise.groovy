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

package org.grails.async.factory.rxjava3

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.core.SingleOnSubscribe
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.subjects.ReplaySubject
import io.reactivex.rxjava3.subjects.Subject

import grails.async.Promise
import org.grails.async.factory.BoundPromise

/**
 * Promise based on RxJava 3.x
 *
 * <p>RxJava 3 does not allow {@code null} to travel through a stream, so a {@code null} value is carried through the
 * stream as {@link #NULL_VALUE} and turned back into {@code null} wherever a value leaves it: in {@link #get()} and in
 * the value an {@link #onComplete(Closure)} callback receives. A promise of {@code null} therefore behaves as it does
 * with every other promise factory.
 *
 * @since 7.0
 */
@AutoFinal
@CompileStatic
@PackageScope
class RxPromise<T>  implements Promise<T> {

    /**
     * Stands in for {@code null} inside the RxJava stream, which cannot carry {@code null} itself.
     */
    static final Object NULL_VALUE = new Object()

    protected final Subject<T> subject
    protected final RxPromiseFactory promiseFactory
    protected final Observable<T> observable

    protected Disposable subscription
    protected boolean finished = false

    RxPromise(RxPromiseFactory promiseFactory, Closure callable, Scheduler scheduler) {
        this(promiseFactory, Single.create({ SingleEmitter<? super T> singleSubscriber ->
            try {
                singleSubscriber.onSuccess((T) runCallable(callable))
            } catch (Throwable t) {
                singleSubscriber.onError(t)
            }
        } as SingleOnSubscribe<T>)
                .subscribeOn(scheduler))
    }

    RxPromise(RxPromiseFactory promiseFactory, Observable single) {
        this(promiseFactory, single, ReplaySubject.create(1))
    }

    RxPromise(RxPromiseFactory promiseFactory, Single single) {
        this(promiseFactory, single, ReplaySubject.create(1))
    }

    RxPromise(RxPromiseFactory promiseFactory, Single single, Subject subject) {
        this(promiseFactory, single.toObservable(), subject)
    }

    RxPromise(RxPromiseFactory promiseFactory, Observable observable, Subject subject) {
        this.observable = observable
        this.promiseFactory = promiseFactory
        observable.subscribe(new Observer<T>() {
            @Override
            void onSubscribe(Disposable d) {
                subscription = d
            }

            @Override
            void onNext(T t) {
                subject.onNext(t)
            }

            @Override
            void onError(Throwable e) {
                subject.onError(e)
            }

            @Override
            void onComplete() {
                finished = true
                subject.onComplete()
            }
        })
        this.subject = subject
    }

    /**
     * @return the result of this promise, which replays the value once it is known, rather than its source, which
     * would run a task again for every subscriber
     */
    Observable<T> toObservable() {
        return this.subject
    }

    @Override
    Promise<T> accept(T value) {
        return new BoundPromise<T>(value)
    }

    @Override
    Promise<T> onComplete(Closure<T> callable) {
        def decoratedCallable = promiseFactory.applyDecorators(callable, null)
        Function<T, T> restoringCallable = { T value ->
            (T) wrap(decoratedCallable.call(unwrap(value)))
        } as Function<T, T>
        return new RxPromise<T>(promiseFactory, subject.map(restoringCallable))
    }

    @Override
    Promise<T> onError(Closure<T> callable) {
        def decoratedCallable = promiseFactory.applyDecorators(callable, null)
        return new RxPromise<T>(promiseFactory, subject.doOnError(decoratedCallable as Consumer<Throwable>))
    }

    @Override
    Promise<T> then(Closure<T> callable) {
        return onComplete(callable)
    }

    @Override
    boolean cancel(boolean mayInterruptIfRunning) {
        if (subscription != null) {
            subscription.dispose()
            return subscription.isDisposed()
        }
        return false
    }

    @Override
    boolean isCancelled() {
        if (subscription == null) {
            return false
        }
        else {
            return subscription.isDisposed()
        }
    }

    @Override
    boolean isDone() {
        return finished
    }

    @Override
    T get() throws InterruptedException, ExecutionException {
        return (T) unwrap(subject.blockingFirst())
    }

    @Override
    T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return (T) unwrap(subject.timeout(timeout, unit).blockingFirst())
        } catch (Throwable e) {
            if (e.cause instanceof TimeoutException) {
                throw e.cause
            }
            else {
                throw e
            }
        }
    }

    static Object runCallable(Closure callable) {
        return wrap(callable.call())
    }

    /**
     * @return the value as it travels through the stream: {@link #NULL_VALUE} for {@code null}, the value itself otherwise
     */
    static Object wrap(Object value) {
        return value == null ? NULL_VALUE : value
    }

    /**
     * @return the value as the promise hands it out: {@code null} for {@link #NULL_VALUE}, the value itself otherwise
     */
    static Object unwrap(Object value) {
        return value.is(NULL_VALUE) ? null : value
    }
}
