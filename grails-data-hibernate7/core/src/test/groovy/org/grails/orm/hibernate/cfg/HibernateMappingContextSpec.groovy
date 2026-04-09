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
package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.ValueGenerator
import org.grails.datastore.mapping.model.config.JpaMappingConfigurationStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsJpaMappingConfigurationStrategy
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings
import org.springframework.validation.Errors
import org.grails.datastore.mapping.core.connections.ConnectionSource

class HibernateMappingContextSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([MappingContextBook, MappingContextAuthor, MappingContextAddress])
    }

    // --- unit-style tests (no datastore required) ---

    void "default constructor creates a usable context"() {
        when:
        def ctx = new HibernateMappingContext()

        then:
        ctx.mappingFactory != null
        ctx.getMappingSyntaxStrategy() instanceof JpaMappingConfigurationStrategy
    }

    void "custom type marshaller is registered on the mapping factory"() {
        given:
        HibernateConnectionSourceSettings settings = new HibernateConnectionSourceSettings()
        settings.custom.types = [new MappingContextTypeMarshaller(MappingContextUUID)]

        when:
        def ctx = new HibernateMappingContext(settings)

        then:
        ctx.mappingFactory.isCustomType(MappingContextUUID)
    }

    void "entity with custom id generator resolves to ValueGenerator.CUSTOM"() {
        when:
        def ctx = new HibernateMappingContext()
        PersistentEntity entity = ctx.addPersistentEntity(CustomIdGeneratorEntity)

        then:
        entity.mapping.identifier.generator == ValueGenerator.CUSTOM
    }

    void "Errors type is not treated as a custom type by the syntax strategy"() {
        when:
        def ctx = new HibernateMappingContext()
        def strategy = ctx.getMappingSyntaxStrategy() as GrailsJpaMappingConfigurationStrategy

        then:
        !strategy.supportsCustomType(Errors)
    }

    void "arbitrary non-Errors type is supported as a custom type by the syntax strategy"() {
        when:
        def ctx = new HibernateMappingContext()
        def strategy = ctx.getMappingSyntaxStrategy() as GrailsJpaMappingConfigurationStrategy

        then:
        strategy.supportsCustomType(MappingContextUUID)
    }

    void "getPersistentEntity strips Hibernate proxy suffix"() {
        when:
        def ctx = new HibernateMappingContext()
        ctx.addPersistentEntity(CustomIdGeneratorEntity)

        then:
        ctx.getPersistentEntity("org.grails.orm.hibernate.cfg.CustomIdGeneratorEntity\$HibernateProxy\$XYZ") != null
    }

    void "non-GormEntity class is not added as a persistent entity"() {
        when:
        def ctx = new HibernateMappingContext()
        def entity = ctx.addPersistentEntity(MappingContextUUID)

        then:
        entity == null
    }

    // --- integration-style tests (use live datastore) ---

    void "mappingContext is a HibernateMappingContext"() {
        expect:
        mappingContext instanceof HibernateMappingContext
    }

    void "registered domain classes appear as persistent entities"() {
        expect:
        mappingContext.getPersistentEntity(MappingContextBook.name) != null
        mappingContext.getPersistentEntity(MappingContextAuthor.name) != null
    }

    void "getHibernatePersistentEntities returns GrailsHibernatePersistentEntity instances"() {
        when:
        def entities = mappingContext.getHibernatePersistentEntities(ConnectionSource.DEFAULT)

        then:
        entities.every { it instanceof GrailsHibernatePersistentEntity }
        entities.every { it.dataSourceName == ConnectionSource.DEFAULT }
    }

    void "getHibernatePersistentEntities sets the dataSourceName on each entity"() {
        when:
        def entities = mappingContext.getHibernatePersistentEntities("myDs")

        then:
        entities.every { it.dataSourceName == "myDs" }
    }

    void "embedded entity is created correctly"() {
        when:
        def embedded = mappingContext.createEmbeddedEntity(MappingContextAddress)

        then:
        embedded != null
        embedded.javaClass == MappingContextAddress
    }

    void "MappingContextBook has expected persistent properties"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingContextBook.name)

        then:
        entity.persistentProperties.find { it.name == "title" } != null
        entity.persistentProperties.find { it.name == "author" } != null
    }

    void "MappingContextAuthor oneToMany relationship is mapped"() {
        when:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingContextAuthor.name)

        then:
        entity.persistentProperties.find { it.name == "books" } != null
    }

    void "getMappingFactory returns a HibernateMappingFactory"() {
        expect:
        mappingContext.mappingFactory != null
        mappingContext.mappingFactory instanceof org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateMappingFactory
    }
}

// --- domain classes used in integration tests ---

@Entity
class MappingContextBook implements HibernateEntity<MappingContextBook> {
    String title
    MappingContextAuthor author
    static belongsTo = [author: MappingContextAuthor]
}

@Entity
class MappingContextAuthor implements HibernateEntity<MappingContextAuthor> {
    String name
    static hasMany = [books: MappingContextBook]
}

class MappingContextAddress {
    String street
    String city
}

// --- helpers for unit tests ---

@Entity
class CustomIdGeneratorEntity {
    String name
    static mapping = {
        id(generator: "org.grails.orm.hibernate.cfg.MappingContextUUID", type: "uuid-binary")
    }
}

class MappingContextUUID {}

class MappingContextTypeMarshaller extends AbstractMappingAwareCustomTypeMarshaller {
    MappingContextTypeMarshaller(Class targetType) { super(targetType) }

    @Override
    protected Object writeInternal(PersistentProperty property, String key, Object value, Object nativeTarget) { value }

    @Override
    protected Object readInternal(PersistentProperty property, String key, Object nativeSource) { nativeSource }
}