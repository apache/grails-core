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
package grails.test

import spock.lang.Specification

class AbstractClosureProxySpec extends Specification {

    static class RecordingProxy extends AbstractClosureProxy {

        List<String> events = []

        RecordingProxy(Closure<?> target) {
            super(target)
        }

        @Override
        protected void doBeforeCall(Object[] args) {
            events << "before${args.toList()}".toString()
        }

        @Override
        protected void doAfterCall(Object[] args) {
            events << "after${args.toList()}".toString()
        }

        @Override
        protected Closure<?> createWrapper(Closure<?> c) {
            new RecordingProxy(c)
        }

    }

    void 'a call is bracketed by the before and after hooks and returns the target result'() {
        given:
        RecordingProxy proxy = new RecordingProxy({ a, b -> a + b })

        expect:
        proxy.call(1, 2) == 3
        proxy.getEvents() == ['before[1, 2]', 'after[1, 2]']
    }

    void 'the after hook runs when the target throws'() {
        given:
        RecordingProxy proxy = new RecordingProxy({ -> throw new IllegalStateException('boom') })

        when:
        proxy.call()

        then:
        thrown(IllegalStateException)
        proxy.getEvents() == ['before[]', 'after[]']
    }

    void 'the proxy is created with the owner and this of the target'() {
        given:
        Closure target = { -> 1 }

        expect:
        new RecordingProxy(target).owner.is(target.owner)
        new RecordingProxy(target).thisObject.is(target.thisObject)
    }

    void 'the proxy equals itself and its target, and hashes as the target does'() {
        given:
        Closure target = { -> 1 }
        RecordingProxy proxy = new RecordingProxy(target)

        expect:
        proxy.equals(proxy)
        proxy.equals(target)
        !proxy.equals({ -> 1 })
        !proxy.equals(new RecordingProxy(target))
        proxy.hashCode() == target.hashCode()
    }

    void 'curry wraps the curried target in a new proxy of the same kind'() {
        given:
        RecordingProxy proxy = new RecordingProxy({ a, b -> a * b })

        when:
        Closure curried = proxy.curry(3)

        then:
        curried instanceof RecordingProxy
        curried.call(4) == 12
        ((RecordingProxy) curried).getEvents() == ['before[4]', 'after[4]']
    }

    void 'isCase and asWritable are answered by the target'() {
        given:
        RecordingProxy proxy = new RecordingProxy({ it > 5 })

        expect:
        proxy.isCase(10)
        !proxy.isCase(1)
        proxy.asWritable() instanceof Closure
        proxy.asWritable().call(1) == false
    }

    void 'properties are read from and written to the target'() {
        given:
        Closure target = { -> 1 }
        RecordingProxy proxy = new RecordingProxy(target)

        when:
        proxy.setProperty('delegate', 'd')

        then:
        target.delegate == 'd'
        proxy.getProperty('delegate') == 'd'
    }

    void 'closure state is read from and written to the target through the methods'() {
        given:
        Closure target = { a, b -> a }
        RecordingProxy proxy = new RecordingProxy(target)
        Object delegate = new Object()

        when:
        proxy.setDelegate(delegate)
        proxy.setResolveStrategy(Closure.DELEGATE_ONLY)
        proxy.setDirective(Closure.DONE)

        then:
        target.getDelegate().is(delegate)
        proxy.getDelegate().is(delegate)
        target.getResolveStrategy() == Closure.DELEGATE_ONLY
        proxy.getResolveStrategy() == Closure.DELEGATE_ONLY
        target.getDirective() == Closure.DONE
        proxy.getDirective() == Closure.DONE
        proxy.getMaximumNumberOfParameters() == 2
        proxy.getParameterTypes().length == 2
    }

}
