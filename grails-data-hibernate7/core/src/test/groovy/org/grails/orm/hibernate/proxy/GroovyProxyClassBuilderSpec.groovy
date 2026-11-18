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
package org.grails.orm.hibernate.proxy

import org.codehaus.groovy.runtime.InvokerHelper
import org.hibernate.HibernateException
import org.hibernate.bytecode.internal.bytebuddy.ByteBuddyState
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.ProxyConfiguration
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyHelper
import spock.lang.Specification

class GroovyProxyClassBuilderSpec extends Specification {

    private static final Class[] INTERFACES = [HibernateProxy] as Class[]

    ByteBuddyState state = new ByteBuddyState()
    ByteBuddyProxyHelper helper = new ByteBuddyProxyHelper(state)
    GroovyProxyClassBuilder builder = new GroovyProxyClassBuilder(helper, state)

    void "a Groovy entity gets a wrapper around the Hibernate proxy class that overrides getMetaClass()"() {
        when:
        Class<?> proxyClass = builder.build('GroovyProxyBuilderEntity', GroovyProxyBuilderEntity, INTERFACES)
        Class<?> stock = helper.buildProxy(GroovyProxyBuilderEntity, INTERFACES)

        then:
        proxyClass != stock
        stock.isAssignableFrom(proxyClass)
        GroovyProxyBuilderEntity.isAssignableFrom(proxyClass)
        proxyClass.getMethod('getMetaClass').declaringClass == proxyClass
    }

    void "the wrapper still declares the interfaces of the Hibernate proxy class"() {
        when:
        Class<?> proxyClass = builder.build('GroovyProxyBuilderEntity', GroovyProxyBuilderEntity, INTERFACES)

        then: 'code that looks for HibernateProxy among Class#getInterfaces() keeps recognising the proxy'
        proxyClass.interfaces.contains(HibernateProxy)
        ProxyConfiguration.isAssignableFrom(proxyClass)
    }

    void "the wrapper answers the entity's MetaClass before an interceptor is assigned"() {
        given:
        Class<?> proxyClass = builder.build('GroovyProxyBuilderEntity', GroovyProxyBuilderEntity, INTERFACES)

        when: 'the proxy is built the way a trait initializer sees it, with no interceptor'
        def proxy = proxyClass.getDeclaredConstructor().newInstance()

        then:
        proxy.getMetaClass() == InvokerHelper.getMetaClass(GroovyProxyBuilderEntity)
    }

    void "the wrapper is loaded through the same state as the stock proxy class, so building twice gives one class"() {
        when:
        Class<?> first = builder.build('GroovyProxyBuilderEntity', GroovyProxyBuilderEntity, INTERFACES)
        Class<?> second = builder.build('GroovyProxyBuilderEntity', GroovyProxyBuilderEntity, INTERFACES)

        then:
        first.is(second)
    }

    void "an entity that is not a Groovy object keeps the Hibernate proxy class"() {
        given:
        Class<?> stock = Mock(ProxyConfiguration).getClass()
        def stockHelper = Mock(ByteBuddyProxyHelper) {
            buildProxy(Object, _ as Class[]) >> stock
        }

        expect:
        new GroovyProxyClassBuilder(stockHelper, state).build('PlainEntity', Object, INTERFACES).is(stock)
    }

    void "a proxy class that cannot take an interceptor fails when it is built, naming the entity"() {
        given:
        def stockHelper = Mock(ByteBuddyProxyHelper) {
            buildProxy(Object, _ as Class[]) >> String
        }

        when:
        new GroovyProxyClassBuilder(stockHelper, state).build('PlainEntity', Object, INTERFACES)

        then:
        HibernateException e = thrown()
        e.message.contains('PlainEntity')
        e.message.contains('ProxyConfiguration')
    }
}

class GroovyProxyBuilderEntity {
    Long id
}
