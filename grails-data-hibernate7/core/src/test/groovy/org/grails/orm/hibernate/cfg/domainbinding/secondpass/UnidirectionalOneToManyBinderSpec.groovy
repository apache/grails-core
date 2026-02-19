package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.orm.hibernate.cfg.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.hibernate.mapping.Backref
import org.hibernate.mapping.Bag
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.PersistentClass
import spock.lang.Subject

class UnidirectionalOneToManyBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    UnidirectionalOneToManyBinder binder

    def setupSpec() {
        manager.addAllDomainClasses([
                UniOwner, UniPet
        ])
    }

    def setup() {
        def grailsDomainBinder = getGrailsDomainBinder()
        def metadataBuildingContext = grailsDomainBinder.getMetadataBuildingContext()
        def namingStrategy = grailsDomainBinder.getNamingStrategy()
        def jdbcEnvironment = grailsDomainBinder.getJdbcEnvironment()
        def defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy)
        def backticksRemover = new BackticksRemover()
        def columnNameForPropertyAndPathFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover)

        def unidirectionalOneToManyInverseValuesBinder = new UnidirectionalOneToManyInverseValuesBinder()
        def enumTypeBinder = new EnumTypeBinder(metadataBuildingContext, columnNameForPropertyAndPathFetcher)
        def compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment)
        def simpleValueColumnFetcher = new SimpleValueColumnFetcher()
        def collectionForPropertyConfigBinder = new CollectionForPropertyConfigBinder()

        def collectionWithJoinTableBinder = new CollectionWithJoinTableBinder(
                metadataBuildingContext,
                namingStrategy,
                unidirectionalOneToManyInverseValuesBinder,
                enumTypeBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher,
                collectionForPropertyConfigBinder,
                new SimpleValueColumnBinder(),
                new ColumnConfigToColumnBinder()
        )
        binder = new UnidirectionalOneToManyBinder(collectionWithJoinTableBinder)
    }

    def "test bindUnidirectionalOneToMany with join table"() {
        given:
        def grailsDomainBinder = getGrailsDomainBinder()
        def ownerEntity = grailsDomainBinder.hibernateMappingContext.getPersistentEntity(UniOwner.name) as GrailsHibernatePersistentEntity
        def petEntity = grailsDomainBinder.hibernateMappingContext.getPersistentEntity(UniPet.name) as GrailsHibernatePersistentEntity

        def ownerToPetsProperty = ownerEntity.getPropertyByName("pets") as HibernateOneToManyProperty

        def mappings = grailsDomainBinder.metadataBuildingContext.metadataCollector
        def ownerPersistentClass = mappings.getEntityBinding(UniOwner.name)
        def collection = new Bag(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass)
        def role = UniOwner.name + ".pets"
        collection.setRole(role)
        collection.setCollectionTable(ownerPersistentClass.getTable()) // Just use owner table for simplicity in this test
        def element = new OneToMany(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass)
        element.setReferencedEntityName(petEntity.getName())
        collection.setElement(element)
        collection.setKey(new BasicValue(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass.getTable()))

        when:
        binder.bind(ownerToPetsProperty, mappings, collection)

        then:
        collection.isInverse() == false
        // By default it uses join table because shouldBindWithForeignKey() is false for unidirectional OTM in hibernate7
        collection.getElement() instanceof org.hibernate.mapping.ManyToOne 
    }

    def "test bindUnidirectionalOneToMany with backref"() {
        given:
        def grailsDomainBinder = getGrailsDomainBinder()
        def ownerEntity = grailsDomainBinder.hibernateMappingContext.getPersistentEntity(UniOwner.name) as GrailsHibernatePersistentEntity
        def petEntity = grailsDomainBinder.hibernateMappingContext.getPersistentEntity(UniPet.name) as GrailsHibernatePersistentEntity

        // Use a Stub for the property to override shouldBindWithForeignKey
        def ownerToPetsProperty = Stub(HibernateOneToManyProperty) {
            shouldBindWithForeignKey() >> true
            getOwner() >> ownerEntity
            getName() >> "pets"
        }

        def mappings = grailsDomainBinder.metadataBuildingContext.metadataCollector
        def ownerPersistentClass = mappings.getEntityBinding(UniOwner.name)
        def petPersistentClass = mappings.getEntityBinding(UniPet.name)

        def collection = new Bag(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass)
        collection.setRole(UniOwner.name + ".pets")

        def element = new OneToMany(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass)
        element.setReferencedEntityName(petEntity.getName())
        collection.setElement(element)
        collection.setKey(new BasicValue(grailsDomainBinder.metadataBuildingContext, ownerPersistentClass.getTable()))

        when:
        binder.bind(ownerToPetsProperty, mappings, collection)

        then:
        collection.isInverse() == false
        petPersistentClass.getProperty("_UniOwner_petsBackref") != null
    }

}

@Entity
class UniOwner {
    Long id
    Set<UniPet> pets
    static hasMany = [pets: UniPet]
}

@Entity
class UniPet {
    Long id
}
