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
            GeneratorDefaultEntity, GeneratorUuid2Entity, CompositeKeyEntity,
            HPPSManyA, HPPSManyB
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

    def "test isLazy for collection with fetch join"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(JoinFetchEntity.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("items")

        expect:
        !property.isLazy()
    }

    def "test isLazy for explicit non-lazy"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(ExplicitNonLazy.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("name")

        expect:
        !property.isLazy()
    }

    def "test getHibernateOwner"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyBook.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("title")

        expect:
        property.getHibernateOwner() == entity
    }

    def "test isEnum"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(EnumEntity.name)
        def enumProp = (HibernatePersistentProperty) entity.getPropertyByName("type")
        def stringProp = (HibernatePersistentProperty) entity.getPropertyByName("name")

        expect:
        enumProp.isEnum()
        !stringProp.isEnum()
    }

    def "test getGeneratorName"() {
        given:
        def defaultEntity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(GeneratorDefaultEntity.name)
        def uuidEntity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(GeneratorUuid2Entity.name)

        expect:
        defaultEntity.getIdentityProperty().getGeneratorName() == "identity"
        uuidEntity.getIdentityProperty().getGeneratorName() == "uuid2"
    }

    def "test getPersistentClass"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyBook.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("title")

        expect:
        property.getPersistentClass() != null
        property.getPersistentClass().getEntityName() == LazyBook.name
    }

    def "test validateProperty returns self by default"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyBook.name)
        def property = (HibernatePersistentProperty) entity.getPropertyByName("title")

        when:
        def result = property.validateProperty()

        then:
        result == property
    }

    def "test isManyToMany and isOneToMany"() {
        given:
        def authorEntity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(LazyAuthor.name)
        def entityA = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HPPSManyA.name)

        def oneToMany = (HibernateToManyProperty) authorEntity.getPropertyByName("books")
        def manyToMany = (HibernateToManyProperty) entityA.getPropertyByName("others")

        expect:
        oneToMany.isOneToMany()
        !oneToMany.isManyToMany()

        manyToMany.isManyToMany()
        !manyToMany.isOneToMany()
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
        parts*.name.sort() == ["code", "name"]
    }
}

@Entity
class LazyBook {
    Long id
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
    String name
    static mapping = {
        name lazy: false
    }

    boolean isLazyFalse() { false }
}

@Entity
class JoinFetchEntity {
    Long id
    static hasMany = [items: String]
    static mapping = {
        items fetch: "join"
    }
}

@Entity
class EnumEntity {
    Long id
    String name
    GrailsSequenceGeneratorEnum type
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

@Entity
class CompositeKeyEntity implements Serializable {
    String name
    Integer code
    static mapping = {
        id composite: ['name', 'code']
    }
}

@Entity
class HPPSManyA {
    Long id
    static hasMany = [others: HPPSManyB]
    static mapping = {
        others joinTable: [name: 'many_a_others']
    }
}

@Entity
class HPPSManyB {
    Long id
    static hasMany = [owners: HPPSManyA]
    static belongsTo = [owners: HPPSManyA]
}
