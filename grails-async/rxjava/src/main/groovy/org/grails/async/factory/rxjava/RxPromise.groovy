package org.grails.async.factory.rxjava

import grails.async.Promise
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.grails.async.factory.BoundPromise
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.SingleSubscriber
import rx.Subscription
import rx.functions.Action1
import rx.functions.Func1
import rx.subjects.ReplaySubject
import rx.subjects.Subject

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Promise based on RxJava 1.x
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
@PackageScope
class RxPromise<T>  implements Promise<T> {

    final Subject<T,T> subject
    final RxPromiseFactory promiseFactory
    protected Subscription subscription

    RxPromise(RxPromiseFactory promiseFactory, Closure callable, Scheduler scheduler) {
        this(promiseFactory, Single.create( { SingleSubscriber<? super T> singleSubscriber ->
            try {
                singleSubscriber.onSuccess((T)callable.call())
            } catch (Throwable t) {
                singleSubscriber.onError(t)
            }
        } as Single.OnSubscribe<T>)
        .subscribeOn(scheduler))
    }

    RxPromise(RxPromiseFactory promiseFactory, Observable single) {
        this(promiseFactory, single, ReplaySubject.create(1))
    }

    RxPromise(RxPromiseFactory promiseFactory, Single single) {
        this(promiseFactory, single, ReplaySubject.create(1))
    }

    RxPromise(RxPromiseFactory promiseFactory, Single single, Subject subject) {
        this.promiseFactory = promiseFactory
        this.subscription = single.subscribe(subject)
        this.subject = subject
    }

    RxPromise(RxPromiseFactory promiseFactory,Observable observable, Subject subject) {
        this.promiseFactory = promiseFactory
        this.subscription = observable.subscribe(subject)
        this.subject = subject
    }

    @Override
    Promise<T> accept(T value) {
        return new BoundPromise<T>(value)
    }

    @Override
    Promise<T> onComplete(Closure callable) {
        def decoratedCallable = promiseFactory.applyDecorators(callable, null)
        return new RxPromise<T>(promiseFactory,subject.map(decoratedCallable as Func1<T, T>))
    }

    @Override
    Promise<T> onError(Closure callable) {
        def decoratedCallable = promiseFactory.applyDecorators(callable, null)
        return new RxPromise<T>(promiseFactory,subject.doOnError(decoratedCallable as Action1<Throwable>))
    }

    @Override
    Promise<T> then(Closure callable) {
        return onComplete(callable)
    }

    @Override
    boolean cancel(boolean mayInterruptIfRunning) {
        if(subscription != null) {
            subscription.unsubscribe()
            return subscription.isUnsubscribed()
        }
        return false
    }

    @Override
    boolean isCancelled() {
        if(subscription == null) {
            return false
        }
        else {
            return subscription.isUnsubscribed()
        }
    }

    @Override
    boolean isDone() {
        throw new UnsupportedOperationException('Method isDone() not supported by RxJava implementation')
    }

    @Override
    T get() throws InterruptedException, ExecutionException {
        return subject.toBlocking().first()
    }

    @Override
    T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return subject.timeout(timeout, unit).toBlocking().first()
        } catch (Throwable e) {
            if(e.cause instanceof TimeoutException) {
                throw e.cause
            }
            else {
                throw e
            }
        }
    }
}
