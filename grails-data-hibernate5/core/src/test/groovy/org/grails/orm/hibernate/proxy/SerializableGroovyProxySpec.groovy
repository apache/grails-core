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
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SerializableGroovyProxySpec extends Specification {

    /** Lazy loading outside a transaction is how Hibernate initializes a deserialized proxy. */
    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            ['dataSource.dbCreate': 'create-drop', 'hibernate.enable_lazy_load_no_trans': true],
            SerializableProxyEntity)

    void "a proxy that never had a session reads back as a Groovy-aware proxy through the shared provider"() {
        given: 'a proxy built without a session, so its serialized form names no SessionFactory'
        ByteBuddyGroovyProxyFactory factory = new ByteBuddyGroovyProxyFactory(new GrailsBytecodeProvider().getByteBuddyProxyHelper(), true)
        factory.postInstantiate(SerializableProxyEntity.name, SerializableProxyEntity, [HibernateProxy] as Set,
                SerializableProxyEntity.getMethod('getId'), SerializableProxyEntity.getMethod('setId', Long), null)
        HibernateProxy proxy = factory.getProxy(7L, null)

        when:
        def first = roundTrip(proxy)
        def second = roundTrip(proxy)

        then: 'it is an uninitialized proxy of the entity with the Groovy interceptor, its identifier and lazy toString'
        first instanceof SerializableProxyEntity
        first instanceof HibernateProxy
        !Hibernate.isInitialized(first)
        lazyInitializerOf(first) instanceof ByteBuddyGroovyInterceptor
        lazyInitializerOf(first).identifier == 7L
        lazyInitializerOf(first).entityName == SerializableProxyEntity.name
        first.id == 7L
        first.toString() == "${SerializableProxyEntity.name}:7"
        !Hibernate.isInitialized(first)

        and: 'the proxy class is defined once and reused by later reads'
        first.getClass().is(second.getClass())
    }

    void "a proxy from a session reads back with the proxy class its SessionFactory uses"() {
        given:
        Long id = null
        SerializableProxyEntity.withTransaction {
            id = new SerializableProxyEntity(name: 'seed').save(flush: true, failOnError: true).id
        }

        when: 'an uninitialized proxy is serialized while its session is open, and read back'
        Class<?> proxyClass = null
        def deserialized = null
        SerializableProxyEntity.withTransaction {
            SerializableProxyEntity proxy = SerializableProxyEntity.load(id)
            proxyClass = proxy.getClass()
            deserialized = roundTrip(proxy)
        }

        then: 'the proxy class is the one the SessionFactory uses, and the proxy is still uninitialized'
        deserialized.getClass().is(proxyClass)
        !Hibernate.isInitialized(deserialized)
        lazyInitializerOf(deserialized) instanceof ByteBuddyGroovyInterceptor
        lazyInitializerOf(deserialized).identifier == id

        and: 'the serialized form kept what Hibernate needs to load it outside a session through that SessionFactory'
        deserialized.name == 'seed'
        Hibernate.isInitialized(deserialized)
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
    private static LazyInitializer lazyInitializerOf(Object proxy) {
        ((HibernateProxy) proxy).getHibernateLazyInitializer()
    }
}

@Entity
class SerializableProxyEntity {
    Long id
    String name
}
