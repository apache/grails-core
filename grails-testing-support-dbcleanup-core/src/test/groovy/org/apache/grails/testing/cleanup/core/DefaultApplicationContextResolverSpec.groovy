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

package org.apache.grails.testing.cleanup.core

import spock.lang.Specification

import org.spockframework.runtime.extension.IMethodInvocation

import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestContext

class DefaultApplicationContextResolverSpec extends Specification {

    def cleanup() {
        TestContextHolderListener.CURRENT.remove()
    }

    def "resolve returns ApplicationContext from TestContextHolderListener ThreadLocal"() {
        given:
        def appCtx = Mock(ApplicationContext)
        def testContext = Mock(TestContext) {
            getApplicationContext() >> appCtx
        }
        TestContextHolderListener.CURRENT.set(testContext)

        def resolver = new DefaultApplicationContextResolver()
        def invocation = Mock(IMethodInvocation)

        when:
        ApplicationContext result = resolver.resolve(invocation)

        then:
        result.is(appCtx)
    }

    def "resolve throws IllegalStateException when ThreadLocal is empty"() {
        given:
        def resolver = new DefaultApplicationContextResolver()
        def invocation = Mock(IMethodInvocation)

        when:
        resolver.resolve(invocation)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('Could not resolve ApplicationContext')
    }

    def "resolve throws IllegalStateException when TestContext has null ApplicationContext"() {
        given:
        def testContext = Mock(TestContext) {
            getApplicationContext() >> null
        }
        TestContextHolderListener.CURRENT.set(testContext)

        def resolver = new DefaultApplicationContextResolver()
        def invocation = Mock(IMethodInvocation)

        when:
        resolver.resolve(invocation)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('Could not resolve ApplicationContext')
    }

    def "resolver has a no-arg constructor"() {
        when:
        def resolver = DefaultApplicationContextResolver.getDeclaredConstructor().newInstance()

        then:
        resolver instanceof ApplicationContextResolver
    }

    def "resolver implements ApplicationContextResolver interface"() {
        expect:
        ApplicationContextResolver.isAssignableFrom(DefaultApplicationContextResolver)
    }
}
