package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
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

        def binder = new ManyToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def association = Mock(HibernateManyToOneProperty)
        def path = "/test"
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = new PropertyConfig()

        association.getHibernateAssociatedEntity() >> refDomainClass
        association.getMappedForm() >> propertyConfig
        mapping.setIdentity(hasCompositeId ? new CompositeIdentity() : null)

        when:
        def result = binder.bindManyToOne(association as HibernateAssociation, null, path)

        then:
        result instanceof ManyToOne
        1 * manyToOneValuesBinder.bindManyToOneValues(association as HibernateAssociation, _ as ManyToOne)
        compositeBinderCalls * compositeBinder.bindCompositeIdentifierToManyToOne(association as HibernatePersistentProperty, _ as ManyToOne, _, refDomainClass, path)
        simpleValueBinderCalls * simpleValueBinder.bindSimpleValue(association as HibernatePersistentProperty, null, _ as ManyToOne, path)

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

        def binder = new ManyToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def property = Mock(HibernateManyToManyProperty)
        def mapping = new Mapping()
        mapping.setColumns(new HashMap<String, PropertyConfig>())
        def ownerEntity = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = new PropertyConfig()

        property.isCircular() >> true
        property.getOwner() >> ownerEntity
        property.getHibernateOwner() >> ownerEntity
        property.getName() >> "myCircularProp"
        property.getMappedForm() >> propertyConfig
        namingStrategy.resolveColumnName("myCircularProp") >> "my_circular_prop"

        when:
        def result = binder.bindManyToOne(property as HibernateAssociation, null, "/test")

        then:
        result instanceof ManyToOne
        1 * manyToOneValuesBinder.bindManyToOneValues(property as HibernateAssociation, _ as ManyToOne)
        1 * simpleValueBinder.bindSimpleValue(property as HibernatePersistentProperty, null, _ as ManyToOne, "/test")
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

        def binder = new ManyToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def property = Mock(HibernateOneToOneProperty)
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = Mock(PropertyConfig)
        def column = new Column('test')
        def inverseSide = Mock(HibernateToOneProperty)

        property.getHibernateAssociatedEntity() >> refDomainClass
        mapping.setIdentity(null)
        property.getMappedForm() >> propertyConfig
        columnFetcher.getColumnForSimpleValue(_ as ManyToOne) >> column

        propertyConfig.isUnique() >> isUnique
        propertyConfig.isUniqueWithinGroup() >> isUniqueWithinGroup
        property.isBidirectional() >> isBidirectional
        property.getHibernateInverseSide() >> inverseSide
        inverseSide.isHibernateOneToOne() >> isInverseHasOne

        when:
        def result = binder.bindManyToOne(property as HibernateAssociation, null, "/test")

        then:
        result.isAlternateUniqueKey()
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

        def binder = new ManyToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), namingStrategy, simpleValueBinder, manyToOneValuesBinder, compositeBinder, columnFetcher)

        def property = Mock(HibernateOneToOneProperty)
        def mapping = new Mapping()
        def refDomainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
        }
        def propertyConfig = new PropertyConfig()

        property.getHibernateAssociatedEntity() >> refDomainClass
        mapping.setIdentity(null)
        property.getMappedForm() >> propertyConfig
        columnFetcher.getColumnForSimpleValue(_ as ManyToOne) >> null

        when:
        binder.bindManyToOne(property as HibernateAssociation, null, "/test")

        then:
        thrown(MappingException)
    }
}
