package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.HibernateToManyProperty
import org.hibernate.mapping.Map
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SimpleValue

class MapSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([
                MapAuthorBinder,
                MapBookBinder
        ])
    }

    void "Test bind map second pass"() {
        given:
        def collector = getCollector()
        def binder = getGrailsDomainBinder()
        def collectionBinder = binder.getCollectionBinder()
        def collectionSecondPassBinder = new CollectionSecondPassBinder(binder.getMetadataBuildingContext(), binder.getNamingStrategy())
        def mapSecondPassBinder = new MapSecondPassBinder(binder.getMetadataBuildingContext(), binder.getNamingStrategy(), collectionSecondPassBinder)

        def authorEntity = getPersistentEntity(MapAuthorBinder) as GrailsHibernatePersistentEntity
        def bookEntity = getPersistentEntity(MapBookBinder) as GrailsHibernatePersistentEntity

        // Register referenced entity in Hibernate
        binder.bindRoot(bookEntity, collector, "sessionFactory")

        // Manually create RootClass for the main entity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "MAP_AUTHOR_BINDER", null, false, binder.getMetadataBuildingContext()))
        
        // Add a primary key to avoid NPE
        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        def booksProp = authorEntity.getPropertyByName("books") as HibernateToManyProperty
        def map = new org.hibernate.mapping.Map(binder.getMetadataBuildingContext(), rootClass)
        map.setRole(authorEntity.name + ".books")
        
        // Initial first pass binding
        collectionBinder.bindCollection(booksProp, map, rootClass, collector, "", "sessionFactory")

        // Prepare persistentClasses map
        java.util.Map persistentClasses = [
            (authorEntity.name): rootClass,
            (bookEntity.name): collector.getEntityBinding(bookEntity.name)
        ]

        when:
        collectionSecondPassBinder.bindCollectionSecondPass(booksProp, collector, persistentClasses, map, "sessionFactory")
        mapSecondPassBinder.bindMapSecondPass(booksProp, collector, persistentClasses, map, "sessionFactory")
        collector.processSecondPasses(binder.getMetadataBuildingContext())

        then:
        map.index != null
        map.index instanceof SimpleValue
        ((SimpleValue)map.index).typeName == "string"
        map.element != null
    }
}

@Entity
class MapAuthorBinder {
    Long id
    java.util.Map<String, MapBookBinder> books
    static hasMany = [books: MapBookBinder]
}

@Entity
class MapBookBinder {
    Long id
    String title
}
