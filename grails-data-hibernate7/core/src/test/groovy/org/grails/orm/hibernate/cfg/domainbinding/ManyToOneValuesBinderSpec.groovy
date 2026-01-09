package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.FetchMode
import org.hibernate.mapping.ManyToOne
import spock.lang.Unroll

class ManyToOneValuesBinderSpec extends HibernateGormDatastoreSpec {

    @Unroll
    def "Test bindManyToOneValues with #scenario"() {
        given:
        // 1. Mock the dependency and use the protected constructor
        def propertyConfigConverter = Mock(PersistentPropertyToPropertyConfig)
        def binder = new ManyToOneValuesBinder(propertyConfigConverter)

        // 2. Set up mocks for the method arguments
        def association = Mock(Association)
        def manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(),null)
        def associatedEntity = Mock(PersistentEntity)

        // 3. Create the config object that the converter will return
        def config = new PropertyConfig()
        if (testFetchMode != null) {
            config.setFetch(testFetchMode)
        }
        config.setLazy(testLazy)
        config.setIgnoreNotFound(testIgnoreNotFound)

        // 4. Define mock behaviors
        propertyConfigConverter.toPropertyConfig(association) >> config
        association.getAssociatedEntity() >> associatedEntity
        associatedEntity.getName() >> "AssociatedEntityName"

        when:
        binder.bindManyToOneValues(association, manyToOne)

        then:
        // 5. Verify that the correct values were set on the ManyToOne object
        1 * manyToOne.setFetchMode(expectedFetchMode)
        1 * manyToOne.setLazy(expectedLazy)
        1 * manyToOne.setIgnoreNotFound(testIgnoreNotFound)
        1 * manyToOne.setReferencedEntityName("AssociatedEntityName")

        where:
        scenario                | testFetchMode    | testLazy | testIgnoreNotFound | expectedFetchMode | expectedLazy
        "explicit values"       | FetchMode.JOIN   | true     | true               | FetchMode.JOIN    | true
        "default values"        | null             | null     | false              | FetchMode.DEFAULT | true
        "other explicit values" | FetchMode.SELECT | false    | false              | FetchMode.SELECT  | false
    }
}
