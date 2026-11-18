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
package org.grails.datastore.rx.proxy

import grails.gorm.rx.PersistentObservable
import groovy.transform.PackageScope
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.query.QueryState
import rx.Observable
import rx.Subscriber
import rx.Subscription
import spock.lang.Specification

import java.lang.reflect.Method

class AbstractObservableProxyMethodHandlerSpec extends Specification {

    private TestObservableProxyMethodHandler newHandler(Observable observableToReturn = Observable.empty(),
                                                          Object delegateTarget = new RealTarget(),
                                                          Serializable keyToReturn = 7L) {
        new TestObservableProxyMethodHandler(RealTarget, RealTarget, new QueryState(), Stub(RxDatastoreClient),
                keyToReturn, observableToReturn, delegateTarget)
    }

    private static Method methodNamed(String name, Class... paramTypes) {
        Signatures.getMethod(name, paramTypes)
    }

    void "invoke() for subscribe(subscriber) resolves the observable and subscribes the given subscriber"() {
        given:
        List<String> received = []
        Observable observable = Observable.just('resolved')
        TestObservableProxyMethodHandler handler = newHandler(observable)
        Subscriber subscriber = new Subscriber<String>() {
            void onNext(String value) { received << value }
            void onError(Throwable error) { throw error }
            void onCompleted() { }
        }

        when:
        Object result = handler.invoke(new Object(), methodNamed('subscribe', Subscriber), null, [subscriber] as Object[])

        then:
        received == ['resolved']
        result instanceof Subscription
    }

    void "invoke() for toObservable() returns the resolved observable directly"() {
        given:
        Observable observable = Observable.just('value')
        TestObservableProxyMethodHandler handler = newHandler(observable)

        when:
        Object result = handler.invoke(new Object(), methodNamed('toObservable'), null, [] as Object[])

        then:
        result.is(observable)
    }

    void "invoke() for isProxy() always reports true"() {
        given:
        TestObservableProxyMethodHandler handler = newHandler()

        expect:
        handler.invoke(new Object(), methodNamed('isProxy'), null, [] as Object[]) == true
    }

    void "invoke() for getProxyKey() and getId() both delegate to the handler's key"() {
        given:
        TestObservableProxyMethodHandler handler = newHandler(Observable.empty(), new RealTarget(), 123L)

        expect:
        handler.invoke(new Object(), methodNamed('getProxyKey'), null, [] as Object[]) == 123L
        handler.invoke(new Object(), methodNamed('getId'), null, [] as Object[]) == 123L
    }

    void "invoke() for isInitialized() reflects whether the target has been resolved yet"() {
        given:
        TestObservableProxyMethodHandler handler = newHandler()

        expect: 'no target resolved yet'
        handler.invoke(new Object(), methodNamed('isInitialized'), null, [] as Object[]) == false

        when: 'the target is resolved as a side effect of getTarget()'
        handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[])

