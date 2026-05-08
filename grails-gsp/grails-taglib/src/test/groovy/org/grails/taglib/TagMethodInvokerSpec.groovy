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
package org.grails.taglib

import grails.gsp.NotATag
import spock.lang.Specification

class TagMethodInvokerSpec extends Specification {

    void "Error thrown by tag method propagates unwrapped"() {
        given:
        def tagLib = new ErrorThrowingTagLib()

        when:
        TagMethodInvoker.invokeTagMethod(tagLib, 'boom', [:], null)

        then:
        AssertionError e = thrown()
        e.message == 'boom!'
    }

    void "RuntimeException thrown by tag method propagates unwrapped"() {
        given:
        def tagLib = new ErrorThrowingTagLib()

        when:
        TagMethodInvoker.invokeTagMethod(tagLib, 'kapow', [:], null)

        then:
        IllegalStateException e = thrown()
        e.message == 'kapow!'
    }

    void "user override of Object methods is not exposed as a tag"() {
        expect:
        !TagMethodInvoker.getInvokableTagMethodNames(ObjectOverrideTagLib).contains('toString')
        !TagMethodInvoker.getInvokableTagMethodNames(ObjectOverrideTagLib).contains('hashCode')
        !TagMethodInvoker.getInvokableTagMethodNames(ObjectOverrideTagLib).contains('equals')
    }

    void "Spring lifecycle methods are not exposed as tags"() {
        expect:
        !TagMethodInvoker.getInvokableTagMethodNames(LifecycleTagLib).contains('destroy')
        !TagMethodInvoker.getInvokableTagMethodNames(LifecycleTagLib).contains('onApplicationEvent')
    }

    void "@NotATag method is not exposed as a tag"() {
        when:
        Collection<String> names = TagMethodInvoker.getInvokableTagMethodNames(OptedOutTagLib)

        then:
        names.contains('publicTag')
        !names.contains('helperMethod')
    }

    void "closure tag is resolved from declared field"() {
        given:
        def tagLib = new ClosureTagLib()

        expect:
        TagMethodInvoker.getClosureTagProperty(tagLib, 'greeting') instanceof Closure
        TagMethodInvoker.getClosureTagProperty(tagLib, 'doesNotExist') == null
    }

    void "closure tag in subclass shadows superclass field of same name"() {
        given:
        def tagLib = new SubclassClosureTagLib()

        when:
        Closure closure = (Closure) TagMethodInvoker.getClosureTagProperty(tagLib, 'greeting')

        then:
        closure.call() == 'subclass'
    }

    void "non-closure subclass field shadows superclass closure"() {
        given:
        def tagLib = new ShadowingNonClosureTagLib()

        expect:
        TagMethodInvoker.getClosureTagProperty(tagLib, 'greeting') == null
    }

    void "static closure field is not treated as a tag"() {
        given:
        def tagLib = new StaticClosureTagLib()

        expect:
        TagMethodInvoker.getClosureTagProperty(tagLib, 'staticGreeting') == null
    }

    void "binding by parameter name does not silently use the only attribute when names mismatch"() {
        given:
        def tagLib = new TypedParamTagLib()

        when: 'the attribute name does not match the parameter name'
        TagMethodInvoker.invokeTagMethod(tagLib, 'greet', [foo: 'bar'], null)

        then: 'the overload is rejected rather than silently binding bar to name'
        thrown(MissingMethodException)
    }

    void "missing attribute rejects an overload that requires a parameter"() {
        given:
        def tagLib = new TypedParamTagLib()

        when:
        TagMethodInvoker.invokeTagMethod(tagLib, 'greet', [:], null)

        then:
        thrown(MissingMethodException)
    }

    void "null reference attribute is bound to a reference-typed parameter"() {
        given:
        def tagLib = new TypedParamTagLib()

        expect:
        TagMethodInvoker.invokeTagMethod(tagLib, 'greet', [name: null], null) == 'hello null'
    }

    void "null attribute rejects a primitive-typed parameter"() {
        given:
        def tagLib = new TypedParamTagLib()

        when:
        TagMethodInvoker.invokeTagMethod(tagLib, 'pageNumber', [page: null], null)

        then:
        thrown(MissingMethodException)
    }

    void "same-arity overloads dispatch correctly regardless of JVM method-order"() {
        given:
        def tagLib = new SameArityOverloadsTagLib()

        expect:
        TagMethodInvoker.invokeTagMethod(tagLib, 'render', [name: 'Ada'], null) == 'name:Ada'
        TagMethodInvoker.invokeTagMethod(tagLib, 'render', [count: 7], null) == 'count:7'
    }
}

class ErrorThrowingTagLib {
    def boom() {
        throw new AssertionError((Object) 'boom!')
    }

    def kapow() {
        throw new IllegalStateException('kapow!')
    }
}

class ObjectOverrideTagLib {
    @Override
    String toString() { 'override' }

    @Override
    int hashCode() { 42 }

    @Override
    boolean equals(Object other) { other instanceof ObjectOverrideTagLib }

    def realTag() { 'tag' }
}

class LifecycleTagLib {
    void destroy() {}

    void onApplicationEvent(Object event) {}

    def realTag() { 'tag' }
}

class OptedOutTagLib {
    def publicTag() { 'tag' }

    @NotATag
    def helperMethod(Date when) { when?.toString() }
}

class ClosureTagLib {
    Closure greeting = { -> 'hello' }
}

class SubclassClosureTagLib extends ClosureTagLib {
    Closure greeting = { -> 'subclass' }
}

class ShadowingNonClosureTagLib extends ClosureTagLib {
    private String greeting = 'not a closure'
}

class StaticClosureTagLib {
    static Closure staticGreeting = { -> 'static' }
}

class TypedParamTagLib {
    def greet(String name) { "hello ${name}" }

    def pageNumber(int page) { "page ${page}" }
}

class SameArityOverloadsTagLib {
    def render(String name) { "name:${name}" }

    def render(int count) { "count:${count}" }
}
