package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.orm.hibernate.cfg.domainbinding.ConfigureDerivedPropertiesConsumer
import org.grails.orm.hibernate.cfg.domainbinding.DefaultColumnNameFetcher
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory
import org.hibernate.boot.spi.InFlightMetadataCollector
import spock.lang.Specification
import org.grails.datastore.mapping.model.MappingContext

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
                DerivedPropertyEntity
        ])
    }

    void "test buildDiscriminatorSet for simple entity"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(Simple) as GrailsHibernatePersistentEntity

        expect:
        entity.buildDiscriminatorSet() == ["'org.grails.orm.hibernate.cfg.Simple'"] as Set
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
        vehicle.buildDiscriminatorSet() == ["'VEHICLE'", "'CAR'", "'TRUCK'"] as Set
    }

    void "test getHibernateRootEntity and getRootMapping"() {
        given:
        GrailsHibernatePersistentEntity car = getPersistentEntity(Car) as GrailsHibernatePersistentEntity

        expect:
        car.getHibernateRootEntity().javaClass == Vehicle
        car.getRootMapping().discriminator.value == "VEHICLE"
    }

    void "test isTablePerHierarchySubclass"() {
        given:
        GrailsHibernatePersistentEntity vehicle = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity car = getPersistentEntity(Car) as GrailsHibernatePersistentEntity

        expect:
        !vehicle.isTablePerHierarchySubclass()
        car.isTablePerHierarchySubclass()
    }

    void "test getDiscriminatorValue"() {
        given:
        GrailsHibernatePersistentEntity vehicle = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity car = getPersistentEntity(Car) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity simple = getPersistentEntity(Simple) as GrailsHibernatePersistentEntity

        expect:
        vehicle.getDiscriminatorValue() == "VEHICLE"
        car.getDiscriminatorValue() == "CAR"
        simple.getDiscriminatorValue() == "org.grails.orm.hibernate.cfg.Simple"
    }

    void "test getPersistentPropertiesToBind"() {
        given:
        GrailsHibernatePersistentEntity person = getPersistentEntity(Person) as GrailsHibernatePersistentEntity

        when:
        def props = person.getPersistentPropertiesToBind()

        then:
        props.size() == 1
        props[0].name == "name"
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
        def tenantIdProp = Stub(TenantId)
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
    static mapping = {
        discriminator "VEHICLE"
    }
}

@Entity
class Car extends Vehicle {
    static mapping = {
        discriminator "CAR"
    }
}

@Entity
class Truck extends Vehicle {
    static mapping = {
        discriminator "TRUCK"
    }
}
