package org.grails.async.factory.rxjava3

import grails.async.Promise
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
import org.grails.async.factory.BoundPromise

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Promise based on RxJava 3.x
 *
 * @since 7.0
 */
@AutoFinal
@CompileStatic
@PackageScope
class RxPromise<T>  implements Promise<T> {

    protected final Subject<T> subject
    protected final RxPromiseFactory promiseFactory
    protected final Observable<T> observable

    protected Disposable subscription
    protected boolean finished = false

    RxPromise(RxPromiseFactory promiseFactory, Closure callable, Scheduler scheduler) {
        this(promiseFactory, Single.create( { SingleEmitter<? super T> singleSubscriber ->
            try {
                singleSubscriber.onSuccess((T)runCallable(callable))
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
            }
        })
        this.subject = subject
    }

    Observable<T> toObservable() {
        return this.observable
    }

    @Override
    Promise<T> accept(T value) {
        return new BoundPromise<T>(value)
    }

    @Override
    Promise<T> onComplete(Closure<T> callable) {
        def decoratedCallable = promiseFactory.applyDecorators(callable, null)
        return new RxPromise<T>(promiseFactory, subject.map(decoratedCallable as Function<T, T>))
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
        if(subscription != null) {
            subscription.dispose()
            return subscription.isDisposed()
        }
        return false
    }

    @Override
    boolean isCancelled() {
        if(subscription == null) {
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
        return subject.blockingFirst()
    }

    @Override
    T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return subject.timeout(timeout, unit).blockingFirst()
        } catch (Throwable e) {
            if(e.cause instanceof TimeoutException) {
                throw e.cause
            }
            else {
                throw e
            }
        }
    }

    static Object runCallable(Closure callable) {
        Object rtn = callable.call()
        if(rtn == null) {
            return Void
        } else {
            return rtn
        }
    }
}
