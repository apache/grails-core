package org.grails.async.factory.future

import grails.async.Promise
import grails.async.PromiseFactory
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.grails.async.factory.BoundPromise

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock

/**
 * A child promise of a {@link FutureTaskPromise}
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
@PackageScope
class FutureTaskChildPromise<T> implements Promise<T> {

    final Promise<T> parent
    final Closure<T> callable
    final PromiseFactory promiseFactory

    private Collection<FutureTaskChildPromise> failureCallbacks = new ConcurrentLinkedQueue<>()
    private Collection<FutureTaskChildPromise> successCallbacks = new ConcurrentLinkedQueue<>()

    private Promise<T> bound = null

    FutureTaskChildPromise(PromiseFactory promiseFactory, Promise<T> parent, Closure<T> callable) {
        this.parent = parent
        this.callable = promiseFactory.applyDecorators(callable,null)
        this.promiseFactory = promiseFactory
    }

    @Override
    Promise<T> accept(T value) {
        try {
            T transformedValue = callable.call(value)
            bound = new BoundPromise<T>(transformedValue)
            for (callback in successCallbacks) {
                callback.accept(transformedValue)
            }
        } catch (Throwable e) {
            for (callback in failureCallbacks) {
                callback.accept(e)
            }
            throw e
        }
        return bound
    }

    @Override
    Promise<T> onComplete(Closure<T> callable) {
        Promise<T> newPromise = new FutureTaskChildPromise(promiseFactory, this as Promise<T>, callable)
        successCallbacks.add(newPromise)
        return newPromise
    }

    @Override
    Promise<T> onError(Closure<T> callable) {
        Promise<T> newPromise = new FutureTaskChildPromise(promiseFactory, this as Promise<T>, callable)
        failureCallbacks.add(newPromise)
        return newPromise
    }

    @Override
    Promise<T> then(Closure<T> callable) {
        return onComplete(callable)
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
        return bound != null
    }

    @Override
    T get() throws InterruptedException, ExecutionException {
        if (bound != null) {
            return bound.get()
        }
        else {
            if (parent instanceof FutureTaskPromise) {
                def value = parent.get()
                if (bound == null) {
                    def v = callable.call(value)
                    bound = new BoundPromise<>(v)
                }
                return bound.get()
            }
            else {
                def v = callable.call(parent.get())
                bound = new BoundPromise<>(v)
                return v
            }
        }
    }

    @Override
    T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (bound != null) {
            return bound.get()
        }
        else {
            if (parent instanceof FutureTaskPromise) {
                def value = parent.get(timeout, unit)
                if (bound == null) {
                    def v = callable.call(value)
                    bound = new BoundPromise<>(v)
                }
                return bound.get()
            }
            else {
                def v = callable.call(parent.get(timeout, unit))
                bound = new BoundPromise<>(v)
                return v
            }
        }
    }
}
