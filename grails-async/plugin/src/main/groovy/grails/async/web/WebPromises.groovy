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

package grails.async.web

import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic

import org.springframework.web.context.request.async.StandardServletAsyncWebRequest
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils

import grails.async.Promise
import grails.async.PromiseFactory
import grails.async.decorator.PromiseDecorator
import org.grails.async.factory.PromiseFactoryBuilder
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes

/**
 * A specific promises factory class designed for use in controllers and other web contexts
 *
 * @since 3.2.7
 * @author  Graeme Rocher
 */
@CompileStatic
class WebPromises {

    static PromiseFactory promiseFactory

    static PromiseFactory getPromiseFactory() {
        if (promiseFactory == null) {
            promiseFactory = new PromiseFactoryBuilder().build()
        }
        return promiseFactory
    }

    static void setPromiseFactory(PromiseFactory promiseFactory) {
        WebPromises.@promiseFactory = promiseFactory
    }

    private WebPromises() {}

    /**
     * @see grails.async.PromiseFactory#waitAll(grails.async.Promise[])
     */
    static<T> List<T> waitAll(Promise<T>...promises) {
        return getPromiseFactory().waitAll(promises)
    }

    /**
     * @see grails.async.PromiseFactory#waitAll(java.util.List)
     */
    static<T> List<T> waitAll(List<Promise<T>> promises) {
        return getPromiseFactory().waitAll(promises)
    }

    /**
     * @see grails.async.PromiseFactory#waitAll(java.util.List)
     */
    static<T> List<T> waitAll(List<Promise<T>> promises, final long timeout, final TimeUnit units) {
        return getPromiseFactory().waitAll(promises, timeout, units)
    }

    /**
     * @see grails.async.PromiseFactory#onComplete(java.util.List, groovy.lang.Closure)
     */
    static<T> Promise<List<T>> onComplete(List<Promise<T>> promises, Closure<T> callable) {
        return getPromiseFactory().onComplete(promises, callable)
    }
    /**
     * @see grails.async.PromiseFactory#onError(java.util.List, groovy.lang.Closure)
     */
    static<T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
        return getPromiseFactory().onError(promises, callable)
    }
    /**
     * @see grails.async.PromiseFactory#createPromise(java.util.Map)
     */
    static<K,V> Promise<Map<K,V>> createPromise(Map<K, V> map) {
        prepareAsyncRequest()
        return getPromiseFactory().createPromise(map)
    }
    /**
     * @see grails.async.PromiseFactory#createPromise(groovy.lang.Closure[])
     */
    static<T> Promise<List<T>> createPromise(Closure<T>... c) {
        prepareAsyncRequest()
        return getPromiseFactory().createPromise(Arrays.asList(c))
    }

    /**
     * @see grails.async.PromiseFactory#createPromise(java.util.Map)
     */
    static<K,V> Promise<Map<K,V>> tasks(Map<K, V> map) {
        return createPromise(map)
    }
    /**
     * @see grails.async.PromiseFactory#createPromise(groovy.lang.Closure[])
     */
    static<T> Promise<T> task(Closure<T> c) {
        prepareAsyncRequest()
        return getPromiseFactory().createPromise(c)
    }
    /**
     * @see grails.async.PromiseFactory#createPromise(groovy.lang.Closure[])
     */
    static<T> Promise<List<T>> tasks(Closure<T>... c) {
        return createPromise(c)
    }
    /**
     * @see grails.async.PromiseFactory#createPromise(groovy.lang.Closure[])
     */
    static<T> Promise<List<T>> tasks(List<Closure<T>> closures) {
        prepareAsyncRequest()
        return getPromiseFactory().createPromise(closures)
    }

    /**
     * @see grails.async.PromiseFactory#createPromise()
     */
    static Promise<Void> createPromise() {
        promiseFactory.createPromise()
    }

    /**
     * @see grails.async.PromiseFactory#createPromise(Class)
     */
    static<T> Promise<T> createPromise(Class<T> returnType) {
        return getPromiseFactory().createPromise(returnType)
    }

    /**
     * @see grails.async.PromiseFactory#createPromise(groovy.lang.Closure, java.util.List)
     */
    static<T> Promise<T> createPromise(Closure<T> c, List<PromiseDecorator> decorators) {
        prepareAsyncRequest()
        return getPromiseFactory().createPromise(c, decorators)
    }
    /**
     * @see grails.async.PromiseFactory#createPromise(java.util.List, java.util.List)
     */
    static<T> Promise<List<T>> createPromise(List<Closure<T>> closures, List<PromiseDecorator> decorators) {
        prepareAsyncRequest()
        return getPromiseFactory().createPromise(closures, decorators)
    }
    /**
     * @see grails.async.PromiseFactory#createPromise(grails.async.Promise[])
     */
    static <T> Promise<List<T>> createPromise(Promise<T>...promises) {
        return getPromiseFactory().createPromise(promises)
    }

    /**
     * @see grails.async.PromiseFactory#createBoundPromise(java.lang.Object)
     */
    static<T> Promise<T> createBoundPromise(T value) {
        return getPromiseFactory().createBoundPromise(value)
    }

    private static void prepareAsyncRequest() {
        GrailsWebRequest webRequest = GrailsWebRequest.lookup()
        if (webRequest == null) {
            return
        }

        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest.currentRequest)
        if (asyncManager.isConcurrentHandlingStarted()) {
            return
        }

        StandardServletAsyncWebRequest asyncWebRequest = new StandardServletAsyncWebRequest(
                webRequest.currentRequest,
                webRequest.currentResponse)
        asyncWebRequest.timeout = -1L
        asyncManager.asyncWebRequest = asyncWebRequest
        asyncWebRequest.startAsync()
        webRequest.currentRequest.setAttribute(GrailsApplicationAttributes.ASYNC_STARTED, true)
    }
}
