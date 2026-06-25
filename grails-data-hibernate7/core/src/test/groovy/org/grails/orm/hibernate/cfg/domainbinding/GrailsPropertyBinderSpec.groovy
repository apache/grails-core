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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.*
import org.grails.orm.hibernate.cfg.domainbinding.binder.*

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
        EnumTypeBinder enumTypeBinderToUse = new EnumTypeBinder(metadataBuildingContext, columnNameForPropertyAndPathFetcher, namingStrategy)
        SimpleValueColumnFetcher simpleValueColumnFetcher = new SimpleValueColumnFetcher()
        CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(
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
                oneToOneBinder,
                manyToOneBinder,
                foreignKeyOneToOneBinder
        )
        componentBinder.setGrailsPropertyBinder(propertyBinder)
        
        return [
            propertyBinder: propertyBinder,
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
            PropertyBinderSpecAuthor,
            PropertyBinderSpecPet,
            PropertyBinderSpecEmployee,
            PropertyBinderSpecSerializableEntity,
            PropertyBinderSpecCustomEntity,
            PropertyBinderSpecCustomUserTypeCollection,
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
