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
package org.grails.datastore.mapping.model

import java.beans.PropertyDescriptor

import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Custom
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.Identity
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.Simple
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.model.types.mapping.CustomWithMapping
import org.grails.datastore.mapping.model.types.mapping.ManyToOneWithMapping
import org.grails.datastore.mapping.model.types.mapping.OneToOneWithMapping
import org.grails.datastore.mapping.query.Query

class MappingFactorySpec extends Specification {

    KeyValueMappingContext mappingContext = new KeyValueMappingContext('test')
    MappingFactory factory = mappingContext.mappingFactory
    PersistentEntity owner = mappingContext.addPersistentEntity(MFOwner)

    private PropertyDescriptor descriptor(String name) {
        new PropertyDescriptor(name, MFOwner)
    }

    @Unroll
    void "isSimpleType(#type) == #expected"() {
        expect:
        factory.isSimpleType((Class) type) == expected

        where:
        type      | expected
        String    | true
        int       | true
        int[]     | true
        Date      | true
        MFColor   | true
        Object    | true
        List      | false
        MFOwner   | false
        MFOwner[] | false
        null      | false
    }

    void "simple types are also resolvable by name"() {
        expect:
        MappingFactory.isSimpleType('java.time.Instant')
        MappingFactory.isSimpleType('org.bson.types.ObjectId')
        !MappingFactory.isSimpleType('java.util.List')
        MappingFactory.IDENTITY_PROPERTY == 'id'
    }

    void "custom types are registered per target type and enums can share a marshaller"() {
        given:
        CustomTypeMarshaller marshaller = marshallerFor(MFMoney)
        CustomTypeMarshaller enumMarshaller = marshallerFor(Enum)

        expect:
        !factory.isCustomType(MFMoney)
        !factory.isCustomType(MFColor)
        factory.findCustomType(mappingContext, MFMoney) == null

        when:
        factory.registerCustomType(marshaller)
        factory.registerCustomType(enumMarshaller)

        then:
        factory.isCustomType(MFMoney)
        factory.isCustomType(MFColor)
        !factory.isSimpleType(MFColor)
        factory.findCustomType(mappingContext, MFMoney).is(marshaller)
        factory.findCustomType(mappingContext, Enum).is(enumMarshaller)
    }

    void "a marshaller that does not support the context is ignored"() {
        given:
        CustomTypeMarshaller unsupported = marshallerFor(MFMoney, false)
        factory.registerCustomType(unsupported)

        expect:
        factory.isCustomType(MFMoney)
        factory.findCustomType(mappingContext, MFMoney) == null
    }

    void "custom properties require a marshaller unless arbitrary custom types are allowed"() {
        when:
        factory.createCustom(owner, mappingContext, descriptor('money'))

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot create a custom type without a type converter for type ' + MFMoney

        when:
        CustomTypeMarshaller marshaller = marshallerFor(MFMoney)
        factory.registerCustomType(marshaller)
        Custom custom = factory.createCustom(owner, mappingContext, descriptor('money'))

        then:
        custom instanceof CustomWithMapping
        custom.customTypeMarshaller.is(marshaller)
        custom.mapping.mappedForm instanceof Property
        custom.mapping.classMapping.entity.is(owner)
    }

    void "an enum custom property falls back to the Enum marshaller"() {
        given:
        CustomTypeMarshaller enumMarshaller = marshallerFor(Enum)
        factory.registerCustomType(enumMarshaller)

        when:
        Custom custom = factory.createCustom(owner, mappingContext, descriptor('color'))

        then:
        custom.customTypeMarshaller.is(enumMarshaller)
    }

    void "property factories create the mapped property types with a mapping"() {
        expect:
        factory.createIdentity(owner, mappingContext, descriptor('id')) instanceof Identity
        factory.createIdentity(owner, mappingContext, descriptor('id')).mapping.mappedForm.targetName == 'id'
        factory.createSimple(owner, mappingContext, descriptor('name')) instanceof Simple
        factory.createOneToOne(owner, mappingContext, descriptor('other')) instanceof OneToOneWithMapping
        factory.createManyToOne(owner, mappingContext, descriptor('other')) instanceof ManyToOneWithMapping
        factory.createOneToMany(owner, mappingContext, descriptor('others')) instanceof OneToMany
        factory.createManyToMany(owner, mappingContext, descriptor('others')) instanceof ManyToMany
        factory.createEmbedded(owner, mappingContext, descriptor('other')) instanceof Embedded
        factory.createEmbeddedCollection(owner, mappingContext, descriptor('others')) instanceof EmbeddedCollection
        factory.createBasicCollection(owner, mappingContext, descriptor('tags')) instanceof Basic
        factory.createBasicCollection(owner, mappingContext, descriptor('tags')).componentType == String
        factory.createBasicCollection(owner, mappingContext, descriptor('codes')).componentType == String
        factory.createBasicCollection(owner, mappingContext, descriptor('untyped')).componentType == Object
        factory.createOneToOne(owner, mappingContext, descriptor('other')).mapping.classMapping.entity.is(owner)
    }

