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
package org.grails.datastore.gorm

import spock.lang.AutoCleanup
import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.simple.SimpleMapDatastore

class GormInstanceApiIdentSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore

    void setup() {
        GormRegistry.instance.reset()
        datastore = new SimpleMapDatastore(
                IdentGenerated, IdentNamed, IdentComposite, IdentCompositeNotSerializable,
                IdentCompositeSub)
    }

    void cleanup() {
        GormRegistry.instance.reset()
    }

    private GormInstanceApi compositeApi(Class type) {
        PersistentEntity real = datastore.mappingContext.getPersistentEntity(type.name)
        PersistentProperty[] keys = [real.getPropertyByName('first'), real.getPropertyByName('second')] as PersistentProperty[]
        def entity = Stub(PersistentEntity) {
            getIdentity() >> null
            getCompositeIdentity() >> keys
            newInstance() >> { real.newInstance() }
            getReflector() >> real.reflector
        }
        def context = Stub(MappingContext) {
            getPersistentEntity(type.name) >> entity
        }
        new GormInstanceApi(type, context, Stub(DatastoreResolver))
    }

    void "ident returns null before persistence and the generated id afterwards"() {
        given:
        def thing = new IdentGenerated(name: 'a')

        expect:
        thing.ident() == null

        when:
        thing.save(flush: true, failOnError: true)

        then:
        thing.ident() == thing.id
        thing.ident() != null
    }

    void "ident returns the mapped identity property when the identifier is renamed"() {
        given:
        def named = new IdentNamed(code: 'NAMED-1', payload: 'p')

        expect:
        named.ident() == 'NAMED-1'

        when:
        named.save(flush: true, failOnError: true)

        then:
        named.ident() == 'NAMED-1'
        IdentNamed.get(named.ident()).payload == 'p'
    }

    void "ident on an uninitialized #type.simpleName proxy returns its key without loading the entity"() {
        given:
        ProxyHandler proxyHandler = datastore.mappingContext.proxyHandler
        def proxy = type.load(key)

        expect:
        proxyHandler.isProxy(proxy)
        !proxyHandler.isInitialized(proxy)

        when:
        Serializable identifier = proxy.ident()

        then:
        identifier == key
        !proxyHandler.isInitialized(proxy)

        where:
        type           | key
        IdentGenerated | 404L
        IdentNamed     | 'MISSING'
    }

    void "ident on an initialized renamed-key proxy returns the mapped identity property"() {
        given:
        new IdentNamed(code: 'NAMED-3', payload: 'p').save(flush: true, failOnError: true)
        IdentNamed.withSession { it.clear() }
        ProxyHandler proxyHandler = datastore.mappingContext.proxyHandler
        def proxy = IdentNamed.load('NAMED-3')

        when:
        proxyHandler.initialize(proxy)

        then:
        proxyHandler.isProxy(proxy)
        proxyHandler.isInitialized(proxy)
        proxy.ident() == 'NAMED-3'
    }

    void "ident returns null for a renamed identifier that has not been assigned"() {
        expect:
        new IdentNamed(payload: 'p').ident() == null
    }

    void "ident copies only the composite key properties into a separate instance"() {
        given:
        def record = new IdentComposite(first: 'a', second: 2L, payload: 'content')

        when:
        Serializable identifier = compositeApi(IdentComposite).ident(record)

        then:
        identifier instanceof IdentComposite
        !identifier.is(record)
        identifier.first == 'a'
        identifier.second == 2L
        identifier.payload == null
        record.payload == 'content'
    }

    void "a composite identifier is a snapshot independent of later changes to the instance"() {
        given:
        def record = new IdentComposite(first: 'a', second: 2L)
        Serializable identifier = compositeApi(IdentComposite).ident(record)

        when:
        record.first = 'changed'

        then:
        identifier.first == 'a'
    }

    void "ident returns null for a composite key whose class is not Serializable"() {
        expect:
        compositeApi(IdentCompositeNotSerializable).ident(new IdentCompositeNotSerializable(first: 'a', second: 2L)) == null
    }

    void "ident returns null for an entity that has neither an identity nor a composite identity"() {
        given:
        def entity = Stub(PersistentEntity) {
            getIdentity() >> null
            getCompositeIdentity() >> null
        }
        def context = Stub(MappingContext) {
            getPersistentEntity(IdentUnmapped.name) >> entity
        }
        def api = new GormInstanceApi(IdentUnmapped, context, Stub(DatastoreResolver))

        expect:
        api.ident(new IdentUnmapped(id: 42L)) == null
    }

    void "ident of a subclass instance with a composite key returns the key properties"() {
        when:
        Serializable identifier = compositeApi(IdentComposite)
                .ident(new IdentCompositeSub(first: 'a', second: 3L, payload: 'x', extra: 'e'))

        then:
        identifier != null
        identifier.first == 'a'
        identifier.second == 3L
        identifier.payload == null
    }

    void "ident falls back to the id property for a class unknown to the mapping context"() {
        given:
        def api = new GormInstanceApi(IdentUnmapped, datastore)

        expect:
        api.ident(new IdentUnmapped(id: 42L)) == 42L
    }

    void "the registered instance api resolves ident the same way as the domain method"() {
        given:
        def named = new IdentNamed(code: 'NAMED-2')
        def api = new GormInstanceApi(IdentNamed, datastore)

        expect:
        api.ident(named) == named.ident()
    }
}

@Entity
class IdentGenerated {
    Long id
    String name
}

@Entity
class IdentNamed {
    String code
    String payload

    static mapping = {
        id name: 'code', generator: 'assigned'
    }
}

@Entity
class IdentComposite implements Serializable {
    String first
    Long second
    String payload

    static mapping = {
        id composite: ['first', 'second']
    }
}

@Entity
class IdentCompositeNotSerializable {
    String first
    Long second

    static mapping = {
        id composite: ['first', 'second']
    }
}

@Entity
class IdentCompositeSub extends IdentComposite {
    String extra
}

class IdentUnmapped {
    Long id
}
