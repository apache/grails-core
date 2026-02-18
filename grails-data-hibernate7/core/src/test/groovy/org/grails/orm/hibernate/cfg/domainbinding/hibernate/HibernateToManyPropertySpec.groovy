package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.Identity // Added this import
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.Property
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic

import java.util.Optional
import java.util.Map

class HibernateToManyPropertySpec extends HibernateGormDatastoreSpec {

    protected GrailsHibernatePersistentProperty createTestHibernateToManyProperty(Class<?> domainClass = TestEntityWithMany, String propertyName = "items") {
        PersistentEntity entity = createPersistentEntity(domainClass)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName(propertyName)
        return property
    }




    def "test getCompositeIdentity returns CompositeIdentity when conditions met"() {
        given:
        def mapping = Mock(Mapping)
        // Use the helper to create a concrete instance
        def property = createTestHibernateToManyProperty(TestEntityWithMany) // Use TestEntityWithMany for a basic association
        def spiedProperty = Spy(property)
        spiedProperty.supportsJoinColumnMapping() >> true // Explicitly set for this test scenario
        
        // Use a real CompositeIdentity instead of a mock
        def compositeIdentity = new CompositeIdentity()
        mapping.hasCompositeIdentifier() >> true
        mapping.getIdentity() >> compositeIdentity

        when:
        Optional<CompositeIdentity> result = spiedProperty.getCompositeIdentity(mapping)

        then:
        result.isPresent()
        result.get() == compositeIdentity
    }

    def "test getCompositeIdentity returns empty when mapping is null"() {
        given:
        def property = createTestHibernateToManyProperty(TestEntityWithMany) // Use a real entity
        def spiedProperty = Spy(property)
        spiedProperty.supportsJoinColumnMapping() >> true // Explicitly set for this test scenario
        
        when:
        Optional<CompositeIdentity> result = spiedProperty.getCompositeIdentity(null)

        then:
        !result.isPresent()
    }

    def "test getCompositeIdentity returns empty when mapping has no composite identifier"() {
        given:
        def mapping = Mock(Mapping)
        def property = createTestHibernateToManyProperty(TestEntityWithMany) // Use a real entity
        def spiedProperty = Spy(property)
        spiedProperty.supportsJoinColumnMapping() >> true // Explicitly set for this test scenario
        
        mapping.hasCompositeIdentifier() >> false

        when:
        Optional<CompositeIdentity> result = spiedProperty.getCompositeIdentity(mapping)

        then:
        !result.isPresent()
    }

    def "test getCompositeIdentity returns empty when property does not support join column mapping"() {
        given:
        def mapping = Mock(Mapping)
        def property = createTestHibernateToManyProperty(TestEntityWithMany) // Use a real entity
        def spiedProperty = Spy(property)
        spiedProperty.supportsJoinColumnMapping() >> false // Explicitly set to false for this test scenario
        
        mapping.hasCompositeIdentifier() >> true

        when:
        Optional<CompositeIdentity> result = spiedProperty.getCompositeIdentity(mapping)

        then:
        !result.isPresent()
    }

    def "test getCompositeIdentity returns empty when mapping.getIdentity is not CompositeIdentity"() {
        given:
        def mapping = Mock(Mapping)
        def property = createTestHibernateToManyProperty(TestEntityWithMany) // Use a real entity
        def spiedProperty = Spy(property)
        spiedProperty.supportsJoinColumnMapping() >> true // Explicitly set to true for this test scenario
        
        // Use a real Identity instead of a mock or a plain Object
        def nonCompositeIdentity = new Identity()
        
        mapping.hasCompositeIdentifier() >> true
        mapping.getIdentity() >> nonCompositeIdentity

        when:
        Optional<CompositeIdentity> result = spiedProperty.getCompositeIdentity(mapping)

        then:
        !result.isPresent()
    }
}

@Entity
class TestEntityWithMany {
    Long id
    String name
    static hasMany = [items: AssociatedItem]
}

@Entity
class AssociatedItem {
    Long id
    String value
    TestEntityWithMany parent // Bidirectional for association property testing
    static belongsTo = [parent: TestEntityWithMany]
}
