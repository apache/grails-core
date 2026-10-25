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

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.CompositeMoney
import org.grails.orm.hibernate.CompositeMoneyType
import org.grails.orm.hibernate.CompositeTier
import org.grails.orm.hibernate.CompositeTierType
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.*
import org.grails.orm.hibernate.cfg.domainbinding.binder.*

import org.hibernate.MappingException
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.OneToOne
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Value
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Component
import org.hibernate.mapping.SimpleValue
import org.hibernate.mapping.Table
import org.hibernate.boot.spi.MetadataBuildingContext
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.TableForManyCalculator
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.orm.hibernate.cfg.PropertyConfig

import java.beans.PropertyDescriptor

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH

class GrailsPropertyBinderSpec extends HibernateGormDatastoreSpec {

    protected Map getBinders(GrailsDomainBinder binder, InFlightMetadataCollector collector = getCollector()) {
        MetadataBuildingContext metadataBuildingContext = binder.getMetadataBuildingContext()
        PersistentEntityNamingStrategy namingStrategy = binder.getNamingStrategy()
        JdbcEnvironment jdbcEnvironment = binder.getJdbcEnvironment()
        BackticksRemover backticksRemover = new BackticksRemover()
        DefaultColumnNameFetcher defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy, backticksRemover)
        ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover)
        
        SimpleValueBinder simpleValueBinder = new SimpleValueBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment)
        CompositeUserTypeBinder compositeUserTypeBinder = new CompositeUserTypeBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment)
        EnumTypeBinder enumTypeBinderToUse = new EnumTypeBinder(metadataBuildingContext, columnNameForPropertyAndPathFetcher, namingStrategy)
        SimpleValueColumnFetcher simpleValueColumnFetcher = new SimpleValueColumnFetcher()
        CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(
                collector,
                new org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator(),
                namingStrategy,
                defaultColumnNameFetcher,
                backticksRemover,
                simpleValueBinder
        )
        OneToOneBinder oneToOneBinder = new OneToOneBinder(metadataBuildingContext, simpleValueBinder)
        ManyToOneBinder manyToOneBinder = new ManyToOneBinder(metadataBuildingContext, namingStrategy, simpleValueBinder, new ManyToOneValuesBinder(), compositeIdentifierToManyToOneBinder)
        ForeignKeyOneToOneBinder foreignKeyOneToOneBinder = new ForeignKeyOneToOneBinder(manyToOneBinder, simpleValueColumnFetcher)

        TableForManyCalculator tableForManyCalculator = new TableForManyCalculator(namingStrategy, collector)
        CollectionBinder collectionBinder = new CollectionBinder(
                metadataBuildingContext,
                namingStrategy,
                simpleValueBinder,
                enumTypeBinderToUse,
                manyToOneBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher,
                new org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder(metadataBuildingContext),
                collector,
                tableForManyCalculator
        )
        PropertyFromValueCreator propertyFromValueCreator = new PropertyFromValueCreator()
        ComponentUpdater componentUpdater = new ComponentUpdater(propertyFromValueCreator)
        ComponentBinder componentBinder = new ComponentBinder(
                metadataBuildingContext,
                binder.getMappingCacheHolder(),
                componentUpdater
        )
        GrailsPropertyBinder propertyBinder = new GrailsPropertyBinder(
                enumTypeBinderToUse,
                componentBinder,
                collectionBinder,
                simpleValueBinder,
                compositeUserTypeBinder,
                oneToOneBinder,
                manyToOneBinder,
                foreignKeyOneToOneBinder
        )
        componentBinder.setGrailsPropertyBinder(propertyBinder)
        GrailsPropertyBinder legacyPropertyBinder = new GrailsPropertyBinder(
                enumTypeBinderToUse,
                componentBinder,
                collectionBinder,
                simpleValueBinder,
                oneToOneBinder,
                manyToOneBinder,
                foreignKeyOneToOneBinder
        )
        
        return [
            propertyBinder: propertyBinder,
            legacyPropertyBinder: legacyPropertyBinder,
            collectionBinder: collectionBinder
        ]
    }

    protected void bindRoot(GrailsDomainBinder binder, GrailsHibernatePersistentEntity entity, InFlightMetadataCollector mappings) {
        entity.setPersistentClass(new RootClass(binder.getMetadataBuildingContext()))
    }

    void setupSpec() {
        manager.registerDomainClasses(
            PropertyBinderSpecSimpleBook,
            PropertyBinderSpecEnumBook,
            PropertyBinderSpecEnumCollection,
            PropertyBinderSpecAuthor,
            PropertyBinderSpecPet,
            PropertyBinderSpecEmployee,
            PropertyBinderSpecSerializableEntity,
            PropertyBinderSpecCustomEntity,
            PropertyBinderSpecCustomUserTypeCollection,
            PropertyBinderSpecCompositeUserTypeEntity,
            PropertyBinderSpecCompositeEnumEntity,
            PropertyBinderSpecHasOneOwner,
            PropertyBinderSpecHasOneProfile,
            PropertyBinderSpecFKOwner,
            PropertyBinderSpecFKChild,
            PropertyBinderSpecTenantEntity
        )
    }

    void "Test bind simple property"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecSimpleBook) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("SIMPLE_BOOK"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def titleProp = persistentEntity.getPropertyByName("title") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(titleProp, null, EMPTY_PATH)

        then:
        value instanceof BasicValue
        ((BasicValue)value).typeName == String.name
    }

    void "Test bind enum property"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecEnumBook) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("ENUM_BOOK"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def statusProp = persistentEntity.getPropertyByName("status") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(statusProp, null, EMPTY_PATH)

        then:
        value instanceof BasicValue
        ((BasicValue)value).enumerationStyle == jakarta.persistence.EnumType.STRING
    }

    void "Test bind hasMany of enum falls through to the collection binder"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecEnumCollection) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("ENUM_COLLECTION"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def statusesProp = persistentEntity.getPropertyByName("statuses") as HibernatePersistentProperty

        then: "the property is a HibernateEnumProperty, but flagged as a collection element"
        statusesProp instanceof HibernateBasicEnumProperty
        ((HibernateEnumProperty) statusesProp).isCollectionElement()

        when:
        Value value = propertyBinder.bindProperty(statusesProp, null, EMPTY_PATH)

        then: "it is bound as a collection with a join table, not as a scalar enum BasicValue"
        value instanceof org.hibernate.mapping.Set
    }

    void "Test bind many-to-one"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecPet) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("PET"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def ownerProp = persistentEntity.getPropertyByName("owner") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(ownerProp, null, EMPTY_PATH)

        then:
        value instanceof ManyToOne
        ((ManyToOne)value).referencedEntityName == PropertyBinderSpecAuthor.name
    }

    void "Test bind to-many collection"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecAuthor) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("AUTHOR"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def petsProp = persistentEntity.getPropertyByName("pets") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(petsProp, null, EMPTY_PATH)

        then:
        value instanceof org.hibernate.mapping.Set
    }

    void "Test bind embedded property"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecEmployee) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("EMPLOYEE"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def addressProp = persistentEntity.getPropertyByName("address") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(addressProp, null, EMPTY_PATH)

        then:
        value instanceof Component
        ((Component)value).componentClassName == PropertyBinderSpecAddress.name
    }

    void "Test bind serializable collection type"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecSerializableEntity) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("SERIALIZABLE_ENTITY"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def tagsProp = persistentEntity.getPropertyByName("tags") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(tagsProp, null, EMPTY_PATH)

        then:
        value instanceof BasicValue
        ((BasicValue)value).typeName == "serializable"
    }

    void "Test bind custom property type"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecCustomEntity) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("CUSTOM_ENTITY"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def dataProp = persistentEntity.getPropertyByName("data") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(dataProp, null, EMPTY_PATH)

        then: "the type: mapping DSL routes this through isUserButNotCollectionType(), as a plain HibernateSimpleProperty"
        !(dataProp instanceof HibernateCustomProperty)
        dataProp.isUserButNotCollectionType()
        value instanceof BasicValue
    }

    void "Test bind composite user type property"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecCompositeUserTypeEntity) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("COMPOSITE_USER_TYPE_ENTITY"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def priceProp = persistentEntity.getPropertyByName("price") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(priceProp, null, EMPTY_PATH)

        then: "a type: mapping naming a CompositeUserType is bound as a component of that type, not as a basic value"
        priceProp.getCompositeUserType() == CompositeMoneyType
        priceProp.isUserButNotCollectionType()
        value instanceof Component
        (value as Component).typeName == CompositeMoneyType.name
        (value as Component).componentClassName == CompositeMoney.name

        and: "the component has one property per attribute of the embeddable class, in alphabetical order"
        (value as Component).properties*.name == ['amount', 'currency']
        (value as Component).properties.collect { it.value.columns.first().name } == ['price_value', 'price_currency']
        rootClass.table.columns*.name == ['price_value', 'price_currency']
    }

    void "Test bind enum property mapped with a composite user type"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecCompositeEnumEntity) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("COMPOSITE_ENUM_ENTITY"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def tierProp = persistentEntity.getPropertyByName("tier") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(tierProp, null, EMPTY_PATH)

        then: "the enum property is bound as a component of its composite user type, not as an enum column"
        tierProp instanceof HibernateEnumProperty
        tierProp.getCompositeUserType() == CompositeTierType
        value instanceof Component
        (value as Component).typeName == CompositeTierType.name
        (value as Component).componentClassName == CompositeTier.name
        (value as Component).properties*.name == ['code', 'rank']
        rootClass.table.columns*.name == ['tier_code', 'tier_rank']
    }

    void "Test a property binder created without a composite user type binder binds other properties and rejects a composite user type"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).legacyPropertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecCompositeUserTypeEntity) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("COMPOSITE_USER_TYPE_ENTITY"))
        persistentEntity.setPersistentClass(rootClass)

        when: "a simple property is bound"
        def nameProp = persistentEntity.getPropertyByName("name") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(nameProp, null, EMPTY_PATH)

        then:
        value instanceof BasicValue

        when: "a property mapped with a composite user type is bound"
        def priceProp = persistentEntity.getPropertyByName("price") as HibernatePersistentProperty
        propertyBinder.bindProperty(priceProp, null, EMPTY_PATH)

        then: "the binder explains that it cannot bind it"
        MappingException e = thrown()
        e.message.contains('price')
        e.message.contains(PropertyBinderSpecCompositeUserTypeEntity.name)
        e.message.contains(CompositeMoneyType.name)
        e.message.contains(CompositeUserTypeBinder.simpleName)
    }

    void "Test bind a genuine HibernateCustomProperty (GORM-detected custom type marshaller, no type: mapping)"() {
        given: "a HibernateCustomProperty built the way HibernateMappingFactory#createCustom does: no type: " +
                "mapping is set, so isUserButNotCollectionType() is false and the instanceof HibernateCustomProperty " +
                "branch is the only one that can match"
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecSimpleBook) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("SIMPLE_BOOK"))
        persistentEntity.setPersistentClass(rootClass)

        def propertyDescriptor = new PropertyDescriptor("title", PropertyBinderSpecSimpleBook)
        def marshaller = Mock(CustomTypeMarshaller)
        def customProp = new HibernateCustomProperty(persistentEntity, getMappingContext(), propertyDescriptor, marshaller)
        customProp.setMapping(new PropertyMapping<PropertyConfig>() {
            ClassMapping getClassMapping() { null }
            PropertyConfig getMappedForm() { new PropertyConfig() }
        })

        expect: "no type: mapping means the isUserButNotCollectionType() branch cannot intercept it"
        !customProp.isUserButNotCollectionType()

        when:
        Value value = propertyBinder.bindProperty(customProp, null, EMPTY_PATH)

        then:
        value instanceof BasicValue
    }

    void "Test bind collection with custom UserType"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecCustomUserTypeCollection) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("CUSTOM_COLLECTION"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def categoriesProp = persistentEntity.getPropertyByName("categories") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(categoriesProp, null, EMPTY_PATH)

        then:
        value instanceof BasicValue
        !(value instanceof org.hibernate.mapping.Collection)
    }

    void "Test bind valid hasOne property (HibernateOneToOneProperty.isValidHibernateOneToOne = true)"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecHasOneOwner) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("HAS_ONE_OWNER"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def profileProp = persistentEntity.getPropertyByName("profile") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(profileProp, null, EMPTY_PATH)

        then:
        value instanceof OneToOne
    }

    void "Test bind FK one-to-one property (HibernateOneToOneProperty.isValidHibernateOneToOne = false)"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecFKOwner) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("FK_OWNER"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def childProp = persistentEntity.getPropertyByName("child") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(childProp, null, EMPTY_PATH)

        then:
        value instanceof ManyToOne
    }

    void "Test bind tenantId property"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        def persistentEntity = getPersistentEntity(PropertyBinderSpecTenantEntity) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table("TENANT_ENTITY"))
        persistentEntity.setPersistentClass(rootClass)

        when:
        def tenantIdProp = persistentEntity.getPropertyByName("tenantId") as HibernatePersistentProperty
        Value value = propertyBinder.bindProperty(tenantIdProp, null, EMPTY_PATH)

        then:
        tenantIdProp instanceof HibernateTenantIdProperty
        value instanceof SimpleValue
    }

    void "Test unsupported property type"() {
        given:
        def binder = getGrailsDomainBinder()
        def propertyBinder = getBinders(binder).propertyBinder
        HibernatePersistentProperty mockProp = Mock(HibernatePersistentProperty)
        mockProp.getName() >> "unsupported"
        mockProp.getTable() >> new Table("MOCK")

        when:
        propertyBinder.bindProperty(mockProp, null, EMPTY_PATH)

        then:
        RuntimeException e = thrown()
        e.message.contains "Unsupported property type"
    }
}

