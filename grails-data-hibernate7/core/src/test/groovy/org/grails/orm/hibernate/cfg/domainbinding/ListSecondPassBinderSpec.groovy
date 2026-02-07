package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.HibernateToManyProperty

import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SimpleValue

class ListSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([
                ListBinderAuthor,
                ListBinderBook
        ])
    }

    void "Test bind list second pass"() {
        given:
        def collector = getCollector()
        def binder = getGrailsDomainBinder()
        def collectionBinder = binder.getCollectionBinder()
        def listSecondPassBinder = new ListSecondPassBinder(binder.getMetadataBuildingContext(), collectionBinder)

        def authorEntity = getPersistentEntity(ListBinderAuthor) as GrailsHibernatePersistentEntity
        def bookEntity = getPersistentEntity(ListBinderBook) as GrailsHibernatePersistentEntity

        // Register referenced entity in Hibernate
        binder.bindRoot(bookEntity, collector, "sessionFactory")

        // Manually create RootClass for the main entity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "LIST_BINDER_AUTHOR", null, false, binder.getMetadataBuildingContext()))
        
        // Add a primary key to avoid NPE
        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        def booksProp = authorEntity.getPropertyByName("books") as HibernateToManyProperty
        def list = new org.hibernate.mapping.List(binder.getMetadataBuildingContext(), rootClass)
        list.setRole(authorEntity.name + ".books")
        
        // Initial first pass binding needed for second pass to work
        collectionBinder.bindCollection(booksProp, list, rootClass, collector, "", "sessionFactory")

        // Prepare persistentClasses map
        Map persistentClasses = [
            (authorEntity.name): rootClass,
            (bookEntity.name): collector.getEntityBinding(bookEntity.name)
        ]

        when:
        listSecondPassBinder.bindListSecondPass(booksProp, collector, persistentClasses, list, "sessionFactory")
        collector.processSecondPasses(binder.getMetadataBuildingContext())

        then:
        list.index != null
        list.index instanceof SimpleValue
        ((SimpleValue)list.index).typeName == "integer"
        list.element != null
    }
}

@Entity
class ListBinderAuthor {
    Long id
    java.util.List<ListBinderBook> books
    static hasMany = [books: ListBinderBook]
}

@Entity
class ListBinderBook {
    Long id
    String title
}
