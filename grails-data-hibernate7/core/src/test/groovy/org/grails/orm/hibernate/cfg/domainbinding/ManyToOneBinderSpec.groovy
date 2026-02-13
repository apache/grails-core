package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.types.Association
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.hibernate.MappingException
import org.hibernate.mapping.Column
import org.hibernate.mapping.ManyToOne
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher

class ManyToOneBinderSpec extends HibernateGormDatastoreSpec {

    @Unroll
    def "Test bindManyToOne orchestration for #scenario"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)

        def binder = new ManyToOneBinder(namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def association = Mock(HibernateManyToOneProperty)
        def manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def path = "/test"
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = new PropertyConfig()

        association.getAssociatedEntity() >> refDomainClass
        association.getMappedForm() >> propertyConfig
        mapping.setIdentity(hasCompositeId ? new CompositeIdentity() : null)

        when:
        binder.bindManyToOne(association as Association, manyToOne, path)

        then:
        1 * manyToOneValuesBinder.bindManyToOneValues(association as Association, manyToOne)
        compositeBinderCalls * compositeBinder.bindCompositeIdentifierToManyToOne(association as GrailsHibernatePersistentProperty, manyToOne, _, refDomainClass, path)
        simpleValueBinderCalls * simpleValueBinder.bindSimpleValue(association as GrailsHibernatePersistentProperty, null, manyToOne, path)

        where:
        scenario                 | hasCompositeId | compositeBinderCalls | simpleValueBinderCalls
        "a composite identifier" | true           | 1                    | 0
        "a simple identifier"    | false          | 0                    | 1
    }

    def "Test circular many-to-many binding"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)

        def binder = new ManyToOneBinder(namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def property = Mock(HibernateManyToManyProperty)
        def manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def mapping = new Mapping()
        mapping.setColumns(new HashMap<String, PropertyConfig>())
        def ownerEntity = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = new PropertyConfig()

        property.isCircular() >> true
        property.getOwner() >> ownerEntity
        property.getName() >> "myCircularProp"
        property.getMappedForm() >> propertyConfig
        namingStrategy.resolveColumnName("myCircularProp") >> "my_circular_prop"

        when:
        binder.bindManyToOne(property as Association, manyToOne, "/test")

        then:
        1 * manyToOneValuesBinder.bindManyToOneValues(property as Association, manyToOne)
        1 * simpleValueBinder.bindSimpleValue(property as GrailsHibernatePersistentProperty, null, manyToOne, "/test")
        def resultConfig = mapping.getColumns().get("myCircularProp")
        resultConfig != null
        resultConfig.getJoinTable().getKey().getName() == "my_circular_prop_id"
    }

    @Unroll
    def "Test one-to-one binding with uniqueWithinGroup constraint for #scenario"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)

        def binder = new ManyToOneBinder(namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def property = Mock(HibernateOneToOneProperty)
        def manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column('test')
        def inverseSide = Mock(Association)

        property.getAssociatedEntity() >> refDomainClass
        mapping.setIdentity(null)
        property.getMappedForm() >> propertyConfig
        columnFetcher.getColumnForSimpleValue(manyToOne) >> column

        propertyConfig.isUnique() >> isUnique
        propertyConfig.isUniqueWithinGroup() >> isUniqueWithinGroup
        property.isBidirectional() >> isBidirectional
        property.getInverseSide() >> inverseSide
        inverseSide.isHasOne() >> isInverseHasOne

        when:
        binder.bindManyToOne(property as Association, manyToOne, "/test")

        then:
        manyToOne.isAlternateUniqueKey()
        if (expectedUniqueValue != null) {
            assert column.isUnique() == expectedUniqueValue
        } else {
            assert !column.isUnique()
        }

        where:
        scenario                               | isUnique | isUniqueWithinGroup | isBidirectional | isInverseHasOne | expectedUniqueValue
        "simple unique=true"                   | true     | false               | false           | false           | true
        "simple unique=false"                  | false    | false               | false           | false           | false
        "uniqueWithinGroup and bidirectional"  | false    | true                | true            | true            | true
        "uniqueWithinGroup and unidirectional" | false    | true                | false           | false           | null
        "uniqueWithinGroup and not hasOne"     | false    | true                | true            | false           | null
    }

    def "Test one-to-one binding throws exception when column is not found"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)

        def binder = new ManyToOneBinder(namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def property = Mock(HibernateOneToOneProperty)
        def manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = new PropertyConfig()

        property.getAssociatedEntity() >> refDomainClass
        mapping.setIdentity(null)
        property.getMappedForm() >> propertyConfig
        columnFetcher.getColumnForSimpleValue(manyToOne) >> null

        when:
        binder.bindManyToOne(property as Association, manyToOne, "/test")

        then:
        thrown(MappingException)
    }
}
