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

import groovy.mock.interceptor.Demand
import groovy.mock.interceptor.LooseExpectation
import groovy.mock.interceptor.StrictExpectation
import spock.lang.Specification

class MockClosureProxySpec extends Specification {

    void 'the proxy checks the expectation before delegating to the target'() {
        given:
        Demand demand = new Demand()
        demand.save(1..1) { }
        LooseExpectation expectation = new LooseExpectation(demand)
        List calls = []
        Closure target = { Object... args -> calls << args.toList(); 'saved' }

        when:
        MockClosureProxy proxy = new MockClosureProxy(target, 'save', expectation)
        Object result = proxy.call('a', 1)

        then:
        result == 'saved'
        calls == [['a', 1]]
        proxy.@methodName == 'save'
        proxy.@expectation.is(expectation)
        proxy.equals(proxy)
        proxy.equals(target)
        !proxy.equals(new MockClosureProxy(target, 'save', expectation))
        proxy.hashCode() == target.hashCode()
        proxy.maximumNumberOfParameters == target.maximumNumberOfParameters
        proxy.parameterTypes == target.parameterTypes
        proxy.isCase('anything') == target.isCase('anything')
        proxy.asWritable() != null

        when: 'the same method is invoked beyond its demand'
        proxy.call()

        then:
        thrown(AssertionError)
    }

    void 'a strict expectation is accepted and unexpected calls fail'() {
        given:
        Demand demand = new Demand()
        demand.save(1..1) { }
        StrictExpectation expectation = new StrictExpectation(demand)

        when:
        MockClosureProxy proxy = new MockClosureProxy({ -> 'ok' }, 'delete', expectation)
        proxy.call()

        then:
        thrown(AssertionError)
    }

    void 'any other expectation type is rejected'() {
        when:
        new MockClosureProxy({ -> 'ok' }, 'save', 'not an expectation')

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith('Expectation must be either groovy.mock.interceptor.LooseExpectation or')
        e.message.endsWith('(actual class: class java.lang.String)')
    }

    void 'closure state is delegated to the target and currying wraps the result'() {
        given:
        Demand demand = new Demand()
        demand.save(1..3) { }
        LooseExpectation expectation = new LooseExpectation(demand)
        Closure target = { a, b -> "${a}-${b}-${delegate}" }
        Object delegate = new Object()

        when:
        MockClosureProxy proxy = new MockClosureProxy(target, 'save', expectation)
        proxy.delegate = delegate
        proxy.resolveStrategy = Closure.DELEGATE_FIRST
        proxy.directive = Closure.DONE
        Closure curried = proxy.curry('x')

        then:
        target.delegate.is(delegate)
        proxy.delegate.is(delegate)
        target.resolveStrategy == Closure.DELEGATE_FIRST
        proxy.resolveStrategy == Closure.DELEGATE_FIRST
        target.directive == Closure.DONE
        proxy.directive == Closure.DONE
        curried instanceof MockClosureProxy
        ((MockClosureProxy) curried).@methodName == 'save'
        curried.call('y') == "x-y-${delegate}"
        proxy.getProperty('delegate').is(delegate)
    }

}
