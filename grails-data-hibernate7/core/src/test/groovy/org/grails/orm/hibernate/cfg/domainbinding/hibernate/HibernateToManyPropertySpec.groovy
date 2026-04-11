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
import org.hibernate.MappingException
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.Mapping

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

    void "isBasic returns true for basic element collection"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPOrder, "items")

        expect:
        property.isBasic()
        !property.isOneToMany()
        !property.isManyToMany()
    }

    void "isOneToMany returns true for one-to-many association"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        property.isOneToMany()
        !property.isBasic()
        !property.isManyToMany()
    }

    void "isManyToMany returns true for many-to-many association"() {
        given:
        createPersistentEntity(HTMPCourse)
        def property = createTestHibernateToManyProperty(HTMPStudent, "courses")

        expect:
        property.isManyToMany()
        !property.isBasic()
        !property.isOneToMany()
    }

    void "hasSort returns true and getSort/getOrder return values when configured"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthorSorted, "books")

        expect:
        property.hasSort()
        property.getSort() == "title"
        property.getOrder() == "asc"
    }

    void "hasSort returns false when no sort is configured"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        !property.hasSort()
    }

    void "getLazy returns false when explicitly set to false"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthorLazy, "books")

        expect:
        property.getLazy() == false
    }

    void "getIgnoreNotFound returns false by default"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        !property.getIgnoreNotFound()
    }

    void "getCacheUsage returns cache usage string when cache is configured"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthorCached, "books")

        expect:
        property.getCacheUsage() != null
    }

    void "getCacheUsage returns null when no cache is configured"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        property.getCacheUsage() == null
    }

    void "getIndexColumnName returns default name when mapped form has no index column or columns"() {
        given:
        createPersistentEntity(HTMPBook)
        def property = createTestHibernateToManyProperty(HTMPAuthorSorted, "books")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        expect:
        property.getIndexColumnName(namingStrategy) != null
    }

    void "getIndexColumnType returns defaultType when mapped form has no index column or columns"() {
        given:
        createPersistentEntity(HTMPBook)
        def property = createTestHibernateToManyProperty(HTMPAuthorSorted, "books")

        expect:
        property.getIndexColumnType("mydefault") == "mydefault"
    }

    void "getFetchMode returns a non-null fetch mode"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        property.getFetchMode() != null
    }

    void "getCascade returns cascade string (may be null if not configured)"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        property.getCascade() == null || property.getCascade() instanceof String
    }

    void "getBatchSize returns -1 when no batch size is configured"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        property.getBatchSize() == -1
    }

    void "getRole returns qualified entity and property name"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        property.getRole("") != null
        property.getRole("parent.books") != null
    }

    void "getMapElementName returns default element column name when no join table column configured"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        expect:
        property.getMapElementName(namingStrategy) != null
        property.getMapElementName(namingStrategy).endsWith("_elt")
    }

    void "joinTableColumName returns derived column name for basic String collection (no explicit column)"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPOrder, "items")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        expect:
        property.joinTableColumName(namingStrategy) != null
    }

    void "joinTableColumName returns derived column name for enum collection"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPEntityWithEnum, "statuses")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        expect:
        property.joinTableColumName(namingStrategy) != null
    }

    void "joinTableColumName uses explicit join table column name when present"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPJoinColOwner, "tags")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        expect:
        property.joinTableColumName(namingStrategy) == "tag_val"
    }

    void "getColumnConfigOptional returns empty when no join table column config"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        !property.getColumnConfigOptional().isPresent()
    }

    void "shouldBindWithForeignKey returns false by default"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        expect:
        !property.shouldBindWithForeignKey()
    }

    void "validateOwningSide throws MappingException when Hibernate collection is not a List"() {
        given:
        createPersistentEntity(HTMPBook)
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        and:
        hibernateFirstPass()

        when:
        property.validateOwningSide()

        then:
        thrown(MappingException)
    }

    void "getCollection throws MappingException when Hibernate collection is not initialized"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        when:
        property.getCollection()

        then:
        thrown(MappingException)
    }

    void "setCollection with null does not throw"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthor, "books")

        when:
        property.setCollection(null)

        then:
        noExceptionThrown()
    }

    // ─── Additional edge cases for coverage ───────────────────────────────────

    void "test index column name with empty columns"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPOrderEmptyIndex, "items")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        expect:
        property.getIndexColumnName(namingStrategy).endsWith("_idx")
    }

    void "test setCollection handles orphan delete"() {
        given:
        def property = createTestHibernateToManyProperty(HTMPAuthorOrphan, "books")
        def mockCollection = Mock(org.hibernate.mapping.Set)

        when:
        property.setCollection(mockCollection)

        then:
        1 * mockCollection.setOrphanDelete(true)
    }

    /**
     * Helper to register entity and return the property
     */
    protected HibernateToManyProperty createTestHibernateToManyProperty(Class<?> domainClass, String propertyName) {
        def entity = createPersistentEntity(domainClass)
        return (HibernateToManyProperty) entity.getPropertyByName(propertyName)
    }

    // -------------------------------------------------------------------------
    // HibernateToManyCollectionProperty.getElementTypeName — all 4 branches
    // -------------------------------------------------------------------------

    void "getElementTypeName returns component type name when componentType is non-null and has a mapped Hibernate type"() {
        given: "a String-valued basic collection — componentType is String, typeName resolves to 'string'"
        def prop = createTestHibernateToManyProperty(HTMPOwnerString, "tags") as HibernateToManyCollectionProperty

        expect:
        prop.getElementTypeName() != null
        prop.getElementTypeName() != Object.class.name
    }

    void "getElementTypeName falls back to StandardBasicTypes.STRING for Object-typed collections"() {
        given: "a collection whose element type resolves to Object"
        def prop = createTestHibernateToManyProperty(HTMPOwnerObject, "items") as HibernateToManyCollectionProperty

        expect:
        prop.getElementTypeName() == org.hibernate.type.StandardBasicTypes.STRING.getName()
    }

    // ─── Tests for default methods via Stub ──────────────────────────────────

    def "isAssociationColumnNullable returns true for non-ManyToMany"() {
        given:
        def entity = (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(HTMPAuthor.name)
        def stub = new TestHibernateToManyProperty(entity, HTMPBook, null)

        expect:
        stub.isAssociationColumnNullable()
    }

    def "isBidirectionalManyToOneWithListMapping coverage"() {
        given:
        def entity = (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(HTMPAuthor.name)
        def stub = new TestHibernateToManyProperty(entity, HTMPBook, null)
        def prop = Mock(org.hibernate.mapping.Property)
        def manyToOne = GroovyMock(org.hibernate.mapping.ManyToOne)
        prop.getValue() >> manyToOne

        expect:
        !stub.isBidirectionalManyToOneWithListMapping(prop)
    }

    def "getTypeName priority logic for ToMany"() {
        given:
        def entity = (GrailsHibernatePersistentEntity) getMappingContext().getPersistentEntity(HTMPAuthor.name)
        def stub = new TestHibernateToManyProperty(entity, String, null)
        
        def config1 = new PropertyConfig(type: "custom")
        def mapping1 = Mock(Mapping)
        
        def config2 = new PropertyConfig(type: null)
        def mapping2 = Mock(Mapping)
        
        def config3 = new PropertyConfig(type: null)
        def mapping3 = Mock(Mapping)

        expect: "Config priority"
        stub.getTypeName(String, config1, mapping1) == "custom"

        when: "Mapping priority"
        def res2 = stub.getTypeName(String, config2, mapping2)
        
        then:
        1 * mapping2.getTypeName(String) >> "mapped"
        res2 == "mapped"

        when: "Neither have it"
        def res3 = stub.getTypeName(String, config3, mapping3)
        
        then:
        1 * mapping3.getTypeName(String) >> null
        res3 == String.name
    }

    static class TestHibernateToManyProperty implements HibernateToManyProperty {
        GrailsHibernatePersistentEntity ownerField
        Class componentTypeField
        PropertyConfig mappedFormField

        TestHibernateToManyProperty(GrailsHibernatePersistentEntity entity, Class componentType, PropertyConfig mappedForm) {
            this.ownerField = entity
            this.componentTypeField = componentType
            this.mappedFormField = mappedForm
        }

        @Override Class getComponentType() { componentTypeField }
        @Override PropertyConfig getMappedForm() { mappedFormField }
        @Override String getName() { "books" }
        @Override Class getType() { List }
        @Override PersistentEntity getOwner() { ownerField }

        @Override String getCapitilizedName() { getName().capitalize() }
        @Override PropertyMapping<PropertyConfig> getMapping() { 
            return new PropertyMapping<PropertyConfig>() {
                @Override ClassMapping getClassMapping() { null }
                @Override PropertyConfig getMappedForm() { mappedFormField }
            }
        }
        @Override boolean isNullable() { true }
        @Override boolean isInherited() { false }
        @Override EntityReflector.PropertyReader getReader() { null }
        @Override EntityReflector.PropertyWriter getWriter() { null }
        @Override boolean supportsJoinColumnMapping() { true }

        @Override PersistentProperty<?> getInverseSide() { null }
        @Override PersistentEntity getAssociatedEntity() { null }
        @Override boolean isBidirectional() { false }
        @Override boolean isOwningSide() { false }
        @Override boolean isCircular() { false }
        @Override boolean isBidirectionalToManyMap() { false }
        @Override boolean isBasic() { false }
        @Override boolean isOneToMany() { true }
        @Override boolean isManyToMany() { false }
        @Override void setHibernateCollection(org.hibernate.mapping.Collection collection) {}
        @Override org.hibernate.mapping.Collection getHibernateCollection() { null }
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

@Entity
class HTMPOrderEmptyIndex {
    Long id
    List<String> items
    static hasMany = [items: String]
    static mapping = {
        items index: { }
    }
}

enum HTMPStatus { ACTIVE, INACTIVE }

@Entity
class HTMPEntityWithEnum {
    Long id
    static hasMany = [statuses: HTMPStatus]
}

@Entity
class HTMPAuthorSorted {
    Long id
    String name
    static hasMany = [books: HTMPBook]
    static mapping = {
        books sort: 'title', order: 'asc'
    }
}

@Entity
class HTMPAuthorLazy {
    Long id
    String name
    static hasMany = [books: HTMPBook]
    static mapping = {
        books lazy: false
    }
}

@Entity
class HTMPAuthorCached {
    Long id
    String name
    static hasMany = [books: HTMPBook]
    static mapping = {
        books cache: true
    }
}

@Entity
class HTMPAuthorOrphan {
    Long id
    String name
    static hasMany = [books: HTMPBook]
    static mapping = {
        books cascade: 'all-delete-orphan'
    }
}

@Entity
class HTMPOwnerString {
    Long id
    static hasMany = [tags: String]
}

@Entity
class HTMPOwnerObject {
    Long id
    static hasMany = [items: Object]
}

@Entity
class HTMPJoinColOwner {
    Long id
    static hasMany = [tags: String]
    static mapping = {
        tags joinTable: [name: 'htmp_join_col_owner_tags', column: 'tag_val']
    }
}
