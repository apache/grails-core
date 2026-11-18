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
package org.grails.async.factory.gpars

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import groovyx.gpars.GParsConfig
import groovyx.gpars.dataflow.Dataflow
import groovyx.gpars.dataflow.DataflowVariable

import grails.async.Promise
import grails.async.PromiseList
import grails.async.factory.AbstractPromiseFactory

/**
 * GPars implementation of the {@link grails.async.PromiseFactory} interface
 *
 * @since 2.3
 * @deprecated GPars is superseded by Groovy 6's {@code groovy.concurrent} toolkit. Replace the
 * {@code grails-async-gpars} dependency with {@code grails-async-groovy-concurrent}, which provides
 * {@code org.grails.async.factory.groovy.AwaitablePromiseFactory}.
 */
@Deprecated(since = '9.0', forRemoval = true)
@Slf4j
@AutoFinal
@CompileStatic
class GparsPromiseFactory extends AbstractPromiseFactory {

    static final boolean GPARS_PRESENT
    static {
        try { GPARS_PRESENT = Thread.currentThread().contextClassLoader.loadClass('groovyx.gpars.GParsConfig') }
        catch (Throwable ignore) { GPARS_PRESENT = false }
    }
    static boolean isGparsAvailable() { GPARS_PRESENT }

    private static final AtomicBoolean DEPRECATION_LOGGED = new AtomicBoolean()

    private static final Closure<List<?>> originalValuesClosure = { List<?> values -> values } as Closure<List<?>>

    GparsPromiseFactory() {
        if (DEPRECATION_LOGGED.compareAndSet(false, true)) {
            log.warn('grails-async-gpars is deprecated and will be removed in a future release. ' +
                'Replace it with grails-async-groovy-concurrent, which uses the groovy.concurrent toolkit built into Groovy 6.')
        }
        try { GParsConfig.setPoolFactory(new LoggingPoolFactory()) }
        catch (IllegalArgumentException ignore) {}
    }

    @Override
    <T> Promise<T> createBoundPromise(T value) {
        def variable = new DataflowVariable()
        variable << value
        return new GparsPromise<T>(this, variable)
    }

    @Override
    <T> Promise<T> createPromise(Class<T> returnType) {
        return new GparsPromise<T>(this, new DataflowVariable())
    }

    @Override
    Promise<Object> createPromise() {
        return new GparsPromise<Object>(this, new DataflowVariable())
    }

    @Override
    <T> Promise<T> createPromise(Closure<T>... closures) {
        if (closures.length == 1) {
            def callable = closures[0]
            return new GparsPromise(this, applyDecorators(callable, null))
        }
        PromiseList<T> promiseList = new PromiseList<>()
        for (c in closures) {
            promiseList.add(new GparsPromise(this, applyDecorators(c, null)))
        }
        return promiseList as Promise<T>
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises) {
        def promise = Dataflow.whenAllBound(toGparsPromises(promises), originalValuesClosure) as groovyx.gpars.dataflow.Promise<List<T>>
        return promise.get()
    }

    @Override
    <T> List<T> waitAll(List<Promise<T>> promises, long timeout, TimeUnit units) {
        def promise = Dataflow.whenAllBound(toGparsPromises(promises), originalValuesClosure) as groovyx.gpars.dataflow.Promise<List<T>>
        return promise.get(timeout, units)
    }

    static <T> List<groovyx.gpars.dataflow.Promise<T>> toGparsPromises(List<Promise<T>> promises) {
        return promises.collect {
            (it as GparsPromise<T>).internalPromise as groovyx.gpars.dataflow.Promise<T>
        }
    }

    @Override
    <T> Promise<T> onComplete(List<Promise<T>> promises, Closure<T> callable) {
        return new GparsPromise<T>(
            this,
            Dataflow.whenAllBound(toGparsPromises(promises), callable as Closure)
        )
    }

    @Override
    <T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
        return new GparsPromise<List<T>>(
            this,
            Dataflow.whenAllBound(toGparsPromises(promises), {} as Closure<T>, callable as Closure<T>)
        )
    }
}
