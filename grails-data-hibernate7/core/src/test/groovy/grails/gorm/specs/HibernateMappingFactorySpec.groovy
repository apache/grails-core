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
package grails.gorm.specs

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.ValueGenerator
import org.grails.datastore.mapping.model.types.*
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.*
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings

/**
 * Spec for {@link HibernateMappingFactory}, verifying that it creates
 * the correct Hibernate-specific property and identity mapping instances.
 */
class HibernateMappingFactorySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([MappingFactoryBook, MappingFactoryAuthor, MappingFactoryTag,
                                     MappingFactoryArticle])
    }

    // --- unit-style tests (standalone factory) ---

    void "factory can be instantiated standalone"() {
        when:
        def factory = new HibernateMappingFactory()

        then:
        factory != null
        factory.getPropertyMappedFormType() == org.grails.orm.hibernate.cfg.PropertyConfig
        factory.getEntityMappedFormType() == org.grails.orm.hibernate.cfg.Mapping
    }

    void "allowArbitraryCustomTypes returns true"() {
        expect:
        new HibernateMappingFactory().allowArbitraryCustomTypes()
    }

    void "custom type marshaller is registered and detectable"() {
        given:
        HibernateConnectionSourceSettings settings = new HibernateConnectionSourceSettings()
        settings.custom.types = [new FactoryTypeMarshaller(FactoryCustomType)]
        def ctx = new HibernateMappingContext(settings)

        expect:
        ctx.mappingFactory.isCustomType(FactoryCustomType)
    }

    void "custom type marshaller is NOT registered for unrelated type"() {
        given:
        HibernateConnectionSourceSettings settings = new HibernateConnectionSourceSettings()
        settings.custom.types = [new FactoryTypeMarshaller(FactoryCustomType)]
        def ctx = new HibernateMappingContext(settings)

        expect:
        !ctx.mappingFactory.isCustomType(String)
    }

    // --- integration-style tests using live datastore ---

    void "mappingFactory is a HibernateMappingFactory"() {
        expect:
        mappingContext.mappingFactory instanceof HibernateMappingFactory
    }

    void "createSimple produces HibernateSimpleProperty for a String field"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingFactoryBook.name)
        def titleProp = entity.persistentProperties.find { it.name == 'title' }

        then:
        titleProp instanceof HibernateSimpleProperty
    }

    void "createManyToOne produces HibernateManyToOneProperty for a many-to-one association"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingFactoryBook.name)
        def authorProp = entity.persistentProperties.find { it.name == 'author' }

        then:
        authorProp instanceof HibernateManyToOneProperty
    }

    void "createOneToMany produces HibernateOneToManyProperty for a one-to-many association"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingFactoryAuthor.name)
        def booksProp = entity.persistentProperties.find { it.name == 'books' }

        then:
        booksProp instanceof HibernateOneToManyProperty
    }

    void "createManyToMany produces HibernateManyToManyProperty"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingFactoryBook.name)
        def tagsProp = entity.persistentProperties.find { it.name == 'tags' }

        then:
        tagsProp instanceof HibernateManyToManyProperty
    }

    void "createIdentity produces HibernateIdentityProperty"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingFactoryBook.name)

        then:
        entity.identity instanceof HibernateIdentityProperty
    }

    void "createIdentityMapping resolves NATIVE generator by default"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingFactoryBook.name)

        then:
        entity.mapping.identifier.generator == ValueGenerator.NATIVE
    }

    void "createIdentityMapping resolves CUSTOM generator for a custom class name"() {
        when:
        def ctx = new HibernateMappingContext()
        PersistentEntity entity = ctx.addPersistentEntity(MappingFactoryCustomIdEntity)

        then:
        entity.mapping.identifier.generator == ValueGenerator.CUSTOM
    }

    void "createIdentityMapping returns HibernateIdentityMapping instance"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingFactoryBook.name)
        def idMapping = entity.mapping.identifier

        then:
        idMapping instanceof HibernateIdentityMapping
        idMapping.identifierName != null
        idMapping.identifierName.length > 0
    }

    void "createEmbedded produces HibernateEmbeddedProperty"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingFactoryArticle.name)
        def addrProp = entity.persistentProperties.find { it.name == 'metadata' }

        then:
        addrProp instanceof HibernateEmbeddedProperty
    }

    @Rollback
    void "factory-created entities can be persisted and retrieved"() {
        when:
        def author = new MappingFactoryAuthor(name: 'Test Author').save(flush: true)
        def book = new MappingFactoryBook(title: 'Test Book', author: author).save(flush: true)

        then:
        MappingFactoryBook.count() >= 1
        MappingFactoryBook.findByTitle('Test Book')?.author?.name == 'Test Author'
    }
}

// --- domain classes ---

@Entity
class MappingFactoryAuthor implements HibernateEntity<MappingFactoryAuthor> {
    String name
    static hasMany = [books: MappingFactoryBook]
}

@Entity
class MappingFactoryBook implements HibernateEntity<MappingFactoryBook> {
    String title
    MappingFactoryAuthor author
    static belongsTo = [author: MappingFactoryAuthor]
    static hasMany = [tags: MappingFactoryTag]
}

@Entity
class MappingFactoryTag implements HibernateEntity<MappingFactoryTag> {
    String name
    static hasMany = [books: MappingFactoryBook]
    static belongsTo = MappingFactoryBook
}

@Entity
class MappingFactoryArticle implements HibernateEntity<MappingFactoryArticle> {
    String title
    MappingFactoryMetadata metadata
    static embedded = ['metadata']
}

class MappingFactoryMetadata {
    String description
}

@Entity
class MappingFactoryCustomIdEntity implements HibernateEntity<MappingFactoryCustomIdEntity> {
    String name
    static mapping = {
        id generator: 'grails.gorm.specs.FactoryCustomType', type: 'uuid-binary'
    }
}

// --- helpers ---

class FactoryCustomType {}

class FactoryTypeMarshaller extends AbstractMappingAwareCustomTypeMarshaller {
    FactoryTypeMarshaller(Class targetType) { super(targetType) }

    @Override
    protected Object writeInternal(PersistentProperty property, String key, Object value, Object nativeTarget) { value }

    @Override
    protected Object readInternal(PersistentProperty property, String key, Object nativeSource) { nativeSource }
}
