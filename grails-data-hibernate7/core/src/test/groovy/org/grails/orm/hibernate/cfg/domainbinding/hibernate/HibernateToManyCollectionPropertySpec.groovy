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

import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.type.StandardBasicTypes
import org.hibernate.mapping.Collection
import spock.lang.Specification

class HibernateToManyCollectionPropertySpec extends Specification {

    static class TestPropertyMapping implements PropertyMapping<PropertyConfig> {
        private final GrailsHibernatePersistentEntity owner
        private final PropertyConfig mapping

        TestPropertyMapping(GrailsHibernatePersistentEntity owner, PropertyConfig mapping) {
            this.owner = owner
            this.mapping = mapping
        }

        @Override
        ClassMapping getClassMapping() { owner.getMapping() }

        @Override
        PropertyConfig getMappedForm() { mapping }
    }

    static class TestToManyCollectionProperty implements HibernateToManyCollectionProperty {
        private final GrailsHibernatePersistentEntity owner
        private final String name
        private final PropertyConfig mapping
        Class<?> componentType
        String typeName
        Collection hibernateCollection

        TestToManyCollectionProperty(GrailsHibernatePersistentEntity owner, String name, PropertyConfig mapping) {
            this.owner = owner
            this.name = name
            this.mapping = mapping
        }

        @Override
        Collection getHibernateCollection() { hibernateCollection }

        @Override
        void setHibernateCollection(Collection collection) { this.hibernateCollection = collection }

        @Override
        PropertyConfig getHibernateMappedForm() { mapping }

        @Override
        String getName() { name }

        @Override
        GrailsHibernatePersistentEntity getHibernateOwner() { owner }

        @Override
        Class<?> getComponentType() { componentType }

        @Override
        String getTypeName() { typeName }

        @Override
        PersistentEntity getOwner() { owner }

        @Override
        PropertyMapping<PropertyConfig> getMapping() {
            return new TestPropertyMapping(owner, mapping)
        }

        @Override
        Class getType() { java.util.Collection.class }

        @Override
        boolean isNullable() { true }

        @Override
        boolean isInherited() { false }

        @Override
        org.grails.datastore.mapping.reflect.EntityReflector.PropertyReader getReader() { null }

        @Override
        org.grails.datastore.mapping.reflect.EntityReflector.PropertyWriter getWriter() { null }

        @Override
        String getCapitilizedName() { name.substring(0, 1).toUpperCase() + name.substring(1) }

        @Override
        boolean isBidirectionalToManyMap() { false }

        @Override
        boolean isCircular() { false }

        @Override
        boolean isOwningSide() { true }

        @Override
        boolean isBidirectional() { false }

        @Override
        PersistentEntity getAssociatedEntity() { null }

        @Override
        PersistentProperty getInverseSide() { null }
    }

    def "test getElementTypeName uses componentType when available"() {
        given:
        def property = new TestToManyCollectionProperty(Mock(GrailsHibernatePersistentEntity), "tags", new PropertyConfig())
        property.setComponentType(String.class)

        expect:
        property.getElementTypeName() == String.class.name
    }

    def "test getElementTypeName falls back to getTypeName when componentType is null"() {
        given:
        def property = new TestToManyCollectionProperty(Mock(GrailsHibernatePersistentEntity), "tags", new PropertyConfig())
        property.setComponentType(null)
        property.setTypeName("fallback_type")

        expect:
        property.getElementTypeName() == "fallback_type"
    }

    def "test getElementTypeName falls back to STRING when typeName is null"() {
        given:
        def property = new TestToManyCollectionProperty(Mock(GrailsHibernatePersistentEntity), "tags", new PropertyConfig())
        property.setComponentType(null)
        property.setTypeName(null)

        expect:
        property.getElementTypeName() == StandardBasicTypes.STRING.getName()
    }

    def "test getElementTypeName falls back to STRING when typeName is Object"() {
        given:
        def property = new TestToManyCollectionProperty(Mock(GrailsHibernatePersistentEntity), "tags", new PropertyConfig())
        property.setComponentType(null)
        property.setTypeName(Object.class.name)

        expect:
        property.getElementTypeName() == StandardBasicTypes.STRING.getName()
    }

    def "test getRole with path"() {
        given:
        GrailsHibernatePersistentEntity owner = Mock(GrailsHibernatePersistentEntity) {
            getName() >> "com.example.Book"
        }
        def property = new TestToManyCollectionProperty(owner, "tags", new PropertyConfig())

        expect:
        property.getRole("") == "com.example.Book.tags"
    }

    def "test getIndexColumnName fallback"() {
        given:
        def property = new TestToManyCollectionProperty(Mock(GrailsHibernatePersistentEntity), "tags", new PropertyConfig())
        PersistentEntityNamingStrategy namingStrategy = Mock(PersistentEntityNamingStrategy)

        when:
        String indexColumn = property.getIndexColumnName(namingStrategy)

        then:
        1 * namingStrategy.resolveColumnName("tags") >> "tags_column"
        indexColumn == "tags_column_idx"
    }

    def "test getMapElementName fallback"() {
        given:
        def property = new TestToManyCollectionProperty(Mock(GrailsHibernatePersistentEntity), "tags", new PropertyConfig())
        PersistentEntityNamingStrategy namingStrategy = Mock(PersistentEntityNamingStrategy)

        when:
        String mapElement = property.getMapElementName(namingStrategy)

        then:
        1 * namingStrategy.resolveColumnName("tags") >> "tags_column"
        mapElement == "tags_column_elt"
    }
}
