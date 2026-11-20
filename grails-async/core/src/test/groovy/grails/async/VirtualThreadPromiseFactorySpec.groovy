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
package grails.async

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

import org.grails.async.factory.PromiseFactoryBuilder
import org.grails.async.factory.SynchronousPromiseFactory
import org.grails.async.factory.future.VirtualThreadPromiseFactory
import spock.lang.Specification

class VirtualThreadPromiseFactorySpec extends Specification {

    private String originalPromiseFactoryProperty
    private PromiseFactory originalPromiseFactory

    def setup() {
        originalPromiseFactoryProperty = System.getProperty('grails.async.promiseFactory')
        originalPromiseFactory = Promises.promiseFactory
    }

    def cleanup() {
        if (originalPromiseFactoryProperty == null) {
            System.clearProperty('grails.async.promiseFactory')
        }
        else {
            System.setProperty('grails.async.promiseFactory', originalPromiseFactoryProperty)
        }
        Promises.promiseFactory = originalPromiseFactory
    }

    void 'builder can opt in to virtual thread promise factory'() {
        given:
        System.setProperty('grails.async.promiseFactory', 'virtual-thread')

        when:
        PromiseFactory factory = PromiseFactoryBuilder.build(executor)

        then:
        factory instanceof VirtualThreadPromiseFactory
        factory.createPromise { Thread.currentThread().isVirtual() }.get()

        cleanup:
        (factory as Closeable)?.close()

        where:
        executor << [null, { Runnable work -> work.run() } as Executor]
    }

    void 'virtual thread factory executes promises'() {
        given:
        def factory = new VirtualThreadPromiseFactory()

        when:
        Promise<Integer> promise = factory.createPromise { 21 * 2 }

        then:
        promise.get() == 42

        cleanup:
        factory.close()
    }

    void 'multi-closure promises use the virtual thread executor'() {
        given:
        def factory = new VirtualThreadPromiseFactory()
        // PromiseList normally delegates closure creation to the global factory. Using a
        // synchronous factory makes the unfixed implementation run on this test thread,
        // while the corrected implementation must still use this factory's virtual threads.
        Promises.promiseFactory = new SynchronousPromiseFactory()

        when:
        List<Boolean> result = (factory.createPromise(
            { Thread.currentThread().isVirtual() },
            { Thread.currentThread().isVirtual() }
        ) as Promise<List<Boolean>>).get()

        then:
        result == [true, true]

        cleanup:
        factory.close()
    }

    void 'closure lists use this factory rather than the global promise factory'() {
        given:
        def factory = new VirtualThreadPromiseFactory()
        Promises.promiseFactory = new SynchronousPromiseFactory()

        expect:
        factory.createPromise([
                { Thread.currentThread().isVirtual() },
                { Thread.currentThread().isVirtual() }
        ]).get() == [true, true]
        factory.createPromise((List<Closure<Boolean>>) null).get() == []

        cleanup:
        factory.close()
    }

    void 'onComplete resolves to the callback result like the default factory'() {
        given:
        def factory = new VirtualThreadPromiseFactory()
        List<Promise<Integer>> promises = [factory.createPromise { 1 }, factory.createPromise { 2 }]
        List<Integer> observed = null

        when: 'the returned promise is consumed as a statically-typed List, not just Object'
        Promise<List<Integer>> combined = factory.onComplete(promises) { List<Integer> values ->
            observed = values
            values.collect { it * 2 }
        }
        List<Integer> result = combined.get()

        then:
        result == [2, 4]
        observed == [1, 2]

        cleanup:
        factory.close()
    }

    void 'onError resolves to the waited values without invoking the callback when nothing fails'() {
        given:
        def factory = new VirtualThreadPromiseFactory()
        List<Promise<Integer>> promises = [factory.createPromise { 1 }, factory.createPromise { 2 }]
        boolean callbackInvoked = false

        when:
        Promise<List<Integer>> combined = factory.onError(promises) { callbackInvoked = true }
        List<Integer> result = combined.get()

        then:
        result == [1, 2]
        !callbackInvoked

        cleanup:
        factory.close()
    }

    void 'aggregate onError reports the original failure and preserves failed completion'() {
        given:
        def factory = new VirtualThreadPromiseFactory()
        def original = new IOException('boom')
        List<Promise<Integer>> promises = [factory.createPromise { throw original }]
        Throwable observed = null

        when:
        Promise<List<Integer>> combined = factory.onError(promises) { Throwable error -> observed = error }
        List<Integer> result = combined.get()

        then:
        def failure = thrown(ExecutionException)
        failure.cause.is(original)
        observed.is(original)

        cleanup:
        factory.close()
    }

    void 'close is idempotent'() {
        given:
        def factory = new VirtualThreadPromiseFactory()

        when:
        factory.close()
        factory.close()

        then:
        noExceptionThrown()
    }
}
