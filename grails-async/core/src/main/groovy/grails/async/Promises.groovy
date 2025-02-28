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
package grails.async

import grails.async.decorator.PromiseDecorator
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.grails.async.factory.PromiseFactoryBuilder

import java.util.concurrent.TimeUnit

/**
 * Factory class for working with {@link Promise} instances
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@AutoFinal
@CompileStatic
class Promises {

    static PromiseFactory promiseFactory

    private Promises() {}

    static PromiseFactory getPromiseFactory() {
        if (!promiseFactory) {
            promiseFactory = new PromiseFactoryBuilder().build()
        }
        return promiseFactory
    }

    static void setPromiseFactory(PromiseFactory promiseFactory) {
        Promises.@promiseFactory = promiseFactory
    }

    /**
     * @see PromiseFactory#waitAll(grails.async.Promise[])
     */
    static<T> List<T> waitAll(Promise<T>... promises) {
        return getPromiseFactory().waitAll(promises)
    }

    /**
     * @see PromiseFactory#waitAll(java.util.List)
     */
    static<T> List<T> waitAll(List<Promise<T>> promises) {
        return getPromiseFactory().waitAll(promises)
    }

    /**
     * @see PromiseFactory#waitAll(java.util.List)
     */
    static<T> List<T> waitAll(List<Promise<T>> promises, long timeout, TimeUnit units) {
        return getPromiseFactory().waitAll(promises, timeout, units)
    }

    /**
     * @see PromiseFactory#onComplete(java.util.List, groovy.lang.Closure)
     */
    static<T> Promise<List<T>> onComplete(List<Promise<T>> promises, Closure<T> callable) {
        return getPromiseFactory().onComplete(promises, callable)
    }
    /**
     * @see PromiseFactory#onError(java.util.List, groovy.lang.Closure)
     */
    static<T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
        return getPromiseFactory().onError(promises, callable)
    }
    /**
     * @see PromiseFactory#createPromise(java.util.Map)
     */
    static<K,V> Promise<Map<K,V>> createPromise(Map<K, V> map) {
        return getPromiseFactory().createPromise(map)
    }
    /**
     * @see PromiseFactory#createPromise(java.util.Map)
     */
    static<K,V> Promise<Map<K,V>> createPromise(Map<K, V> map, List<PromiseDecorator> decorators) {
        return getPromiseFactory().createPromise(map, decorators)
    }
    /**
     * @see PromiseFactory#createPromise(groovy.lang.Closure[])
     */
    static<T> Promise<T> createPromise(Closure<T>... closures) {
        return getPromiseFactory().createPromise(closures)
    }

    /**
     * @see PromiseFactory#createPromise(java.util.Map)
     */
    static<K,V> Promise<Map<K,V>> tasks(Map<K, V> map) {
        return getPromiseFactory().createPromise(map)
    }
    /**
     * @see PromiseFactory#createPromise(groovy.lang.Closure[])
     */
    static<T> Promise<T> task(Closure<T> closure) {
        return getPromiseFactory().createPromise(closure)
    }
    /**
     * @see PromiseFactory#createPromise(groovy.lang.Closure[])
     */
    static<T> Promise<T> tasks(Closure<T>... closures) {
        return getPromiseFactory().createPromise(closures)
    }
    /**
     * @see PromiseFactory#createPromise(groovy.lang.Closure[])
     */
    static<T> Promise<List<T>> tasks(List<Closure<T>> closures) {
        return getPromiseFactory().createPromise(closures)
    }

    /**
     * @see grails.async.PromiseFactory#createPromise()
     */
    static Promise<Void> createPromise() {
        return getPromiseFactory().createPromise()
    }

    /**
     * @see grails.async.PromiseFactory#createPromise(Class)
     */
    static<T> Promise<T> createPromise(Class<T> returnType) {
        return getPromiseFactory().createPromise(returnType)
    }

    /**
     * @see PromiseFactory#createPromise(groovy.lang.Closure, java.util.List)
     */
    static<T> Promise<T> createPromise(Closure<T> closure, List<PromiseDecorator> decorators) {
        return getPromiseFactory().createPromise(closure, decorators)
    }
    /**
     * @see PromiseFactory#createPromise(java.util.List, java.util.List)
     */
    static<T> Promise<List<T>> createPromise(List<Closure<T>> closures, List<PromiseDecorator> decorators) {
        return getPromiseFactory().createPromise(closures, decorators)
    }
    /**
     * @see PromiseFactory#createPromise(grails.async.Promise[])
     */
    static <T> Promise<List<T>> createPromise(Promise<T>...promises) {
        return getPromiseFactory().createPromise(promises)
    }

    /**
     * @see PromiseFactory#createBoundPromise(java.lang.Object)
     */
    static<T> Promise<T> createBoundPromise(T value) {
       return getPromiseFactory().createBoundPromise(value)
    }
}