    void "tenant id properties are derived"() {
        when:
        TenantId tenantId = factory.createTenantId(owner, mappingContext, descriptor('tenantId'))

        then:
        tenantId.mapping.mappedForm.derived
        !factory.createSimple(owner, mappingContext, descriptor('name')).mapping.mappedForm.derived
    }

    void "basic collections of enums pick up the enum marshaller"() {
        given:
        CustomTypeMarshaller enumMarshaller = marshallerFor(Enum)

        expect:
        factory.createBasicCollection(owner, mappingContext, descriptor('colors'), MFColor).customTypeMarshaller == null

        when:
        factory.registerCustomType(enumMarshaller)
        Basic basic = factory.createBasicCollection(owner, mappingContext, descriptor('colors'), MFColor)

        then:
        basic.customTypeMarshaller.is(enumMarshaller)

        when:
        CustomTypeMarshaller colorMarshaller = marshallerFor(MFColor)
        factory.registerCustomType(colorMarshaller)

        then:
        factory.createBasicCollection(owner, mappingContext, descriptor('colors'), MFColor).customTypeMarshaller.is(colorMarshaller)
    }

    void "identity mappings are created lazily by default and eagerly from a property"() {
        given:
        ClassMapping classMapping = owner.mapping

        when:
        IdentityMapping lazy = factory.createIdentityMapping(classMapping)
        IdentityMapping fromNull = factory.createDefaultIdentityMapping(classMapping, null)
        Property property = new Property(name: 'key', generator: 'sequence')
        IdentityMapping fromProperty = factory.createDefaultIdentityMapping(classMapping, property)

        then:
        lazy instanceof DefaultIdentityMapping
        lazy.identifierName == ['id'] as String[]
        lazy.generator == ValueGenerator.AUTO
        fromNull.identifierName == ['id'] as String[]
        fromNull.generator == ValueGenerator.AUTO
        fromNull.mappedForm == null
        fromProperty.identifierName == ['key'] as String[]
        fromProperty.generator == ValueGenerator.SEQUENCE
        fromProperty.mappedForm.is(property)
    }

    void "association descriptions include both ends"() {
        given:
        PersistentEntity other = mappingContext.addPersistentEntity(MFOther)
        ToOne association = factory.createManyToOne(owner, mappingContext, descriptor('other'))
        association.associatedEntity = other

        expect:
        MappingFactory.associationtoString('many-to-one: ', association) == 'many-to-one: ' + MFOwner.name + '-> other ->' + MFOther.name
        association.toString() == 'many-to-one: ' + MFOwner.name + '-> other ->' + MFOther.name
    }

    void "property descriptors are created from meta properties"() {
        expect:
        factory.createPropertyDescriptor(MFOwner, MFOwner.metaClass.getMetaProperty('name')).name == 'name'
    }

    private static CustomTypeMarshaller marshallerFor(Class target, boolean supported = true) {
        new CustomTypeMarshaller() {
            boolean supports(MappingContext context) { supported }
            boolean supports(Datastore datastore) { supported }
            Class getTargetType() { target }
            Object write(PersistentProperty property, Object value, Object nativeTarget) { value }
            Object query(PersistentProperty property, Query.PropertyCriterion criterion, Object nativeQuery) { nativeQuery }
            Object read(PersistentProperty property, Object source) { source }
        }
    }
}

class MFOwner {
    Long id
    String name
    String tenantId
    MFMoney money
    MFColor color
    MFOther other
    Set others
    List<String> tags
    Set<MFColor> colors
    String[] codes
    Set untyped
}

class MFOther {
    Long id
    String name
}

class MFMoney {
    BigDecimal amount
}

enum MFColor {
    RED, GREEN
}
