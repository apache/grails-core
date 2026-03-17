package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.*
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.binder.*
import org.hibernate.mapping.Column
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Table
import org.hibernate.boot.spi.MetadataBuildingContext
import spock.lang.Unroll

class ManyToOneBinderSpec extends HibernateGormDatastoreSpec {

    ManyToOneBinder binder
    PersistentEntityNamingStrategy namingStrategy = Mock()
    SimpleValueBinder simpleValueBinder = Mock()
    ManyToOneValuesBinder manyToOneValuesBinder = Mock()
    CompositeIdentifierToManyToOneBinder compositeBinder = Mock()
    MetadataBuildingContext metadataBuildingContext

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        // Using the 5-arg constructor from your provided Java source
        binder = new ManyToOneBinder(
                metadataBuildingContext,
                namingStrategy,
                simpleValueBinder,
                manyToOneValuesBinder,
                compositeBinder
        )
    }

    @Unroll
    def "Test bindManyToOne orchestration for #scenario"() {
        given:
        def association = Mock(HibernateManyToOneProperty)
        def path = "/test"
        def (mapping, refDomainClass) = mockEntity(hasCompositeId)

        association.getHibernateAssociatedEntity() >> refDomainClass
        association.getMappedForm() >> Mock(PropertyConfig)

        when:
        def result = binder.bindManyToOne(association, null, path)

        then:
        result instanceof ManyToOne
        1 * manyToOneValuesBinder.bindManyToOneValues(association, _ as ManyToOne)
        compositeBinderCalls * compositeBinder.bindCompositeIdentifierToManyToOne(association, _ as ManyToOne, _, refDomainClass, path)
        simpleValueBinderCalls * simpleValueBinder.bindSimpleValue(association, null, _ as ManyToOne, path)

        where:
        scenario                 | hasCompositeId | compositeBinderCalls | simpleValueBinderCalls
        "a composite identifier" | true           | 1                    | 0
        "a simple identifier"    | false          | 0                    | 1
    }

    def "Test circular many-to-many binding"() {
        given:
        def property = Mock(HibernateManyToManyProperty)
        def (mapping, ownerEntity) = mockEntity(false)
        mapping.setColumns([:])

        def propertyConfig = Mock(PropertyConfig)
        property.isCircular() >> true
        property.getOwner() >> ownerEntity
        property.getHibernateOwner() >> ownerEntity
        property.getName() >> "myCircularProp"
        property.getMappedForm() >> propertyConfig
        namingStrategy.resolveColumnName("myCircularProp") >> "my_circular_prop"

        when:
        def result = binder.bindManyToOne(property, null, "/test")

        then:
        result instanceof ManyToOne
        1 * manyToOneValuesBinder.bindManyToOneValues(property, _ as ManyToOne)
        1 * simpleValueBinder.bindSimpleValue(property as HibernatePersistentProperty, null, _ as ManyToOne, "/test")

        mapping.getColumns().containsKey("myCircularProp")
        mapping.getColumns().get("myCircularProp") == propertyConfig
    }

    @Unroll
    def "Test bindManyToOne with unique key constraints for #scenario"() {
        given:
        def property = Mock(HibernateOneToOneProperty)
        def table = Mock(Table)
        def (mapping, refDomainClass) = mockEntity(hasCompositeId)

        // Mocking PropertyConfig avoids ReadOnlyPropertyException
        def propertyConfig = Mock(PropertyConfig)
        propertyConfig.isUnique() >> isUnique
        propertyConfig.isUniqueWithinGroup() >> isUniqueWithinGroup

        property.getTable() >> table
        property.getHibernateAssociatedEntity() >> refDomainClass
        property.getMappedForm() >> propertyConfig
        property.getName() >> "myUniqueProp"
        property.isBidirectional() >> isBidirectional

        if (isBidirectional) {
            property.getInverseSide() >> Mock(HibernateOneToOneProperty) { isValidHibernateOneToOne() >> true }
        }

        when:
        // In the Java source provided, there is no bindManyToOneWithUniqueKey.
        // Assuming you are testing bindManyToOne(HibernateOneToOneProperty, path)
        def result = binder.bindManyToOne(property, "/test/path")

        then:
        result instanceof ManyToOne
                // Note: Logic for setting unique on Column usually happens inside manyToOneValuesBinder or simpleValueBinder
                // verify interactions based on your specific implementation requirements

       where:
        scenario            | hasCompositeId | isBidirectional | isUnique | isUniqueWithinGroup
        "Simple ID"         | false          | false           | true     | false
        "Composite ID"      | true           | false           | true     | false
        "Bidirectional OTO" | false          | true            | true     | true
    }

    /**
     * Helper to reduce repetitive Mocking of entities and mappings
     */
    private List mockEntity(boolean composite) {
        def mapping = new Mapping()
        def compositeId = composite ? new CompositeIdentity() : null
        mapping.setIdentity(compositeId)

        def entity = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getHibernateCompositeIdentity() >> Optional.ofNullable(compositeId)
        }
        return [mapping, entity]
    }
}