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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import spock.lang.Specification
import org.hibernate.MappingException
import org.hibernate.mapping.Property
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Value
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.Mapping
import java.lang.annotation.RetentionPolicy

class HibernateAssociationSpec extends Specification {

    def 'isAssociationColumnNullable defaults to true'() {
        given:
        def assoc = new TestHibernateAssociation()

        expect:
        assoc.isAssociationColumnNullable()
    }

    def 'getHibernateInverseSide returns casted inverse side'() {
        given:
        def inverse = Mock(HibernateAssociation)
        def assoc = new TestHibernateAssociation(inverseSide: inverse)

        expect:
        assoc.getHibernateInverseSide() == inverse
    }

    def 'getHibernateAssociatedEntity returns casted associated entity'() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def assoc = new TestHibernateAssociation(associatedEntity: entity)

        expect:
        assoc.getHibernateAssociatedEntity() == entity
    }

    def 'getReferencedEntityName returns entity name'() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        entity.getName() >> 'Foo'
        def assoc = new TestHibernateAssociation(associatedEntity: entity)

        expect:
        assoc.getReferencedEntityName() == 'Foo'
    }

    def 'validateAssociation throws exception if userType is present'() {
        given:
        def config = new PropertyConfig(type: Object)
        def assoc = new TestHibernateAssociation(name: 'myAssoc', type: List, mappedForm: config)

        when:
        assoc.validateAssociation()

        then:
        def e = thrown(MappingException)
        e.message == 'Cannot bind association property [myAssoc] of type [interface java.util.List] to a user type'
    }

    def 'validateAssociation does not throw exception if userType is null'() {
        given:
        def assoc = new TestHibernateAssociation()

        when:
        assoc.validateAssociation()

        then:
        noExceptionThrown()
    }

    def 'isBidirectionalManyToOneWithListMapping returns true for bidirectional list mapping'() {
        given:
        def inverse = Mock(PersistentProperty)
        def assoc = new TestHibernateAssociation(
            bidirectional: true,
            inverseSide: inverse,
            type: List
        )
        def prop = Mock(Property)
        def manyToOne = GroovyMock(ManyToOne)
        prop.getValue() >> manyToOne

        expect:
        assoc.isBidirectionalManyToOneWithListMapping(prop)
    }

    def 'isBidirectionalManyToOneWithListMapping returns false for various conditions'() {
        expect:
        assoc.isBidirectionalManyToOneWithListMapping(prop) == result

        where:
        assoc | prop | result
        new TestHibernateAssociation(bidirectional: false) | createMockProperty(GroovyMock(ManyToOne)) | false
        new TestHibernateAssociation(bidirectional: true, inverseSide: null) | createMockProperty(GroovyMock(ManyToOne)) | false
        new TestHibernateAssociation(bidirectional: true, inverseSide: Mock(PersistentProperty), type: String) | createMockProperty(GroovyMock(ManyToOne)) | false
        new TestHibernateAssociation(bidirectional: true, inverseSide: Mock(PersistentProperty), type: List) | null | false
        new TestHibernateAssociation(bidirectional: true, inverseSide: Mock(PersistentProperty), type: List) | createMockProperty(GroovyMock(BasicValue)) | false
    }

    private Property createMockProperty(Value value) {
        def prop = Mock(Property)
        prop.getValue() >> value
        return prop
    }

    def 'getTypeName returns null if propertyType matches type and associated entity exists'() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def assoc = new TestHibernateAssociation(type: List, associatedEntity: entity)

        expect:
        assoc.getTypeName(List, null, null) == null
    }

    def 'getTypeName calls super if conditions not met'() {
        given:
        def assoc = new TestHibernateAssociation(type: List, associatedEntity: null)

        expect: 'It falls back to HibernatePersistentProperty.getTypeName which returns the class name for non-enums'
        assoc.getTypeName(List, null, null) == List.name
    }

    // Additional tests for coverage of HibernatePersistentProperty methods through HibernateAssociation
    def 'isLazyAble returns true for HibernateAssociation'() {

        given:
        def assoc = new TestHibernateAssociation()

        expect:
        assoc.isLazyAble()
    }

    def 'isUserButNotCollectionType coverage'() {
        given:
        def config = new PropertyConfig(type: Object)
        def assoc = new TestHibernateAssociation(mappedForm: config)

        expect:
        assoc.isUserButNotCollectionType()
    }

    def 'isEnumType coverage'() {
        given:
        def assoc = new TestHibernateAssociation(type: RetentionPolicy)

        expect:
        assoc.isEnumType()
    }

    // Stub implementation to test default methods of HibernateAssociation
    static class TestHibernateAssociation implements HibernateAssociation {

        String name
        Class type
        PersistentProperty<?> inverseSide
        GrailsHibernatePersistentEntity associatedEntity
        boolean bidirectional
        boolean owningSide
        boolean circular
        boolean bidirectionalToManyMap
        PropertyConfig mappedForm

        @Override PersistentProperty<?> getInverseSide() { inverseSide }
        @Override PersistentEntity getAssociatedEntity() { associatedEntity }
        @Override boolean isBidirectional() { bidirectional }
        @Override boolean isOwningSide() { owningSide }
        @Override boolean isCircular() { circular }
        @Override boolean isBidirectionalToManyMap() { bidirectionalToManyMap }
        
        @Override String getName() { name }
        @Override String getCapitilizedName() { name?.capitalize() }
        @Override Class getType() { type }
        
        @Override PropertyMapping<PropertyConfig> getMapping() {
            return new PropertyMapping<PropertyConfig>() {
                @Override ClassMapping getClassMapping() { null }
                @Override PropertyConfig getMappedForm() { mappedForm }
            }
        }

        @Override PropertyConfig getMappedForm() { mappedForm }
        @Override PersistentEntity getOwner() { null }
        @Override boolean isNullable() { true }
        @Override boolean isInherited() { false }
        @Override EntityReflector.PropertyReader getReader() { null }
        @Override EntityReflector.PropertyWriter getWriter() { null }

        @Override String getTypeName(PropertyConfig config, Mapping mapping) { 'defaultType' }
        @Override boolean supportsJoinColumnMapping() { true }
    }
}
