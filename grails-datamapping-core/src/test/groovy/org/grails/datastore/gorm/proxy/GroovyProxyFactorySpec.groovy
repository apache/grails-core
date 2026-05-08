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
package org.grails.datastore.gorm.proxy

import org.grails.datastore.mapping.core.Session
import spock.lang.Specification

class GroovyProxyFactorySpec extends Specification {

    void "getProxiedClass returns the entity class for a proxy created by this factory"() {
        given:
        GroovyProxyFactory proxyFactory = new GroovyProxyFactory()

        // Mirror what GroovyProxyFactory.createProxy() does internally — attach a
        // ProxyInstanceMetaClass to a real instance of the entity class. Avoids the
        // need to mock EntityPersister (a class, not an interface — would require
        // byte-buddy or cglib on the test classpath).
        ProxyEntity proxy = new ProxyEntity()
        def session = Mock(Session)
        proxy.metaClass = new ProxyInstanceMetaClass(proxy.metaClass, session, 1L)

        expect: 'isProxy correctly identifies it as a proxy'
        proxyFactory.isProxy(proxy)

        and: 'getProxiedClass returns the entity class, not its superclass'
        // GroovyProxyFactory creates "metaclass-only" proxies: the proxy IS a real
        // instance of the entity class, just with a ProxyInstanceMetaClass attached.
        // getProxiedClass() must therefore return the entity class itself.
        // Bug: previously returned proxy.getClass().getSuperclass() (java.lang.Object),
        // which broke the unique-constraint validator's mappingContext.getPersistentEntity(name)
        // lookup whenever cascade validation hit a not-yet-unwrapped proxy.
        proxyFactory.getProxiedClass(proxy) == ProxyEntity
    }

    void "getProxiedClass returns the entity class for a non-proxy instance"() {
        given:
        GroovyProxyFactory proxyFactory = new GroovyProxyFactory()
        ProxyEntity instance = new ProxyEntity()

        expect:
        !proxyFactory.isProxy(instance)
        proxyFactory.getProxiedClass(instance) == ProxyEntity
    }
}

class ProxyEntity {
    Long id
    String name
}