        then: 'the resolved target is now cached and reported as initialized'
        handler.invoke(new Object(), methodNamed('isInitialized'), null, [] as Object[]) == true
    }

    void "invoke() for getTarget() resolves the delegate once and caches it for subsequent calls"() {
        given:
        RealTarget target = new RealTarget()
        TestObservableProxyMethodHandler handler = newHandler(Observable.empty(), target)

        when:
        Object first = handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[])
        Object second = handler.invoke(new Object(), methodNamed('getTarget'), null, [] as Object[])

        then:
        first.is(target)
        second.is(target)
        handler.resolveDelegateInvocationCount == 2
    }

    void "invoke() for initialize() resolves the delegate and reports success as Void.class"() {
        given:
        RealTarget target = new RealTarget()
        TestObservableProxyMethodHandler handler = newHandler(Observable.empty(), target)

        when:
        Object result = handler.invoke(new Object(), methodNamed('initialize'), null, [] as Object[])

        then:
        result == Void.class
        handler.resolveDelegateInvocationCount == 1
    }

    void "invoke() for getMetaClass() lazily creates and then caches the MetaClass"() {
        given:
        TestObservableProxyMethodHandler handler = newHandler()

        when:
        Object first = handler.invoke(new Object(), methodNamed('getMetaClass'), null, [] as Object[])
        Object second = handler.invoke(new Object(), methodNamed('getMetaClass'), null, [] as Object[])

        then:
        first != null
        first.is(second)
    }

    void "invoke() falls back to reflectively invoking a matching method directly on the resolved target when its declaring class matches"() {
        given:
        RealTarget target = new RealTarget(name: 'Alice')
        TestObservableProxyMethodHandler handler = newHandler(Observable.empty(), target)
        Method method = RealTarget.getMethod('getName')

        when:
        Object result = handler.invoke(new Object(), method, null, [] as Object[])

        then:
        result == 'Alice'
    }

    void "invoke() falls back to a same-named public method found on the resolved target's own class when the target isn't an instance of the method's declaring class"() {
        given:
        UnrelatedGreeter target = new UnrelatedGreeter()
        TestObservableProxyMethodHandler handler = newHandler(Observable.empty(), target)
        Method method = Greetable.getMethod('greet')

        expect:
        !method.declaringClass.isInstance(target)

        when:
        Object result = handler.invoke(new Object(), method, null, [] as Object[])

        then:
        result == 'Hi there'
    }

    void "invoke() falls back to a same-named non-public method found on the resolved target's own class when the target isn't an instance of the method's declaring class"() {
        given:
        OtherSample target = new OtherSample()
        TestObservableProxyMethodHandler handler = newHandler(Observable.empty(), target)
        Method method = Sample.getDeclaredMethod('label')

        expect:
        !method.declaringClass.isInstance(target)
        !java.lang.reflect.Modifier.isPublic(method.modifiers)

        when:
        Object result = handler.invoke(new Object(), method, null, [] as Object[])

        then:
        result == 'other'
    }

    void "invoke() falls back to reflectively delegating toString() to the resolved target, with no special-casing"() {
        given:
        RealTarget target = new RealTarget(name: 'Bob')
        TestObservableProxyMethodHandler handler = newHandler(Observable.empty(), target)
        Method method = Object.getMethod('toString')

        when:
        Object result = handler.invoke(new Object(), method, null, [] as Object[])

        then:
        result == 'RealTarget(Bob)'
    }
}

interface Signatures {
    void subscribe(Subscriber subscriber)
    void toObservable()
    void isProxy()
    void getProxyKey()
    void getId()
    void isInitialized()
    void getTarget()
    void initialize()
    void getMetaClass()
}

interface Greetable {
    String greet()
}

class RealTarget {
    String name = 'default'

    String getName() { name }

    @Override
    String toString() { "RealTarget(${name})" }
}

class UnrelatedGreeter {
    String greet() { 'Hi there' }
}

class Sample {
    @PackageScope
    String label() { 'sample' }
}

class OtherSample {
    String label() { 'other' }
}

class TestObservableProxyMethodHandler extends AbstractObservableProxyMethodHandler {

    private final Observable observableToReturn
    private final Object delegateTarget
    private final Serializable keyToReturn
    int resolveDelegateInvocationCount = 0

    TestObservableProxyMethodHandler(Class proxyClass, Class type, QueryState queryState, RxDatastoreClient client,
                                      Serializable keyToReturn, Observable observableToReturn, Object delegateTarget) {
        super(proxyClass, type, queryState, client)
        this.keyToReturn = keyToReturn
        this.observableToReturn = observableToReturn
        this.delegateTarget = delegateTarget
    }

    @Override
    protected Observable resolveObservable() {
        observableToReturn
    }

    @Override
    protected Object resolveDelegate(Object self) {
        resolveDelegateInvocationCount++
        if (target == null) {
            target = delegateTarget
        }
        target
    }

    @Override
    protected Object getProxyKey(Object self) {
        keyToReturn
    }
}
