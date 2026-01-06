package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToOne
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.JoinTable
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.MappingException
import org.hibernate.mapping.Column
import org.hibernate.mapping.ManyToOne
import spock.lang.Specification
import spock.lang.Unroll

class ManyToOneBinderSpec extends Specification {

    @Unroll
    def "Test bindManyToOne orchestration for #scenario"() {
        given:
        // 1. Create mocks for all dependencies
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def propertyConfigConverter = Mock(PersistentPropertyToPropertyConfig)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)
        def entityWrapper = Mock(HibernateEntityWrapper)

        // 2. Instantiate the binder using the protected constructor
        def binder = new ManyToOneBinder(namingStrategy, simpleValueBinder, propertyConfigConverter, manyToOneValuesBinder, compositeBinder, columnFetcher, entityWrapper)

        // 3. Set up mocks for method arguments
        def association = Mock(Association)
        def manyToOne = Mock(ManyToOne)
        def path = "/test"
        def refDomainClass = Mock(PersistentEntity)
        def mapping = Mock(Mapping)
        def propertyConfig = Mock(PropertyConfig)

        // 4. Define mock behaviors
        association.getAssociatedEntity() >> refDomainClass
        entityWrapper.getMappedForm(refDomainClass) >> mapping
        propertyConfigConverter.toPropertyConfig(association) >> propertyConfig
        mapping.hasCompositeIdentifier() >> hasCompositeId

        if (hasCompositeId) {
            def compositeId = Mock(CompositeIdentity)
            mapping.getIdentity() >> compositeId
        }

        when:
        binder.bindManyToOne(association, manyToOne, path)

        then:
        // 5. Verify the orchestration logic
        1 * manyToOneValuesBinder.bindManyToOneValues(association, manyToOne)
        compositeBinderCalls * compositeBinder.bindCompositeIdentifierToManyToOne(association, manyToOne, _, refDomainClass, path)
        simpleValueBinderCalls * simpleValueBinder.bindSimpleValue(association, null, manyToOne, path)

        where:
        scenario                 | hasCompositeId | compositeBinderCalls | simpleValueBinderCalls
        "a composite identifier" | true           | 1                    | 0
        "a simple identifier"    | false          | 0                    | 1
    }

    def "Test circular many-to-many binding"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def propertyConfigConverter = Mock(PersistentPropertyToPropertyConfig)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)
        def entityWrapper = Mock(HibernateEntityWrapper)

        def binder = new ManyToOneBinder(namingStrategy, simpleValueBinder, propertyConfigConverter, manyToOneValuesBinder, compositeBinder, columnFetcher, entityWrapper)

        def property = Mock(ManyToMany)
        def manyToOne = Mock(ManyToOne)
        def ownerEntity = Mock(PersistentEntity)
        def mapping = new Mapping()
        mapping.setColumns(new HashMap<String, PropertyConfig>())
        def propertyConfig = new PropertyConfig()

        property.isCircular() >> true
        property.getOwner() >> ownerEntity
        property.getName() >> "myCircularProp"
        entityWrapper.getMappedForm(ownerEntity) >> mapping
        propertyConfigConverter.toPropertyConfig(property) >> propertyConfig
        namingStrategy.resolveColumnName("myCircularProp") >> "my_circular_prop"

        when:
        binder.bindManyToOne(property, manyToOne, "/test")

        then:
        1 * manyToOneValuesBinder.bindManyToOneValues(property, manyToOne)
        1 * simpleValueBinder.bindSimpleValue(property, null, manyToOne, "/test")
        def resultConfig = mapping.getColumns().get("myCircularProp")
        resultConfig != null
        resultConfig.getJoinTable().getKey().getName() == "my_circular_prop_id"
    }

    @Unroll
    def "Test one-to-one binding with uniqueWithinGroup constraint for #scenario"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def propertyConfigConverter = Mock(PersistentPropertyToPropertyConfig)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)
        def entityWrapper = Mock(HibernateEntityWrapper)

        def binder = new ManyToOneBinder(namingStrategy, simpleValueBinder, propertyConfigConverter, manyToOneValuesBinder, compositeBinder, columnFetcher, entityWrapper)

        def property = Mock(OneToOne)
        def manyToOne = Mock(ManyToOne)
        def refDomainClass = Mock(PersistentEntity)
        def mapping = Mock(Mapping)
        def propertyConfig = Mock(PropertyConfig)
        def column = Mock(Column, name: 'test')
        def inverseSide = Mock(Association)

        property.getAssociatedEntity() >> refDomainClass
        entityWrapper.getMappedForm(refDomainClass) >> mapping
        mapping.hasCompositeIdentifier() >> false
        propertyConfigConverter.toPropertyConfig(property) >> propertyConfig
        columnFetcher.getColumnForSimpleValue(manyToOne) >> column

        // Configure mocks based on scenario
        propertyConfig.isUnique() >> isUnique
        propertyConfig.isUniqueWithinGroup() >> isUniqueWithinGroup
        property.isBidirectional() >> isBidirectional
        property.getInverseSide() >> inverseSide
        inverseSide.isHasOne() >> isInverseHasOne

        when:
        binder.bindManyToOne(property, manyToOne, "/test")

        then:
        1 * manyToOne.setAlternateUniqueKey(true)
        if (expectedUniqueValue != null) {
            1 * column.setUnique(expectedUniqueValue)
        } else {
            0 * column.setUnique(_)
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
        def propertyConfigConverter = Mock(PersistentPropertyToPropertyConfig)
        def manyToOneValuesBinder = Mock(ManyToOneValuesBinder)
        def compositeBinder = Mock(CompositeIdentifierToManyToOneBinder)
        def columnFetcher = Mock(SimpleValueColumnFetcher)
        def entityWrapper = Mock(HibernateEntityWrapper)

        def binder = new ManyToOneBinder(namingStrategy, simpleValueBinder, propertyConfigConverter, manyToOneValuesBinder, compositeBinder, columnFetcher, entityWrapper)

        def property = Mock(OneToOne)
        def manyToOne = Mock(ManyToOne)
        def refDomainClass = Mock(PersistentEntity)
        def mapping = Mock(Mapping)
        def propertyConfig = new PropertyConfig()

        property.getAssociatedEntity() >> refDomainClass
        entityWrapper.getMappedForm(refDomainClass) >> mapping
        mapping.hasCompositeIdentifier() >> false
        propertyConfigConverter.toPropertyConfig(property) >> propertyConfig
        columnFetcher.getColumnForSimpleValue(manyToOne) >> null // No column found

        when:
        binder.bindManyToOne(property, manyToOne, "/test")

        then:
        thrown(MappingException)
    }
}
