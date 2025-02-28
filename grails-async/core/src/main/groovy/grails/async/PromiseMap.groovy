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

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

import java.util.concurrent.TimeUnit

/**
 * A map-like structure for promises that allows waiting for all values in the map to be populated before
 * executing a callback
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@AutoFinal
@CompileStatic
class PromiseMap<K,V> implements Promise<Map<K,V>> {

    protected LinkedHashMap<K, Promise<V>> promises = [:]
    protected LinkedHashMap<Promise<V>, K> promisesKeys = [:]

    PromiseMap() {}

    PromiseMap(Map<K,V> values) {
        accept(values)
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
        return promisesKeys.keySet().every {it.isDone() }
    }

    @Override
    Promise<Map<K,V>> accept(Map<K,V> values) {
        values.each { K key, Object value ->
            if (value instanceof Promise) {
                put(key, value as Promise<V>)
            } else if (value instanceof Closure) {
                put(key, value as Closure<V>)
            } else {
                put(key, value as V)
            }
        }
        return this
    }

    /**
     * @return The size the map
     */
    int size() {
        return promises.size()
    }

    /**
     * @return Whether the map is empty
     */
    boolean isEmpty() {
        return promises.isEmpty()
    }

    /**
     * @param key The key
     * @return Whether the promise map contains the given key
     */
    boolean containsKey(K key) {
        return promises.containsKey(key)
    }

    /**
     * Gets a promise instance for the given key
     *
     * @param o The key
     * @return A promise
     */
    Promise<V> get(K key) {
        return promises.get(key)
    }

    /**
     * Put any value and return a promise for that value
     * @param key The key
     * @param value The value
     * @return The previous promise associated with the key or null
     */
    Promise<V> put(K key, V value) {
        return put(key, Promises.createBoundPromise(value))
    }

    /**
     * Adds a promise for the given key
     *
     * @param key The key
     * @param promise The promise
     * @return The previous promise associated with the key or null
     */
    Promise<V> put(K key, Promise<V> promise) {
        promisesKeys.put(promise, key)
        return promises.put(key, promise)
    }

    /**
     * Adds a callable for the given key
     *
     * @param key The key
     * @param callable The closure to call
     * @return The previous promise associated with the key or null
     */
    Promise<V> put(K key, Closure<V> callable) {
        def promise = Promises.createPromise(callable)
        promisesKeys.put(promise, key)
        return promises.put(key, promise)
    }

    /**
     * Gets a promise instance for the given key
     *
     * @param key The key
     * @return A promise
     */
    Promise<V> getAt(K key) {
        return get(key)
    }

    /**
     * Adds a promise for the given key
     *
     * @param key The key
     * @param promise The promise
     * @return The previous promise associated with the key or null
     */
    Promise<V> putAt(String key, Promise<V> promise) {
        return put(key as K, promise)
    }

    /**
     * Adds a callable for the given key
     *
     * @param key The key
     * @param callable The closure to call
     * @return The previous promise associated with the key or null
     */
    Promise<V> putAt(String key, Closure<V> callable) {
        return put(key as K, callable)
    }

    /**
     * Adds a value for the given key
     *
     * @param key The key
     * @param value The value
     * @return The previous promise associated with the key or null
     */
    Promise<V> putAt(String key, V value) {
        return put(key as K, value as V)
    }

    /**
     * Adds a promise for the given key
     *
     * @param key The key
     * @param promise The promise
     * @return The previous promise associated with the key or null
     */
    Promise<V> putAt(Integer key, Promise<V> promise) {
        return put(key as K, promise)
    }

    /**
     * Adds a promise for the given key
     *
     * @param key The key
     * @param callable The closure to call
     * @return The previous promise associated with the key or null
     */
    Promise<V> putAt(Integer key, Closure<V> callable) {
        return put(key as K, callable)
    }

    /**
     * Synchronously return the populated map with all values obtained from promises used
     * inside the populated map
     *
     * @return A map where the values are obtained from the promises
     */
    Map<K,V> get() throws Throwable {
        def promises = promises.values()
        Map<K,V> resultMap = [:]
        for(Promise<V> promise : promises) {
            V value = promise.get()
            resultMap.put(promisesKeys.get(promise), value)
        }
        return resultMap
    }

    /**
     * Synchronously return the populated map with all values obtained from promises used
     * inside the populated map
     *
     * @param  timeout The timeout period
     * @param units The timeout units
     * @return A map where the values are obtained from the promises
     */
    Map<K,V> get(long timeout, TimeUnit units) throws Throwable {
        List<Promise<V>> promises = new ArrayList<Promise<V>>(promises.values())
        Promises.waitAll(promises, timeout, units)
        Map<K,V> resultMap = [:]
        for(Promise<V> promise : promises) {
            V value = promise.get()
            resultMap.put(promisesKeys.get(promise), value)
        }
        return resultMap
    }

    @Override
    Promise<Map<K,V>> onComplete(Closure<Map<K,V>> callable) {
        List<Promise<V>> promises = new ArrayList<Promise<V>>(promises.values())
        Promises.onComplete(promises) { List<V> values ->
            Map<K,V> resultMap = [:]
            int i = 0
            for(V value in values) {
                Promise<V> promise = promises[i]
                K key = promisesKeys.get(promise)
                resultMap.put(key, value)
                i++
            }
            callable.call(resultMap)
            return resultMap
        }
        return this
    }

    @Override
    Promise<Map<K,V>> onError(Closure<Map<K,V>> callable) {
        Promises.onError(new ArrayList<Promise<V>>(promises.values()), callable)
        return this
    }

    @Override
    Promise<Map<K, V>> then(Closure<Map<K,V>> callable) {
        return onComplete(callable)
    }

    Promise<Map<K, V>> leftShift(Closure callable) {
        return then(callable)
    }
}
