package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec

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

        when:
        Set<String> result = entity.buildDiscriminatorSet()

        then:
        result == ["'org.grails.orm.hibernate.cfg.Simple'"] as Set
    }

    void "test buildDiscriminatorSet with custom discriminator value"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(CustomDiscriminator) as GrailsHibernatePersistentEntity

        when:
        Set<String> result = entity.buildDiscriminatorSet()

        then:
        result == ["'custom_val'"] as Set
    }

    void "test buildDiscriminatorSet with numeric discriminator type"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(NumericDiscriminator) as GrailsHibernatePersistentEntity

        when:
        Set<String> result = entity.buildDiscriminatorSet()

        then:
        result == ["1"] as Set
    }

    void "test buildDiscriminatorSet with hierarchy"() {
        given:
        GrailsHibernatePersistentEntity entity = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity

        when:
        Set<String> result = entity.buildDiscriminatorSet()

        then:
        result == ["'VEHICLE'", "'CAR'", "'TRUCK'"] as Set
    }

    void "test getHibernateRootEntity and getRootMapping"() {
        given:
        GrailsHibernatePersistentEntity vehicle = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity car = getPersistentEntity(Car) as GrailsHibernatePersistentEntity

        expect:
        car.hibernateRootEntity == vehicle
        car.rootMapping == vehicle.mappedForm
        vehicle.hibernateRootEntity == vehicle
        vehicle.rootMapping == vehicle.mappedForm
    }

    void "test isTablePerHierarchySubclass"() {
        given:
        GrailsHibernatePersistentEntity vehicle = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity car = getPersistentEntity(Car) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity simple = getPersistentEntity(Simple) as GrailsHibernatePersistentEntity

        expect:
        !vehicle.isTablePerHierarchySubclass()
        car.isTablePerHierarchySubclass()
        !simple.isTablePerHierarchySubclass()
    }

    void "test getDiscriminatorValue"() {
        given:
        GrailsHibernatePersistentEntity vehicle = getPersistentEntity(Vehicle) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity car = getPersistentEntity(Car) as GrailsHibernatePersistentEntity
        GrailsHibernatePersistentEntity simple = getPersistentEntity(Simple) as GrailsHibernatePersistentEntity

        expect:
        vehicle.discriminatorValue == "VEHICLE"
        car.discriminatorValue == "CAR"
        simple.discriminatorValue == "org.grails.orm.hibernate.cfg.Simple"
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
        children.any { it.name == Car.name }
        children.any { it.name == Truck.name }
    }

    void "test isComponentPropertyNullable"() {
        given:
        GrailsHibernatePersistentEntity addressOwner = getPersistentEntity(AddressOwner) as GrailsHibernatePersistentEntity
        def addressProp = addressOwner.getPropertyByName("address")

        expect:
        addressOwner.isComponentPropertyNullable(addressProp) == false
    }

    void "test getMultiTenantFilterCondition"() {
        given:
        GrailsHibernatePersistentEntity entity = Spy(getPersistentEntity(Person)) as GrailsHibernatePersistentEntity
        def tenantIdProp = Mock(org.grails.datastore.mapping.model.types.TenantId)
        entity.getTenantId() >> tenantIdProp
        def fetcher = Mock(org.grails.orm.hibernate.cfg.domainbinding.DefaultColumnNameFetcher)
        fetcher.getDefaultColumnName(tenantIdProp) >> "tenant_id"

        expect:
        entity.getMultiTenantFilterCondition(fetcher) == ":tenantId = tenant_id"
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
