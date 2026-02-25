package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

import org.hibernate.mapping.Bag
import org.hibernate.mapping.RootClass

import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover

class CollectionSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    CollectionSecondPassBinder binder

    void setup() {
        def gdb = getGrailsDomainBinder()
        def mbc = gdb.getMetadataBuildingContext()
        def ns = gdb.getNamingStrategy()
        def je = gdb.getJdbcEnvironment()
        def svb = new SimpleValueBinder(mbc, ns, je)
        def svcf = new SimpleValueColumnFetcher()
        def citmto = new CompositeIdentifierToManyToOneBinder(mbc, ns, je)
        def mtob = new ManyToOneBinder(mbc, ns, svb, new ManyToOneValuesBinder(), citmto, svcf)
        def pkvc = new PrimaryKeyValueCreator(mbc)
        def cku = new CollectionKeyColumnUpdater()
        def botml = new BidirectionalOneToManyLinker(new org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver())
        def dkvb = new DependentKeyValueBinder(svb, citmto)
        def cwjtb = new CollectionWithJoinTableBinder(mbc, ns, new UnidirectionalOneToManyInverseValuesBinder(), null, citmto, svcf, new CollectionForPropertyConfigBinder(), new SimpleValueColumnBinder(), null)
        def uotmb = new UnidirectionalOneToManyBinder(cwjtb)
        def cfpcb = new CollectionForPropertyConfigBinder()
        def dcnf = new DefaultColumnNameFetcher(ns, new BackticksRemover())
        def svcb = new SimpleValueColumnBinder()

        binder = new CollectionSecondPassBinder(mtob, pkvc, cku, botml, dkvb, uotmb, cwjtb, cfpcb, dcnf, svcb)
    }

    protected HibernatePersistentProperty createTestHibernateToManyProperty(Class<?> domainClass = CSPBTestEntityWithMany, String propertyName = "items") {
        PersistentEntity entity = createPersistentEntity(domainClass)
        HibernatePersistentProperty property = (HibernatePersistentProperty) entity.getPropertyByName(propertyName)
        return property
    }

    def "test bindOrderBy with sort configured"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        collection.setRole("CSPBTestEntityWithMany.items")
        
        def persistentClasses = [:]
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)
        def valueProperty = new org.hibernate.mapping.Property()
        valueProperty.setName("value")
        def simpleValue = new org.hibernate.mapping.BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), new org.hibernate.mapping.Table("ASSOCIATED_ITEM"))
        simpleValue.setTypeName("string")
        def column = new org.hibernate.mapping.Column("VALUE")
        simpleValue.addColumn(column)
        valueProperty.setValue(simpleValue)
        associatedPersistentClass.addProperty(valueProperty)
        persistentClasses[CSPBAssociatedItem.name] = associatedPersistentClass
        
        property.getMappedForm().setSort("value")
        property.getMappedForm().setOrder("desc")

        when:
        def result = binder.bindOrderBy(property, collection, persistentClasses)

        then:
        collection.getOrderBy() != null
        result == associatedPersistentClass
    }

    def "test bindOrderBy with unidirectional one-to-many throws exception"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBUnidirectionalEntity, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def persistentClasses = [:]
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)
        persistentClasses[CSPBAssociatedItem.name] = associatedPersistentClass
        
        property.getMappedForm().setSort("value")

        when:
        binder.bindOrderBy(property, collection, persistentClasses)

        then:
        thrown(DatastoreConfigurationException)
    }

    def "test bindOrderBy returns associatedClass even without sort"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def persistentClasses = [:]
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)
        persistentClasses[CSPBAssociatedItem.name] = associatedPersistentClass

        when:
        def result = binder.bindOrderBy(property, collection, persistentClasses)

        then:
        collection.getOrderBy() == null
        result == associatedPersistentClass
    }

    def "test bindOrderBy throws MappingException when class is unmapped"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def persistentClasses = [:] // Empty map, so CSPBAssociatedItem will be missing

        when:
        binder.bindOrderBy(property, collection, persistentClasses)

        then:
        thrown(org.hibernate.MappingException)
    }

    def "test bindOrderBy with table per hierarchy subclass"() {
        given:
        def property = createTestHibernateToManyProperty(CSPBTestEntityWithMany, "items") as HibernateToManyProperty
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def persistentClasses = [:]
        def associatedPersistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        associatedPersistentClass.setEntityName(CSPBAssociatedItem.name)
        persistentClasses[CSPBAssociatedItem.name] = associatedPersistentClass

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
        binder.bindOrderBy(spiedProperty, collection, persistentClasses)

        then:
        collection.getWhere() == "item_type in ('A','B')"
    }
}

@Entity
class CSPBTestEntityWithMany {
    Long id
    String name
    static hasMany = [items: CSPBAssociatedItem]
}

@Entity
class CSPBAssociatedItem {
    Long id
    String value
    CSPBTestEntityWithMany parent // Bidirectional for association property testing
    static belongsTo = [parent: CSPBTestEntityWithMany]
}

@Entity
class CSPBUnidirectionalEntity {
    Long id
    Set<CSPBAssociatedItem> items
    static hasMany = [items: CSPBAssociatedItem]
}
