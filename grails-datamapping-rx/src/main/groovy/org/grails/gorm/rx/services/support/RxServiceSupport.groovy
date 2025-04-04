package org.grails.gorm.rx.services.support

import groovy.transform.CompileStatic
import rx.Observable
import rx.Observer
import rx.Scheduler
import rx.Single
import rx.SingleSubscriber
import rx.Subscriber
import rx.observables.SyncOnSubscribe
import rx.schedulers.Schedulers

import java.util.concurrent.Callable

/**
 * Helper class for creating observables that run of the IO scheduler for blocking GORM operations
 *
 * @author Graeme Rocher
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
        Observable<T> observable = Observable.create(new SyncOnSubscribe() {
            @Override
            protected Object generateState() {
                def result = callable.call()
                if (result instanceof Iterable) {
                    return ((Iterable) result).iterator()
                }
                return result
            }

            @Override
            protected Object next(Object state, Observer observer) {
                if (state == null) {
                    observer.onCompleted()
                } else if (state instanceof Iterator) {
                    Iterator i = (Iterator) state
                    if (i.hasNext()) {
                        observer.onNext(i.next())
                    } else {
                        observer.onCompleted()
                    }

                } else {
                    observer.onNext(state)
                    observer.onCompleted()
                }
                return state
            }

        })
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
        Single<T> single = Single.create({ SingleSubscriber<? super T> singleSubscriber ->
            try {
                def result = callable.call()
                singleSubscriber.onSuccess(result)
            } catch (Throwable e) {
                singleSubscriber.onError(e)
            }
        } as Single.OnSubscribe)
        single.observeOn(scheduler)
    }
}