@Entity
class PropertyBinderSpecSimpleBook {
    Long id
    String title
}

@Entity
class PropertyBinderSpecEnumBook {
    Long id
    java.util.concurrent.TimeUnit status
}

@Entity
class PropertyBinderSpecEnumCollection {
    Long id
    Set<java.util.concurrent.TimeUnit> statuses
    static hasMany = [statuses: java.util.concurrent.TimeUnit]
}

@Entity
class PropertyBinderSpecAuthor {
    Long id
    static hasMany = [pets: PropertyBinderSpecPet]
}

@Entity
class PropertyBinderSpecPet {
    Long id
    PropertyBinderSpecAuthor owner
}

@Entity
class PropertyBinderSpecEmployee {
    Long id
    PropertyBinderSpecAddress address
    static embedded = ['address']
}

class PropertyBinderSpecAddress implements Serializable {
    String city
}

@Entity
class PropertyBinderSpecSerializableEntity {
    Long id
    List<String> tags
    static mapping = {
        tags type: 'serializable'
    }
}

@Entity
class PropertyBinderSpecCustomEntity {
    Long id
    String data
    static mapping = {
        data type: 'org.hibernate.type.YesNoConverter'
    }
}

@Entity
class PropertyBinderSpecCompositeUserTypeEntity {
    Long id
    String name
    CompositeMoney price
    static mapping = {
        price type: CompositeMoneyType, {
            column name: 'price_value'
            column name: 'price_currency'
        }
    }
}

