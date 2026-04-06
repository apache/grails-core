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

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec

class HibernateToManyPropertySpec extends HibernateGormDatastoreSpec {

    // Removed setupSpec to prevent loading all entities at once

    void "resolveJoinTableForeignKeyColumnName derives name from associated entity when no explicit config"() {
        given: "Register only entities for this specific test"
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        and: "Trigger Hibernate First Pass"
        hibernateFirstPass()

        when:
        String columnName = property.resolveJoinTableForeignKeyColumnName(namingStrategy)

        then:
        columnName == "htmpbook_id"
    }

    void "resolveJoinTableForeignKeyColumnName uses explicit join table column name when configured"() {
        given: "Register only entities for this specific test"
        def property = createTestHibernateToManyProperty(HTMPAuthorCustom, "books")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        and: "Trigger Hibernate First Pass"
        hibernateFirstPass()

        when:
        String columnName = property.resolveJoinTableForeignKeyColumnName(namingStrategy)

        then:
        columnName == "custom_book_fk"
    }

    void "isAssociationColumnNullable returns false for ManyToMany"() {
        given: "Register only entities for this specific test"
        createPersistentEntity(HTMPCourse) // Course is needed because Student refers to it
        def studentProp = createTestHibernateToManyProperty(HTMPStudent, "courses")

        when:
        hibernateFirstPass()

        then:
        !studentProp.isAssociationColumnNullable()
    }

    void "test index column configuration"() {
        given: "Register the HTMPOrder entity using the helper"
        def property = createTestHibernateToManyProperty(HTMPOrder, "items")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        and: "Trigger Hibernate First Pass"
        hibernateFirstPass()

        expect: "The index column name and type are resolved from the column list"
        verifyAll(property) {
            getIndexColumnName(namingStrategy) == "item_idx"
            getIndexColumnType("integer") == "integer"
        }
    }

    void "test index column configuration with map"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPOrderMap, "items")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        and: "Trigger Hibernate First Pass"
        hibernateFirstPass()

        expect:
        verifyAll(property) {
            getIndexColumnName(namingStrategy) == "map_idx"
            getIndexColumnType("integer") == "string"
        }
    }

    void "test index column configuration with closure"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPOrderClosure, "items")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        and: "Trigger Hibernate First Pass"
        hibernateFirstPass()

        expect:
        verifyAll(property) {
            getIndexColumnName(namingStrategy) == "closure_idx"
            getIndexColumnType("integer") == "long"
        }
    }

    void "getComponentType returns element type for basic collection"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPOrder, "items")

        and:
        hibernateFirstPass()

        expect:
        property.getComponentType() == String
    }

    void "getComponentType returns associated entity class for one-to-many"() {
        given:
        createPersistentEntity(HTMPBook)
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        and:
        hibernateFirstPass()

        expect:
        property.getComponentType() == HTMPBook
    }

    void "getComponentType returns associated entity class for many-to-many"() {
        given:
        createPersistentEntity(HTMPCourse)
        def property = createTestHibernateToManyProperty(HTMPStudent, "courses")

        and:
        hibernateFirstPass()

        expect:
        property.getComponentType() == HTMPCourse
    }

    void "isEnum returns true for enum element collection"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPEntityWithEnum, "statuses")

        and:
        hibernateFirstPass()

        expect:
        property.isEnum()
    }

    void "isEnum returns false for non-enum basic collection"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPOrder, "items")

        and:
        hibernateFirstPass()

        expect:
        !property.isEnum()
    }

    void "getElementTypeName returns java.lang.String for String basic collection"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPOrder, "items")

        and:
        hibernateFirstPass()

        expect:
        property.getElementTypeName() == "java.lang.String"
    }

    void "getElementTypeName defaults to string for embedded collection with no explicit type"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPOrderMap, "items")

        and:
        hibernateFirstPass()

        expect:
        property.getElementTypeName() == "java.lang.String"
    }

    /**
     * Helper to register entity and return the property
     */
    protected HibernateToManyProperty createTestHibernateToManyProperty(Class<?> domainClass, String propertyName) {
        def entity = createPersistentEntity(domainClass)
        return (HibernateToManyProperty) entity.getPropertyByName(propertyName)
    }
}

// --- Supporting Entities ---

@Entity
class HTMPBook {
    Long id
    String title
}

@Entity
class HTMPAuthor {
    Long id
    String name
    static hasMany = [books: HTMPBook]
}

@Entity
class HTMPAuthorCustom {
    Long id
    String name
    static hasMany = [books: HTMPBook]
    static mapping = {
        books joinTable: [column: 'custom_book_fk']
    }
}

@Entity
class HTMPStudent {
    Long id
    String name
    static hasMany = [courses: HTMPCourse]
}

@Entity
class HTMPCourse {
    Long id
    String title
    static hasMany = [students: HTMPStudent]
}

import grails.persistence.Entity

@Entity // Only if outside grails-app/domain
class HTMPOrder {
    Long id

    List<String> items // Remove the = []

    static hasMany = [items: String]

    static mapping = {
        items joinTable: [
                name: "htmp_order_items",
                key: "order_id",
                column: "item_value"
        ], index: "item_idx" // Defines the column for the List index
    }
}

@Entity
class HTMPOrderMap {
    Long id
    List<String> items
    static hasMany = [items: String]
    static mapping = {
        items index: [column: 'map_idx', type: 'string']
    }
}

@Entity
class HTMPOrderClosure {
    Long id
    List<String> items
    static hasMany = [items: String]
    static mapping = {
        items index: {
            column name: 'closure_idx'
            type 'long'
        }
    }
}

enum HTMPStatus { ACTIVE, INACTIVE }

@Entity
class HTMPEntityWithEnum {
    Long id
    static hasMany = [statuses: HTMPStatus]
}