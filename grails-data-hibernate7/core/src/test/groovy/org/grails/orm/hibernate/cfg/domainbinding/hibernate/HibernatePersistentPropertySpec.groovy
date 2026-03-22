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
import spock.lang.Shared

class HibernatePersistentPropertySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([
            LazyBook, LazyAuthor, ExplicitNonLazy, JoinFetchEntity, EnumEntity
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
