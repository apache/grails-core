package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.Identity
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
import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder
import org.hibernate.mapping.Bag
import org.hibernate.mapping.RootClass
import org.grails.datastore.mapping.model.DatastoreConfigurationException

import java.util.Optional
import java.util.Map

class HibernateToManyPropertySpec extends HibernateGormDatastoreSpec {

    protected GrailsHibernatePersistentProperty createTestHibernateToManyProperty(Class<?> domainClass = TestEntityWithMany, String propertyName = "items") {
        PersistentEntity entity = createPersistentEntity(domainClass)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName(propertyName)
        return property
    }

    def "test bindOrderBy with sort configured"() {
        given:
        def property = createTestHibernateToManyProperty(TestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        collection.setRole("TestEntityWithMany.items")
        
        def persistentClasses = [:]
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(AssociatedItem.name)
        persistentClasses[AssociatedItem.name] = associatedPersistentClass
        def orderByClauseBuilder = Mock(OrderByClauseBuilder)
        
        property.getMappedForm().setSort("value")
        property.getMappedForm().setOrder("desc")

        when:
        def result = property.bindOrderBy(collection, persistentClasses, orderByClauseBuilder)

        then:
        1 * orderByClauseBuilder.buildOrderByClause("value", associatedPersistentClass, "TestEntityWithMany.items", "desc") >> "order by value desc"
        collection.getOrderBy() == "order by value desc"
        result == associatedPersistentClass
    }

    def "test bindOrderBy with unidirectional one-to-many throws exception"() {
        given:
        def property = createTestHibernateToManyProperty(UnidirectionalEntity, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def persistentClasses = [:]
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(AssociatedItem.name)
        persistentClasses[AssociatedItem.name] = associatedPersistentClass
        def orderByClauseBuilder = Mock(OrderByClauseBuilder)
        
        property.getMappedForm().setSort("value")

        when:
        property.bindOrderBy(collection, persistentClasses, orderByClauseBuilder)

        then:
        thrown(DatastoreConfigurationException)
    }

    def "test bindOrderBy returns associatedClass even without sort"() {
        given:
        def property = createTestHibernateToManyProperty(TestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def persistentClasses = [:]
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(AssociatedItem.name)
        persistentClasses[AssociatedItem.name] = associatedPersistentClass
        def orderByClauseBuilder = Mock(OrderByClauseBuilder)

        when:
        def result = property.bindOrderBy(collection, persistentClasses, orderByClauseBuilder)

        then:
        collection.getOrderBy() == null
        result == associatedPersistentClass
    }

    def "test bindOrderBy throws MappingException when class is unmapped"() {
        given:
        def property = createTestHibernateToManyProperty(TestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def persistentClasses = [:] // Empty map, so AssociatedItem will be missing
        def orderByClauseBuilder = Mock(OrderByClauseBuilder)

        when:
        property.bindOrderBy(collection, persistentClasses, orderByClauseBuilder)

        then:
        thrown(org.hibernate.MappingException)
    }

    def "test bindOrderBy with table per hierarchy subclass"() {
        given:
        def property = createTestHibernateToManyProperty(TestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def persistentClasses = [:]
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(AssociatedItem.name)
        persistentClasses[AssociatedItem.name] = associatedPersistentClass
        def orderByClauseBuilder = Mock(OrderByClauseBuilder)

        // Mock GrailsHibernatePersistentEntity behavior for table per hierarchy
        def referencedEntity = property.getHibernateAssociatedEntity()
        def spiedReferencedEntity = Spy(referencedEntity)
        spiedReferencedEntity.isTablePerHierarchySubclass() >> true
        spiedReferencedEntity.getDiscriminatorColumnName() >> "item_type"
        spiedReferencedEntity.buildDiscriminatorSet() >> (["'A'", "'B'"] as Set)

        // Inject the spy if possible, or mock the getter on property
        def spiedProperty = Spy(property)
        spiedProperty.getHibernateAssociatedEntity() >> spiedReferencedEntity

        when:
        spiedProperty.bindOrderBy(collection, persistentClasses, orderByClauseBuilder)

        then:
        collection.getWhere() == "item_type in ('A','B')"
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

@Entity
class UnidirectionalEntity {
    Long id
    Set<AssociatedItem> items
    static hasMany = [items: AssociatedItem]
}