@Entity
class PropertyBinderSpecCompositeEnumEntity {
    Long id
    CompositeTier tier
    static mapping = {
        tier type: CompositeTierType, {
            column name: 'tier_code'
            column name: 'tier_rank'
        }
    }
}

@Entity
class PropertyBinderSpecCustomUserTypeCollection {
    Long id
    Set<String> categories
    static mapping = {
        categories type: 'org.hibernate.type.YesNoConverter' 
    }
}

@Entity
class PropertyBinderSpecHasOneProfile {
    Long id
    String bio
    PropertyBinderSpecHasOneOwner owner
    static belongsTo = [owner: PropertyBinderSpecHasOneOwner]
}

@Entity
class PropertyBinderSpecHasOneOwner {
    Long id
    static hasOne = [profile: PropertyBinderSpecHasOneProfile]
}

@Entity
class PropertyBinderSpecFKChild {
    Long id
    PropertyBinderSpecFKOwner owner
    static belongsTo = [owner: PropertyBinderSpecFKOwner]
}

@Entity
class PropertyBinderSpecFKOwner {
    Long id
    PropertyBinderSpecFKChild child
}

@Entity
class PropertyBinderSpecTenantEntity implements grails.gorm.MultiTenant<PropertyBinderSpecTenantEntity> {
    Long id
    Integer tenantId
}
