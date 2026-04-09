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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum
import spock.lang.Shared

class HibernatePersistentPropertySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([
            LazyBook, LazyAuthor, ExplicitNonLazy, JoinFetchEntity, EnumEntity,
            GeneratorDefaultEntity, GeneratorUuid2Entity, CompositeKeyEntity
        ])
    }

    def "test isLazy for standard property"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyBook.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("title")

        expect:
        !property.isLazy()
    }

    def "test isLazy for association"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyBook.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("author")

        expect:
        property.isLazy()
    }

    def "test isLazy for collection"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyAuthor.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("books")

        expect:
        property.isLazyAble()
        property.isLazy()
    }

    def "test isLazy for association"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyBook.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("author")

        expect:
        property.isLazyAble()
        property.isLazy()
    }

    def "test isLazy for explicit lazy false"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(ExplicitNonLazy.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("author")

        expect:
        !property.isLazy()
    }

    def "test isLazy for fetch join"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(JoinFetchEntity.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("author")

        expect:
        !property.isLazy()
    }

    def "test getTypeName"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyBook.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("title")

        expect:
        property.getTypeName() == String.name
    }

    def "test isEnumType"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(EnumEntity.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("status")

        expect:
        property.isEnumType()
    }

    def "test getHibernateOwner"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyBook.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("title")

        expect:
        property.getHibernateOwner() == entity
    }

    def "test isJoinKeyMapped"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(JoinFetchEntity.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("author")

        expect:
        !property.isJoinKeyMapped() // Default many-to-one doesn't have join table usually
    }

    def "test getPersistentClass"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyBook.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("title")

        expect:
        property.getPersistentClass() != null
        property.getPersistentClass().getEntityName() == LazyBook.name
    }

    def "getIdentityProperty returns HibernateCompositeIdentityProperty with all parts for composite key entity"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(CompositeKeyEntity.name)

        when:
        def identityProperty = entity.getIdentityProperty()
        def parts = identityProperty instanceof HibernateCompositeIdentityProperty ?
                ((HibernateCompositeIdentityProperty) identityProperty).getParts() : null

        then:
        identityProperty instanceof HibernateCompositeIdentityProperty
        parts != null
        parts.length == 2
        parts.every { it instanceof HibernatePersistentProperty }
        parts*.name.toSet() == ['firstName', 'lastName'].toSet()
    }


    def "getGeneratorName returns null for regular property with no generator configured"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(GeneratorDefaultEntity.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("name")

        expect:
        property.getGeneratorName() == null
    }

    def "getGeneratorName on identity with no explicit generator delegates to entity"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(GeneratorDefaultEntity.name)
        def identity = (HibernateSimpleIdentityProperty) entity.getIdentityProperty()

        expect:
        identity.getGeneratorName() == entity.getIdentityGeneratorName()
        identity.getGeneratorName() != null
    }

    def "getGeneratorName on identity with explicit generator returns that generator"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(GeneratorUuid2Entity.name)
        def identity = (HibernateSimpleIdentityProperty) entity.getIdentityProperty()

        expect:
        identity.getGeneratorName() == GrailsSequenceGeneratorEnum.UUID2.toString()
    }
}

@Entity
class LazyBook {
    Long id
    Long version
    String title
    LazyAuthor author
}

@Entity
class LazyAuthor {
    Long id
    String name
    static hasMany = [books: LazyBook]
}

@Entity
class ExplicitNonLazy {
    Long id
    LazyAuthor author
    static mapping = {
        author lazy: false
    }
}

@Entity
class JoinFetchEntity {
    Long id
    LazyAuthor author
    static mapping = {
        author fetch: 'join'
    }
}

enum Status { ACTIVE, INACTIVE }

@Entity
class EnumEntity {
    Long id
    Status status
}

@Entity
class CompositeKeyEntity implements Serializable {
    String firstName
    String lastName
    Long version
    static mapping = {
        id composite: ['firstName', 'lastName']
    }
}

@Entity
class GeneratorDefaultEntity {
    Long id
    String name
}

@Entity
class GeneratorUuid2Entity {
    String id
    String name
    static mapping = {
        id generator: 'uuid2'
    }
}
