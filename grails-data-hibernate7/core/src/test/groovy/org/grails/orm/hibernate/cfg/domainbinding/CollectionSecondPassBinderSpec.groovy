package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

import org.hibernate.mapping.RootClass

import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionSecondPassBinder

class CollectionSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([
                Author,
                Book
        ])
    }

    void "Test bind collection second pass"() {
        given:
        def collector = getCollector()
        def binder = getGrailsDomainBinder()
        def collectionBinder = binder.getCollectionBinder()
        def namingStrategy = binder.getNamingStrategy()
        def jdbcEnvironment = binder.getJdbcEnvironment()
        def collectionSecondPassBinder = new CollectionSecondPassBinder(
                binder.getMetadataBuildingContext(),
                namingStrategy,
                jdbcEnvironment,
                new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder(namingStrategy, jdbcEnvironment),
                new org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder(),
                new org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder(namingStrategy, jdbcEnvironment),
                new org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder(namingStrategy, jdbcEnvironment),
                new org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher()
        )

        def authorEntity = getPersistentEntity(Author) as GrailsHibernatePersistentEntity
        def bookEntity = getPersistentEntity(Book) as GrailsHibernatePersistentEntity

        // Register referenced entity in Hibernate
        binder.bindRoot(bookEntity, collector, "sessionFactory", new DefaultColumnNameFetcher(binder.getNamingStrategy(), new BackticksRemover()))

        // Manually create RootClass for the main entity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "AUTHOR", null, false, binder.getMetadataBuildingContext()))
        
        // Add a primary key to avoid NPE
        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        def booksProp = authorEntity.getPropertyByName("books") as HibernateToManyProperty
        def set = new org.hibernate.mapping.Set(binder.getMetadataBuildingContext(), rootClass)
        set.setRole(authorEntity.name + ".books")
        
        // Initial first pass binding
        collectionBinder.bindCollection(booksProp, set, rootClass, collector, "", "sessionFactory")

        // Prepare persistentClasses map
        Map persistentClasses = [
            (authorEntity.name): rootClass,
            (bookEntity.name): collector.getEntityBinding(bookEntity.name)
        ]

        when:
        collectionSecondPassBinder.bindCollectionSecondPass(booksProp, collector, persistentClasses, set, "sessionFactory")
        collector.processSecondPasses(binder.getMetadataBuildingContext())

        then:
        set.key != null
        set.element != null
        set.collectionTable != null
    }
}

@Entity
class Author {
    Long id
    Set<Book> books
    static hasMany = [books: Book]
}

@Entity
class Book {
    Long id
    String title
}
