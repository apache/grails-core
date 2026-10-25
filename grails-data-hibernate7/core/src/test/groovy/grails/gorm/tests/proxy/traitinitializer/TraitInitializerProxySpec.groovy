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
package grails.gorm.tests.proxy.traitinitializer

import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Regression test for the construction of Hibernate proxies for Groovy entities whose trait
 * declares fields with default values.
 *
 * Groovy generates a static {@code $init$} helper for such traits and calls it from the entity
 * constructor. When Hibernate builds the lazy proxy, the proxy constructor runs while the
 * interceptor has not been assigned yet; the generated proxy intercepts {@code getMetaClass()}
 * (Groovy 3+ no longer marks it as synthetic, so Hibernate's synthetic-only filter does not
 * exclude it) and the trait initializer fails with a {@code NullPointerException}.
 *
 * {@code Domain.load()} must return a usable, uninitialized proxy for both entities, and the serialized
 * form of such a proxy must read back as the same kind of proxy: Hibernate's own serialized form rebuilds
 * a stock proxy, which fails the same way.
 */
@Issue('https://github.com/apache/grails-core/issues/16600')
class TraitInitializerProxySpec extends Specification {

    /**
     * Lazy loading outside a transaction is how Hibernate initializes a deserialized proxy: it looks the
     * originating SessionFactory up by the identity the serialized form carries and opens a session for it.
     */
    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            ['dataSource.dbCreate': 'create-drop', 'hibernate.enable_lazy_load_no_trans': true],
            getClass().getPackage())

    void "load() of an entity whose trait declares default values returns a proxy that initializes on use"() {
        given: "a persisted entity whose trait declares fields with default values"
        Long id = null
        InitializedTraitEntity.withTransaction {
            id = new InitializedTraitEntity(name: 'seed').save(flush: true, failOnError: true).id
        }

        when: "the proxy is initialized through dynamic dispatch"
        InitializedTraitEntity proxy = null
        boolean uninitializedBefore = false
        String name = null
        String createdBy = null
        Class<?> unwrapped = null
        InitializedTraitEntity.withTransaction {
            proxy = InitializedTraitEntity.load(id)
            uninitializedBefore = !Hibernate.isInitialized(proxy)
            name = proxy.name
            createdBy = proxy.createdBy
            unwrapped = datastore.mappingContext.proxyHandler.unwrap(proxy).getClass()
        }

        then:
        proxy instanceof HibernateProxy
        proxy instanceof InitializedTrait
        uninitializedBefore
        proxy.id == id
        name == 'seed'
        createdBy == 'N/A'
        Hibernate.isInitialized(proxy)
        unwrapped == InitializedTraitEntity
    }

    void "a lazy many-to-one to an entity whose trait declares default values loads through the proxy"() {
        given: "a persisted owner with an association to the entity"
        Long ownerId = null
        TraitOwnerEntity.withTransaction {
            InitializedTraitEntity target =
                    new InitializedTraitEntity(name: 'seed').save(flush: true, failOnError: true)
            ownerId = new TraitOwnerEntity(target: target).save(flush: true, failOnError: true).id
        }

        when: "the owner is loaded in a new session"
        TraitOwnerEntity owner = null
        boolean uninitializedBefore = false
        String name = null
        String createdBy = null
        TraitOwnerEntity.withTransaction {
            owner = TraitOwnerEntity.get(ownerId)
            uninitializedBefore = !Hibernate.isInitialized(owner.target)
            name = owner.target.name
            createdBy = owner.target.createdBy
        }

        then: "the association was a lazy proxy and reading through it works"
        uninitializedBefore
        owner.target instanceof HibernateProxy
        name == 'seed'
        createdBy == 'N/A'
        Hibernate.isInitialized(owner.target)
    }

    void "an uninitialized proxy of an entity whose trait declares default values survives Java serialization"() {
        given: "a persisted entity whose trait declares fields with default values"
        Long id = null
        InitializedTraitEntity.withTransaction {
            id = new InitializedTraitEntity(name: 'seed').save(flush: true, failOnError: true).id
        }

        when: "an uninitialized proxy is serialized while its session is open, and read back"
        Class<?> proxyClass = null
        InitializedTraitEntity deserialized = null
        InitializedTraitEntity.withTransaction {
            InitializedTraitEntity proxy = InitializedTraitEntity.load(id)
            proxyClass = proxy.getClass()
            deserialized = roundTrip(proxy) as InitializedTraitEntity
        }
        boolean uninitializedAfterRead = !Hibernate.isInitialized(deserialized)
        Long idWithoutInitializing = deserialized.id
        boolean uninitializedAfterId = !Hibernate.isInitialized(deserialized)

        and: "the deserialized proxy is read through, outside any session, so it loads through its SessionFactory"
        String name = deserialized.name
        String createdBy = deserialized.createdBy

        then: "it came back as the same proxy class, answering its identifier without loading and its state by loading"
        deserialized instanceof HibernateProxy
        deserialized instanceof InitializedTrait
        deserialized.getClass() == proxyClass
        uninitializedAfterRead
        idWithoutInitializing == id
        uninitializedAfterId
        name == 'seed'
        createdBy == 'N/A'
        Hibernate.isInitialized(deserialized)
    }

    void "an entity graph holding an uninitialized proxy of such an entity survives Java serialization"() {
        given: "a persisted owner with an association to the entity"
        Long ownerId = null
        Long targetId = null
        TraitOwnerEntity.withTransaction {
            InitializedTraitEntity target =
                    new InitializedTraitEntity(name: 'seed').save(flush: true, failOnError: true)
            targetId = target.id
            ownerId = new TraitOwnerEntity(target: target).save(flush: true, failOnError: true).id
        }

        when: "the owner is loaded in a new session and serialized with the association still lazy"
        TraitOwnerEntity deserializedOwner = null
        boolean targetUninitializedBefore = false
        TraitOwnerEntity.withTransaction {
            TraitOwnerEntity owner = TraitOwnerEntity.get(ownerId)
            targetUninitializedBefore = !Hibernate.isInitialized(owner.target)
            deserializedOwner = roundTrip(owner) as TraitOwnerEntity
        }
        boolean targetUninitializedAfter = !Hibernate.isInitialized(deserializedOwner.target)
        Long idWithoutInitializing = deserializedOwner.target.id

        then: "the association came back as an uninitialized proxy whose identifier reads without a session"
        targetUninitializedBefore
        deserializedOwner.target instanceof HibernateProxy
        deserializedOwner.target instanceof InitializedTrait
        targetUninitializedAfter
        idWithoutInitializing == targetId
        !Hibernate.isInitialized(deserializedOwner.target)
    }

    void "control: load() of an entity whose trait declares no initializer creates the proxy"() {
        given: "a persisted entity whose trait declares a plain property"
        Long id = null
        PlainTraitEntity.withTransaction {
            id = new PlainTraitEntity(name: 'seed').save(flush: true, failOnError: true).id
        }

        when: "a lazy proxy is requested in a new session"
        PlainTraitEntity proxy = null
        PlainTraitEntity.withTransaction { proxy = PlainTraitEntity.load(id) }

        then: "the proxy is built"
        noExceptionThrown()
        proxy != null
        !Hibernate.isInitialized(proxy)
    }

    @CompileStatic
    private static Object roundTrip(Object object) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { ObjectOutputStream out -> out.writeObject(object) }
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable { ObjectInputStream input ->
            input.readObject()
        }
    }
}

trait InitializedTrait {
    String createdBy = 'N/A'
    String lastUpdatedBy = 'N/A'
}

@Entity
class InitializedTraitEntity implements InitializedTrait, Serializable {
    String name
}

@Entity
class TraitOwnerEntity implements Serializable {
    InitializedTraitEntity target
}

trait PlainTrait {
    String note
}

@Entity
class PlainTraitEntity implements PlainTrait {
    String name
}
