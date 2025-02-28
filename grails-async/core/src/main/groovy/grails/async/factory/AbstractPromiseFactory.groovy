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
package grails.async.factory

import grails.async.Promise
import grails.async.PromiseFactory
import grails.async.PromiseList
import grails.async.PromiseMap
import grails.async.decorator.PromiseDecorator
import grails.async.decorator.PromiseDecoratorLookupStrategy
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.grails.async.factory.BoundPromise

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Abstract implementation of the {@link grails.async.PromiseFactory} interface, subclasses should extend
 * this class to obtain common generic functionality
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@AutoFinal
@CompileStatic
abstract class AbstractPromiseFactory implements PromiseFactory {

    protected Collection<PromiseDecoratorLookupStrategy> lookupStrategies = new ConcurrentLinkedQueue<PromiseDecoratorLookupStrategy>()

    void addPromiseDecoratorLookupStrategy(PromiseDecoratorLookupStrategy lookupStrategy) {
        lookupStrategies.add(lookupStrategy)
    }

    <T> Promise<T> createBoundPromise(T value) {
        return new BoundPromise<T>(value)
    }

    /**
     * @see PromiseFactory#createPromise(groovy.lang.Closure, java.util.List)
     */
    <T> Promise<T> createPromise(Closure<T> closure, List<PromiseDecorator> decorators) {
        return createPromiseInternal(applyDecorators(closure, decorators))
    }

    <T> Closure<T> applyDecorators(Closure<T> closure, List<PromiseDecorator> decorators) {
        List<PromiseDecorator> allDecorators = decorators != null ? new ArrayList<PromiseDecorator>(decorators): new ArrayList<PromiseDecorator>()
        for (PromiseDecoratorLookupStrategy lookupStrategy : lookupStrategies) {
            allDecorators.addAll(lookupStrategy.findDecorators())
        }
        def decoratedClosure = closure
        if (!allDecorators.empty) {
            for(PromiseDecorator decorator : allDecorators) {
                decoratedClosure = decorator.decorate(decoratedClosure)
            }
        }
        return decoratedClosure
    }

    /**
     * @see PromiseFactory#createPromise(java.util.List)
     */
    <T> Promise<List<T>> createPromise(List<Closure<T>> closures) {
        return createPromise(closures,null)
    }

    /**
     * @see PromiseFactory#createPromise(java.util.List, java.util.List)
     */
    <T> Promise<List<T>> createPromise(List<Closure<T>> closures, List<PromiseDecorator> decorators) {
        List<Closure<T>> decoratedClosures = new ArrayList<Closure<T>>(closures.size())
        for (Closure<T> closure : closures) {
            decoratedClosures.add(applyDecorators(closure, decorators))
        }
        PromiseList<T> promiseList = new PromiseList<>()
        for (Closure<T> closure : decoratedClosures) {
            promiseList.add(closure)
        }
        return promiseList
    }

    /**
     * @see PromiseFactory#createPromise(grails.async.Promise[])
     */
    <T> Promise<List<T>> createPromise(Promise<T>... promises) {
        PromiseList<T> promiseList = new PromiseList<>()
        for(Promise<T> promise : promises) {
            promiseList.add(promise)
        }
        return promiseList
    }

    @Override
    <K, V> Promise<Map<K, V>> createPromise(Map<K, V> map, List<PromiseDecorator> decorators) {
        PromiseMap<K,V> promiseMap = new PromiseMap<>()
        map.forEach((K key, V value) -> {
            if (value instanceof Promise) {
                promiseMap.put(key, value as Promise<V>)
            }
            else if (value instanceof Closure) {
                Closure<V> closure = value as Closure<V>
                applyDecorators(closure, decorators)
                promiseMap.put(key, createPromiseInternal(closure))
            }
            else {
                promiseMap.put(key, new BoundPromise<V>(value))
            }

        })
        return promiseMap
    }

    /**
     * @see PromiseFactory#createPromise(java.util.Map)
     */
    <K, V> Promise<Map<K, V>> createPromise(Map<K, V> map) {
        return createPromise(map, Collections.<PromiseDecorator>emptyList())
    }

    protected <T> Promise<T> createPromiseInternal(Closure<T> closure) {
       return createPromise(closure)
    }

    /**
     * @see PromiseFactory#waitAll(grails.async.Promise[])
     */
    <T> List<T> waitAll(Promise<T>... promises) {
        return waitAll(Arrays.asList(promises))
    }
}
