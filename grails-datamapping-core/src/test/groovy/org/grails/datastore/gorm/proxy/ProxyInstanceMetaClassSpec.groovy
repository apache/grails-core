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
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification

class ProxyInstanceMetaClassSpec extends Specification {

    Session session = Mock(Session)
    MetaClass delegate = Mock(MetaClass)
    ProxyInstanceTestTarget target = new ProxyInstanceTestTarget()
    Object proxy = new Object()

    void setup() {
        delegate.getTheClass() >> ProxyInstanceTestTarget
    }

    ProxyInstanceMetaClass newMetaClass() {
        new ProxyInstanceMetaClass(delegate, session, 11L)
    }

    void "getKey returns the identifier without resolving the target"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()

        when:
        Serializable key = metaClass.getKey()
        boolean initiated = metaClass.isProxyInitiated()

        then:
        key == 11L
        !initiated
        0 * session.retrieve(_, _)
    }

    void "getProxyTarget lazily loads and caches the target from the session"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()

        when:
        Object first = metaClass.getProxyTarget()
        Object second = metaClass.getProxyTarget()

        then:
        1 * session.retrieve(ProxyInstanceTestTarget, 11L) >> target
        first.is(target)
        second.is(target)
        metaClass.isProxyInitiated()
    }

    void "getProxyTarget throws DataIntegrityViolationException when the associated instance no longer exists"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> null

        when:
        metaClass.getProxyTarget()

        then:
        thrown(DataIntegrityViolationException)
    }

    void "invokeMethod handles proxy-aware methods without resolving the target"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()

        when:
        Object isProxyResult = metaClass.invokeMethod(proxy, 'isProxy', [] as Object[])
        Object getIdResult = metaClass.invokeMethod(proxy, 'getId', [] as Object[])
        Object isInitializedResult = metaClass.invokeMethod(proxy, 'isInitialized', [] as Object[])
        Object getMetaClassResult = metaClass.invokeMethod(proxy, 'getMetaClass', [] as Object[])

        then:
        isProxyResult == true
        getIdResult == 11L
        isInitializedResult == false
        getMetaClassResult.is(metaClass)
        0 * session.retrieve(_, _)
    }

    void "invokeMethod resolves the target and delegates for getTarget/initialize"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target
        delegate.invokeMethod(target, methodName, [] as Object[]) >> target

        expect:
        metaClass.invokeMethod(proxy, methodName, [] as Object[]).is(target)

        where:
        methodName << ['getTarget', 'initialize']
    }

    void "invokeMethod delegates other calls against the resolved target"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target
        delegate.invokeMethod(target, 'toString', [] as Object[]) >> 'resolved'

        expect:
        metaClass.invokeMethod(proxy, 'toString', [] as Object[]) == 'resolved'
    }

    void "invokeMethod for getClass/getDomainClass only resolves once the proxy is already initiated"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        delegate.invokeMethod(proxy, methodName, [] as Object[]) >> ProxyInstanceTestTarget

        when: 'not yet initiated, so the delegate is called against the uninitialized proxy'
        Object result = metaClass.invokeMethod(proxy, methodName, [] as Object[])

        then:
        result == ProxyInstanceTestTarget
        0 * session.retrieve(_, _)

        where:
        methodName << ['getClass', 'getDomainClass']
    }

    void "invokeMethod for getClass/getDomainClass resolves the target once already initiated"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target
        metaClass.getProxyTarget()
        delegate.invokeMethod(target, 'getClass', [] as Object[]) >> ProxyInstanceTestTarget

        expect:
        metaClass.invokeMethod(proxy, 'getClass', [] as Object[]) == ProxyInstanceTestTarget
    }

    void "invokeMethod does not resolve the target for setMetaClass with a MetaClass argument"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        MetaClass newMetaClassArg = Mock(MetaClass)
        delegate.invokeMethod(proxy, 'setMetaClass', [newMetaClassArg] as Object[]) >> null

        when:
        metaClass.invokeMethod(proxy, 'setMetaClass', [newMetaClassArg] as Object[])

        then:
        0 * session.retrieve(_, _)
    }

    void "invokeMethod resolves the target for setMetaClass calls with a non-MetaClass argument"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        delegate.invokeMethod(target, 'setMetaClass', ['not-a-metaclass'] as Object[]) >> null

        when:
        metaClass.invokeMethod(proxy, 'setMetaClass', ['not-a-metaclass'] as Object[])

        then:
        1 * session.retrieve(ProxyInstanceTestTarget, 11L) >> target
    }

    void "invokeMethod resolves the target for setMetaClass calls with an unexpected argument count"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        delegate.invokeMethod(target, 'setMetaClass', [] as Object[]) >> null

        when:
        metaClass.invokeMethod(proxy, 'setMetaClass', [] as Object[])

        then:
        1 * session.retrieve(ProxyInstanceTestTarget, 11L) >> target
    }

    void "invokeMethod does not resolve the target for setMetaClass with a null argument"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        delegate.invokeMethod(proxy, 'setMetaClass', [null] as Object[]) >> null

        when:
        metaClass.invokeMethod(proxy, 'setMetaClass', [null] as Object[])

        then:
        0 * session.retrieve(_, _)
    }

    void "getProperty exposes proxy metadata without resolving the target"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()

        when:
        Object idResult = metaClass.getProperty(proxy, 'id')
        Object proxyResult = metaClass.getProperty(proxy, 'proxy')
        Object initializedResult = metaClass.getProperty(proxy, 'initialized')
        Object metaClassResult = metaClass.getProperty(proxy, 'metaClass')

        then:
        idResult == 11L
        proxyResult == true
        initializedResult == false
        metaClassResult.is(metaClass)
        0 * session.retrieve(_, _)
    }

    void "getProperty for class/domainClass only resolves once the proxy is already initiated"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        delegate.getProperty(proxy, propertyName) >> ProxyInstanceTestTarget

        when:
        Object result = metaClass.getProperty(proxy, propertyName)

        then:
        result == ProxyInstanceTestTarget
        0 * session.retrieve(_, _)

        where:
        propertyName << ['class', 'domainClass']
    }

    void "getProperty resolves the target for the target property"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target

        expect:
        metaClass.getProperty(proxy, 'target').is(target)
    }

    void "getProperty for class/domainClass resolves the target once already initiated"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target
        metaClass.getProxyTarget()
        delegate.getProperty(target, 'class') >> ProxyInstanceTestTarget

        expect:
        metaClass.getProperty(proxy, 'class') == ProxyInstanceTestTarget
    }

    void "getProperty resolves the target for regular properties"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target
        delegate.getProperty(target, 'name') >> 'resolved-name'

        expect:
        metaClass.getProperty(proxy, 'name') == 'resolved-name'
    }

    void "setProperty does not resolve the target when replacing the metaClass"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        MetaClass newMetaClassArg = Mock(MetaClass)

        when:
        metaClass.setProperty(proxy, 'metaClass', newMetaClassArg)

        then:
        1 * delegate.setProperty(proxy, 'metaClass', newMetaClassArg)
        0 * session.retrieve(_, _)
    }

    void "setProperty does not resolve the target when clearing the metaClass"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()

        when:
        metaClass.setProperty(proxy, 'metaClass', null)

        then:
        1 * delegate.setProperty(proxy, 'metaClass', null)
        0 * session.retrieve(_, _)
    }

    void "setProperty resolves the target when setting metaClass to a non-MetaClass value"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()

        when:
        metaClass.setProperty(proxy, 'metaClass', 'not-a-metaclass')

        then:
        1 * session.retrieve(ProxyInstanceTestTarget, 11L) >> target
        1 * delegate.setProperty(target, 'metaClass', 'not-a-metaclass')
    }

    void "setProperty resolves the target for regular properties"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target

        when:
        metaClass.setProperty(proxy, 'name', 'new-name')

        then:
        1 * delegate.setProperty(target, 'name', 'new-name')
    }

    void "getAttribute exposes proxy metadata without resolving the target"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()

        when:
        Object idResult = metaClass.getAttribute(proxy, 'id')
        Object initializedResult = metaClass.getAttribute(proxy, 'initialized')

        then:
        idResult == 11L
        initializedResult == false
        0 * session.retrieve(_, _)
    }

    void "getAttribute resolves the target for the target attribute"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target

        expect:
        metaClass.getAttribute(proxy, 'target').is(target)
    }

    void "getAttribute resolves the target for other attributes"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target
        delegate.getAttribute(target, 'name') >> 'resolved-name'

        expect:
        metaClass.getAttribute(proxy, 'name') == 'resolved-name'
    }

    void "setAttribute always resolves and delegates to the target"() {
        given:
        ProxyInstanceMetaClass metaClass = newMetaClass()
        session.retrieve(ProxyInstanceTestTarget, 11L) >> target

        when:
        metaClass.setAttribute(proxy, 'name', 'new-name')

        then:
        1 * delegate.setAttribute(target, 'name', 'new-name')
    }
}

class ProxyInstanceTestTarget {
    Long id
    String name
}
