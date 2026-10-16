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
package org.grails.datastore.gorm.neo4j.proxy

import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.neo4j.Neo4jMappingContext
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettings
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.proxy.EntityProxy

class Neo4jProxyFactoriesSpec extends Specification {

    Neo4jMappingContext mappingContext = new Neo4jMappingContext(new Neo4jConnectionSourceSettings())
    Session session = Mock(Session) { getMappingContext() >> mappingContext }

    void setup() {
        mappingContext.addPersistentEntity(NpfBook)
    }

    void "neo4j association proxies resolve the identifier from the loaded target"() {
        given:
        Neo4jProxyFactory factory = new Neo4jProxyFactory()
        PersistentEntity entity = mappingContext.getPersistentEntity(NpfBook.name)
        NpfBook target = new NpfBook(id: 42L, title: 'loaded')
        AssociationQueryExecutor executor = Mock(AssociationQueryExecutor) {
            getIndexedEntity() >> entity
            doesReturnKeys() >> false
        }

        when:
        NpfBook proxy = factory.createProxy(session, executor, 7L)

        then:
        factory.isProxy(proxy)
        !factory.isInitialized(proxy)
        factory.getIdentifier(proxy) == 7L
        0 * executor.query(_)

        when:
        Long id = proxy.getId()

        then:
        1 * executor.query(7L) >> [target]
        id == 42L
        factory.isInitialized(proxy)
        proxy.id == 42L
        proxy.title == 'loaded'
        factory.unwrap(proxy).is(target)
    }

    void "hashcode and equals aware proxies compare by identifier"() {
        given:
        HashcodeEqualsAwareProxyFactory factory = new HashcodeEqualsAwareProxyFactory()
        NpfBook target = new NpfBook(id: 5L, title: 'real')
        session.retrieve(NpfBook, 5L) >> target

        when:
        Object proxy = factory.createProxy(session, NpfBook, 5L)
        Object sameId = factory.createProxy(session, NpfBook, 5L)
        Object otherId = factory.createProxy(session, NpfBook, 6L)

        then:
        !factory.isInitialized(proxy)
        proxy instanceof EntityProxy
        proxy instanceof NpfBook
        factory.isProxy(proxy)
        proxy.getId() == 5L
        ((EntityProxy) proxy).getProxyKey() == 5L
        proxy.hashCode() == 5
        proxy.equals(proxy)
        proxy.equals(sameId)
        !proxy.equals(otherId)
        !proxy.equals(null)
        proxy.equals(new NpfBook(id: 5L))
        !proxy.equals(new NpfBook(id: 9L))

        when:
        String title = proxy.getTitle()

        then:
        title == 'real'
        factory.isInitialized(proxy)
        ((EntityProxy) proxy).getTarget().is(target)
        factory.unwrap(proxy).is(target)

        when:
        proxy.setTitle('changed')
        boolean differentType = proxy.equals('5')

        then:
        target.title == 'changed'
        !differentType
    }

    void "hashcode and equals aware proxies fail when the target cannot be loaded"() {
        given:
        HashcodeEqualsAwareProxyFactory factory = new HashcodeEqualsAwareProxyFactory()
        session.retrieve(NpfBook, 99L) >> null
        Object proxy = factory.createProxy(session, NpfBook, 99L)

        when:
        proxy.getTitle()

        then:
        IllegalStateException e = thrown()
        e.message == 'Proxy for [' + NpfBook.name + ':99] could not be initialized'

        when:
        factory.initialize(proxy)

        then:
        noExceptionThrown()
        !factory.isInitialized(proxy)
    }

}

@Entity
class NpfBook {

    Long id
    String title

}
