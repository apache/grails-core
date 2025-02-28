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
import grails.async.decorator.PromiseDecoratorLookupStrategy

import java.util.concurrent.TimeUnit

/**
 * An interface capable of creating {@link Promise} instances. The {@link Promises} static methods use this
 * interface to create promises. The default Promise creation mechanism can be overridden by setting {@link Promises#setPromiseFactory(PromiseFactory)}
 *
 * @author Graeme Rocher
 * @since 2.3
 */
interface PromiseFactory {

    /**
     * Adds a PromiseDecoratorLookupStrategy. The strategy implementation must be stateless, do not add a strategy that contains state
     *
     * @param lookupStrategy The lookup strategy
     */
    void addPromiseDecoratorLookupStrategy(PromiseDecoratorLookupStrategy lookupStrategy)

    /**
     * Applies the registered decorators to the given closure
     * @param c The closure
     * @param decorators The decorators
     * @return The decorated closure
     */
    <T> Closure<T> applyDecorators(Closure<T> c, List<PromiseDecorator> decorators)

    /**
     * Creates a promise with a value pre-bound to it
     * @param value The value
     * @param <T> The type of the value
     * @return A Promise
     */
    <T> Promise<T> createBoundPromise(T value)

    /**
     * Creates an unfulfilled promise that returns the given type
     * @param returnType The return type
     * @param <T> The type of the class
     * @return The unfulfilled promise
     */
    <T> Promise<T> createPromise(Class<T> returnType)

    /**
     * Creates an unfulfilled promise that returns void
     *
     * @return The unfulfilled promise
     */
    Promise<Void> createPromise()

    /**
     * Creates a promise from the given map where the values of the map are either closures or Promise instances
     *
     * @param map The map
     * @return A promise
     */
    <K,V> Promise<Map<K,V>> createPromise(Map<K, V> map)

    /**
     * Creates a promise from the given map where the values of the map are either closures or Promise instances
     *
     * @param map The map
     * @return A promise
     */
    <K,V> Promise<Map<K,V>> createPromise(Map<K, V> map, List<PromiseDecorator> decorators)

    /**
     * Creates a promise from one or more other promises
     *
     * @param promises The promises
     * @return The promise
     */
    <T> Promise<List<T>> createPromise(Promise<T>...promises)

    /**
     * Creates a promise from one or many closures
     *
     * @param closures One or many closures
     * @return A promise
     */
    <T> Promise<T> createPromise(Closure<T>... closures)

    /**
     * Creates a promise from one or many closures
     *
     * @param closure A closure
     * @return A promise
     */
    <T> Promise<T> createPromise(Closure<T> closure, List<PromiseDecorator> decorators)

    /**
     * Creates a promise from one or many closures
     *
     * @param closures One or many closures
     * @return A promise
     */
    <T> Promise<List<T>> createPromise(List<Closure<T>> closures, List<PromiseDecorator> decorators)

    /**
     * Creates a promise from one or many closures
     *
     * @param closures One or many closures
     * @return A promise
     */
    <T> Promise<List<T>> createPromise(List<Closure<T>> closures)

    /**
     * Synchronously waits for all promises to complete returning a list of values
     *
     * @param promises The promises
     * @return The list of bound values
     */
    <T> List<T> waitAll(Promise<T>...promises)
    /**
     * Synchronously waits for all promises to complete returning a list of values
     *
     * @param promises The promises
     * @return The list of bound values
     */
    <T> List<T> waitAll(List<Promise<T>> promises)

    /**
     * Synchronously waits for all promises to complete returning a list of values
     *
     * @param promises The promises
     * @return The list of bound values
     */
    <T> List<T> waitAll(List<Promise<T>> promises, long timeout, TimeUnit units)

    /**
     * Executes the given callback when the list of promises completes
     *
     * @param promises The promises
     * @param callable The callback to execute
     */
    <T> Promise<List<T>> onComplete(List<Promise<T>> promises, Closure<T> callable)

    /**
     * Executes the given callback if an error occurs for the list of promises
     *
     * @param promises The promises The promises
     * @param callable The error callback to execute
     */
    <T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable)
}
