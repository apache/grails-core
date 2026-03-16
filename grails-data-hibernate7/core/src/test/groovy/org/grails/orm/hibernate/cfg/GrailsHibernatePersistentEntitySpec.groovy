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

package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

class GrailsHibernatePersistentEntitySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([
                Simple,
                CustomDiscriminator,
                NumericDiscriminator,
                Vehicle,
                Car,
                Truck,
                Person,
                AddressOwner,
                CustomTableEntity,
                CustomTableNameEntity,
                DerivedPropertyEntity
        ])
    }

    void "test getTableName"() {
        given:
        GrailsHibernatePersistentEntity simple = getPersistentEntity(Simple) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity custom = getPersistentEntity(CustomTableNameEntity) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity car = getPersistentEntity(Car) as GrailsHibernatePersistentEntity
        def namingStrategy = Mock(PersistentEntityNamingStrategy)

        when: "Basic entity with no explicit table name"
        def name1 = simple.getTableName(namingStrategy)

        then:
        1 * namingStrategy.resolveTableName(simple) >> "resolved_simple"
        name1 == "resolved_simple"

        when: "Entity with explicit table name"
        def name2 = custom.getTableName(namingStrategy)

        then:
        0 * namingStrategy.resolveTableName(custom)
        name2 == "my_custom_table"

        when: "Subclass in table-per-hierarchy using root table name"
        def name3 = car.getTableName(namingStrategy)

        then:
        1 * namingStrategy.resolveTableName(_) >> "vehicle_table"
        name3 == "vehicle_table"
    }

    void "test buildDiscriminatorSet for simple entity"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(Simple) as GrailsHibernatePersistentEntity

        expect:
        entity.buildDiscriminatorSet() == ["'Simple'"] as Set
    }

    void "test buildDiscriminatorSet with custom discriminator value"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(CustomDiscriminator) as GrailsHibernatePersistentEntity

        expect:
        entity.buildDiscriminatorSet() == ["'custom_val'"] as Set
    }

    void "test buildDiscriminatorSet with numeric discriminator type"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(NumericDiscriminator) as GrailsHibernatePersistentEntity

        expect:
        entity.buildDiscriminatorSet() == ["1"] as Set
    }

    void "test buildDiscriminatorSet with hierarchy"() {
        given:
        GrailsHibernatePersistentEntity vehicle = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity

        expect:
        vehicle.buildDiscriminatorSet() == ["'Vehicle'", "'Car'", "'Truck'"] as Set
    }

    void "test getHibernateRootEntity and getRootMapping"() {
        given:
        GrailsHibernatePersistentEntity car = getPersistentEntity(Car) as GrailsHibernatePersistentEntity

        expect:
        car.hibernateRootEntity.javaClass == Vehicle
        car.rootMapping != null
    }

    void "test isTablePerHierarchySubclass"() {
        given:
        GrailsHibernatePersistentEntity vehicle = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity car = getPersistentEntity(Car) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity simple = getPersistentEntity(Simple) as GrailsHibernatePersistentEntity

        expect:
        vehicle.isTablePerHierarchySubclass() == false
        car.isTablePerHierarchySubclass() == true
        simple.isTablePerHierarchySubclass() == false
    }

    void "test getDiscriminatorValue"() {
        given:
        GrailsHibernatePersistentEntity vehicle = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity custom = getPersistentEntity(CustomDiscriminator) as GrailsHibernatePersistentEntity

        expect:
        vehicle.getDiscriminatorValue() == "Vehicle"
        custom.getDiscriminatorValue() == "custom_val"
    }

    void "test getPersistentPropertiesToBind"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(Person) as GrailsHibernatePersistentEntity

        when:
        def props = entity.getPersistentPropertiesToBind()

        then:
        props.any { it.name == "name" }
        !props.any { it.name == "id" }
        !props.any { it.name == "version" }
    }

    void "test getChildEntities"() {
        given:
        GrailsHibernatePersistentEntity vehicle = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity

        when:
        def children = vehicle.getChildEntities("DEFAULT")

        then:
        children.size() == 2
        children.any { it.javaClass == Car }
        children.any { it.javaClass == Truck }
    }

    void "test isComponentPropertyNullable"() {
        given:
        GrailsHibernatePersistentEntity owner = getPersistentEntity(AddressOwner) as GrailsHibernatePersistentEntity
        def addressProp = owner.getPropertyByName("address")

        expect:
        owner.isComponentPropertyNullable(addressProp) == false
    }

    void "test getMultiTenantFilterCondition"() {
        given:
        GrailsHibernatePersistentEntity entity = Spy(HibernatePersistentEntity, constructorArgs: [Person, getMappingContext()])
        // Force the stub to implement the required interface for the instanceof check in the default method
        def tenantIdProp = Stub(TenantId, additionalInterfaces: [HibernatePersistentProperty])
        tenantIdProp.getName() >> "tenantId"
        
        entity.getTenantId() >> tenantIdProp
        def fetcher = Stub(DefaultColumnNameFetcher, constructorArgs: [Stub(org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy)])
        fetcher.getDefaultColumnName(_) >> "tenant_id_col"

        when:
        def condition = entity.getMultiTenantFilterCondition(fetcher)

        then:
        condition == ":tenantId = tenant_id_col"
    }

    void "test getSchema and getCatalog"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(CustomTableEntity) as GrailsHibernatePersistentEntity
        def collector = getCollector()

        expect:
        entity.getSchema(collector) == "custom_schema"
        entity.getCatalog(collector) == "custom_catalog"
    }

    void "test configureDerivedProperties"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(DerivedPropertyEntity) as GrailsHibernatePersistentEntity
        def prop = entity.getPropertyByName("fullName")

        when:
        entity.configureDerivedProperties()

        then:
        prop.mappedForm.derived == true
    }

    void "test dataSourceName injection"() {
        when:
        def entities = getMappingContext().getHibernatePersistentEntities("customDS")

        then:
        entities.every { it.dataSourceName == "customDS" }
    }

    void "test buildDiscriminatorSet with dataSourceName"() {
        given:
        def context = getMappingContext()
        GrailsHibernatePersistentEntity vehicle = Spy(HibernatePersistentEntity, constructorArgs: [Vehicle, context])
        GrailsHibernatePersistentEntity car = Spy(HibernatePersistentEntity, constructorArgs: [Car, context])
        GrailsHibernatePersistentEntity truck = Spy(HibernatePersistentEntity, constructorArgs: [Truck, context])

        // Mock discriminator values
        vehicle.getDiscriminatorValue() >> "VEHICLE"
        car.getDiscriminatorValue() >> "CAR"
        truck.getDiscriminatorValue() >> "TRUCK"
        
        // Ensure child Spies don't try to call real buildDiscriminatorSet if it's too complex, 
        // but here we want to test the recursion.
        car.getChildEntities(_) >> []
        truck.getChildEntities(_) >> []

        when: "Testing for DS1"
        vehicle.setDataSourceName("DS1")
        vehicle.getChildEntities("DS1") >> [car]
        Set<String> result1 = vehicle.buildDiscriminatorSet()

        then:
        result1 == ["'VEHICLE'", "'CAR'"] as Set

        when: "Testing for DS2"
        vehicle.setDataSourceName("DS2")
        vehicle.getChildEntities("DS2") >> [truck]
        Set<String> result2 = vehicle.buildDiscriminatorSet()

        then:
        result2 == ["'VEHICLE'", "'TRUCK'"] as Set
    }

    def "test getHibernateCompositeIdentity returns CompositeIdentity when conditions met"() {
        given:
        def context = getMappingContext()
        GrailsHibernatePersistentEntity entity = Spy(HibernatePersistentEntity, constructorArgs: [Person, context])
        def mapping = Mock(Mapping)
        def compositeIdentity = new CompositeIdentity()

        entity.getMappedForm() >> mapping
        mapping.hasCompositeIdentifier() >> true
        mapping.getIdentity() >> compositeIdentity

        when:
        Optional<CompositeIdentity> result = entity.getHibernateCompositeIdentity()

        then:
        result.isPresent()
        result.get() == compositeIdentity
    }

    def "test getHibernateCompositeIdentity returns empty when mapping is null"() {
        given:
        def context = getMappingContext()
        GrailsHibernatePersistentEntity entity = Spy(HibernatePersistentEntity, constructorArgs: [Person, context])

        entity.getMappedForm() >> null

        when:
        Optional<CompositeIdentity> result = entity.getHibernateCompositeIdentity()

        then:
        !result.isPresent()
    }

    def "test getHibernateCompositeIdentity returns empty when mapping has no composite identifier"() {
        given:
        def context = getMappingContext()
        GrailsHibernatePersistentEntity entity = Spy(HibernatePersistentEntity, constructorArgs: [Person, context])
        def mapping = Mock(Mapping)

        entity.getMappedForm() >> mapping
        mapping.hasCompositeIdentifier() >> false

        when:
        Optional<CompositeIdentity> result = entity.getHibernateCompositeIdentity()

        then:
        !result.isPresent()
    }

    def "test getHibernateCompositeIdentity returns empty when mapping.getIdentity is not CompositeIdentity"() {
        given:
        def context = getMappingContext()
        GrailsHibernatePersistentEntity entity = Spy(HibernatePersistentEntity, constructorArgs: [Person, context])
        def mapping = Mock(Mapping)
        def nonCompositeIdentity = new Identity()

        entity.getMappedForm() >> mapping
        mapping.hasCompositeIdentifier() >> true
        mapping.getIdentity() >> nonCompositeIdentity

        when:
        Optional<CompositeIdentity> result = entity.getHibernateCompositeIdentity()

        then:
        !result.isPresent()
    }
}

@Entity
class Person {
    Long id
    String name
}

@Entity
class AddressOwner {
    Long id
    EntityAddress address
    static embedded = ['address']
}

class EntityAddress implements Serializable {
    String city
}

@Entity
class CustomTableEntity {
    Long id
    static mapping = {
        table schema: "custom_schema", catalog: "custom_catalog"
    }
}

@Entity
class CustomTableNameEntity {
    Long id
    static mapping = {
        table "my_custom_table"
    }
}

@Entity
class DerivedPropertyEntity {
    Long id
    String firstName
    String lastName
    String fullName
    static mapping = {
        fullName formula: "CONCAT(first_name, ' ', last_name)"
    }
}


@Entity
class Simple {
    Long id
}

@Entity
class CustomDiscriminator {
    Long id
    static mapping = {
        discriminator "custom_val"
    }
}

@Entity
class NumericDiscriminator {
    Long id
    static mapping = {
        discriminator value: "1", type: "integer"
    }
}

@Entity
class Vehicle {
    Long id
}

@Entity
class Car extends Vehicle {
}

@Entity
class Truck extends Vehicle {
}
