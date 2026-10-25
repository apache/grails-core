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

import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic

import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Hibernate
import org.hibernate.HibernateException
import org.hibernate.Session
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyHelper
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ByteBuddyGroovyProxyFactorySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(GroovyProxyFactoryEntity)

    ByteBuddyProxyHelper helper = new GrailsBytecodeProvider().getByteBuddyProxyHelper()

    void "factory can be instantiated"() {
        expect:
        new ByteBuddyGroovyProxyFactory(Mock(ByteBuddyProxyHelper)) != null
    }

    void "postInstantiate wraps the Hibernate proxy class so getMetaClass() can answer before the interceptor is assigned"() {
        given:
        def factory = new ByteBuddyGroovyProxyFactory(helper)

        when:
        factory.postInstantiate('GroovyProxyFactoryEntity', GroovyProxyFactoryEntity, [HibernateProxy] as Set, null, null, null)

        then: 'the proxy class subclasses the Hibernate proxy class and overrides getMetaClass()'
        def proxyClass = proxyClassOf(factory)
        def stock = proxyClass.superclass
        HibernateProxy.isAssignableFrom(stock)
        GroovyProxyFactoryEntity.isAssignableFrom(proxyClass)
        proxyClass.getMethod('getMetaClass').declaringClass == proxyClass
    }

    void "postInstantiate fails when the generated proxy class cannot take an interceptor, naming the entity"() {
        given:
        def stockHelper = Mock(ByteBuddyProxyHelper) {
            buildProxy(Object, _ as Class[]) >> String
        }
        def factory = new ByteBuddyGroovyProxyFactory(stockHelper)

        when:
        factory.postInstantiate('PlainEntity', Object, [] as Set, null, null, null)

        then: 'it fails when the factory is set up, not on the first lazy load'
        HibernateException e = thrown()
        e.message.contains('PlainEntity')
    }

    void "postInstantiate fails when the entity has no default constructor, naming the entity"() {
        given:
        def factory = new ByteBuddyGroovyProxyFactory(helper)

        when:
        factory.postInstantiate('GroovyProxyNoDefaultConstructorEntity', GroovyProxyNoDefaultConstructorEntity, [HibernateProxy] as Set, null, null, null)

        then:
        HibernateException e = thrown()
        e.message.contains('GroovyProxyNoDefaultConstructorEntity')
    }

    void "getProxy returns a proxy with the Groovy interceptor installed"() {
        given:
        def factory = new ByteBuddyGroovyProxyFactory(helper)
        factory.postInstantiate(GroovyProxyFactoryEntity.name, GroovyProxyFactoryEntity, [HibernateProxy] as Set,
                GroovyProxyFactoryEntity.getMethod('getId'), GroovyProxyFactoryEntity.getMethod('setId', Long), null)
        def session = datastore.sessionFactory.openSession() as SharedSessionContractImplementor

        when:
        HibernateProxy proxy = factory.getProxy(1L, session)

        then:
        proxy instanceof GroovyProxyFactoryEntity
        proxy.getClass().interfaces.contains(HibernateProxy)
        lazyInitializerOf(proxy) instanceof ByteBuddyGroovyInterceptor
        lazyInitializerOf(proxy).identifier == 1L
        lazyInitializerOf(proxy).entityName == GroovyProxyFactoryEntity.name

        cleanup:
        session?.close()
    }

    void "getProxy wraps a failure in HibernateException"() {
        given: 'a factory that was never set up, so it has no proxy class to instantiate'
        def factory = new ByteBuddyGroovyProxyFactory(helper)

        when:
        factory.getProxy(1L, Mock(SharedSessionContractImplementor))

        then:
        thrown(HibernateException)
    }

    void "a proxy from getProxy survives Java serialization as the proxy its SessionFactory hands out"() {
        given:
        def factory = new ByteBuddyGroovyProxyFactory(helper)
        factory.postInstantiate(GroovyProxyFactoryEntity.name, GroovyProxyFactoryEntity, [HibernateProxy] as Set,
                GroovyProxyFactoryEntity.getMethod('getId'), GroovyProxyFactoryEntity.getMethod('setId', Long), null)
        Session session = datastore.sessionFactory.openSession()
        HibernateProxy proxy = factory.getProxy(1L, session as SharedSessionContractImplementor)

        when:
        def deserialized = roundTrip(proxy)

        then: 'the serialized form rebuilt a Groovy-aware proxy of the class the session itself hands out'
        deserialized instanceof GroovyProxyFactoryEntity
        deserialized.getClass().is(session.getReference(GroovyProxyFactoryEntity, 1L).getClass())
        !Hibernate.isInitialized(deserialized)
        lazyInitializerOf(deserialized) instanceof ByteBuddyGroovyInterceptor
        lazyInitializerOf(deserialized).identifier == 1L

        cleanup:
        session?.close()
    }

    @CompileStatic
    private static Object roundTrip(Object object) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { ObjectOutputStream out -> out.writeObject(object) }
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable { ObjectInputStream input ->
            input.readObject()
        }
    }

    @CompileStatic
    private static LazyInitializer lazyInitializerOf(HibernateProxy proxy) {
        proxy.getHibernateLazyInitializer()
    }

    private static Class<?> proxyClassOf(ByteBuddyGroovyProxyFactory factory) {
        def field = ByteBuddyGroovyProxyFactory.getDeclaredField('proxyClass')
        field.accessible = true
        field.get(factory) as Class<?>
    }
}

@Entity
class GroovyProxyFactoryEntity {
    Long id
}

class GroovyProxyNoDefaultConstructorEntity {
    Long id

    GroovyProxyNoDefaultConstructorEntity(Long id) {
        this.id = id
    }
}
