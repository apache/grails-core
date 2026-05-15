/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.proxy

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.annotation.Entity
import org.apache.grails.data.testing.tck.domains.Location
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy

/**
 * Direct coverage tests for {@link ByteBuddyGroovyInterceptor#intercept}.
 * <p>
 * Tests operate against real Hibernate lazy proxies obtained via
 * {@code hibernateSession.getReference()} and exercise each branch of
 * {@code intercept()} by calling different method types on the proxy in
 * both the uninitialized and initialized states.
 */
class ByteBuddyGroovyInterceptorSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([Location])
    }

    private Long savedId

    def setup() {
        Location.withTransaction {
            savedId = new Location(name: 'Springfield', code: 'SP1').save(flush: true).id
        }
        manager.session.clear()
        manager.hibernateSession.clear()
    }

    void 'ident() on uninitialized proxy returns identifier without initialization'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId)

        expect:
        !Hibernate.isInitialized(proxy)
        proxy.ident() == savedId
        !Hibernate.isInitialized(proxy)
    }

    void 'toString() on uninitialized proxy returns entityName:id without initialization'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId)

        when:
        String s = proxy.toString()

        then:
        !Hibernate.isInitialized(proxy)
        s.contains(savedId.toString())
    }

    void 'isDirty() on uninitialized proxy returns false without initialization'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId)

        expect:
        !Hibernate.isInitialized(proxy)
        !proxy.isDirty()
        !Hibernate.isInitialized(proxy)
    }

    void 'metaClass on uninitialized proxy returns metaclass without initialization'() {

        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId)

        when:
        def mc = proxy.metaClass

        then:
        !Hibernate.isInitialized(proxy)
        mc != null
    }

    void 'accessing a regular property initializes the proxy and returns the value'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId) as Location

        when:
        String name = proxy.name

        then:
        Hibernate.isInitialized(proxy)
        name == 'Springfield'
    }

    void 'getProperty via Groovy on initialized proxy delegates via reflection'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId) as Location
        proxy.name // initialize

        when:
        def result = proxy.getProperty('name')

        then:
        Hibernate.isInitialized(proxy)
        result == 'Springfield'
    }

    void 'invokeMethod via Groovy on initialized proxy delegates via reflection'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId) as Location
        proxy.name // initialize

        when:
        def result = proxy.invokeMethod('namedAndCode', [] as Object[])

        then:
        Hibernate.isInitialized(proxy)
        result == 'Springfield - SP1'
    }

    void 'id property on uninitialized proxy returns identifier without initialization'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId) as Location

        expect:
        !Hibernate.isInitialized(proxy)
        proxy.id == savedId
        !Hibernate.isInitialized(proxy)
    }

    void 'getIdentifier() on uninitialized proxy returns identifier without initialization'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId)

        expect:
        !Hibernate.isInitialized(proxy)
        // This should trigger the 'getIdentifier' branch in the interceptor
        proxy.getIdentifier() == savedId
        !Hibernate.isInitialized(proxy)
    }

    void 'setProperty on uninitialized proxy initializes the proxy'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId) as Location

        when:
        proxy.setProperty('name', 'New Name')

        then:
        Hibernate.isInitialized(proxy)
        proxy.name == 'New Name'
    }

    void 'setMetaClass on uninitialized proxy initializes the proxy'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId) as Location

        when:
        proxy.setMetaClass(proxy.getMetaClass())

        then:
        Hibernate.isInitialized(proxy)
    }

    void 'Groovy method throwing exception is handled'() {
        given:
        def proxy = manager.hibernateSession.getReference(Location, savedId) as Location

        when:
        proxy.invokeMethod('throwError', [] as Object[])

        then:
        thrown(RuntimeException)
    }
}

@Entity
class Location implements Serializable {

    Long id
    String name
    String code

    Long getIdentifier() {
        return id
    }

    String namedAndCode() {
        "${name} - ${code}"
    }

    void throwError() {
        throw new RuntimeException('error')
    }
}
