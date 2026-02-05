package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set

class CollectionBinderSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([
            org.apache.grails.data.testing.tck.domains.Pet,
            org.apache.grails.data.testing.tck.domains.Person,
            org.apache.grails.data.testing.tck.domains.PetType
        ])
    }

    void "Test bind collection"() {
        given:
        def binder = getGrailsDomainBinder()
        def collectionBinder = binder.getCollectionBinder()
        def collector = getCollector()

        def personEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Person) as GrailsHibernatePersistentEntity
        def petEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Pet) as GrailsHibernatePersistentEntity

        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(personEntity.name)
        rootClass.setTable(collector.addTable(null, null, "PERSON", null, false, binder.getMetadataBuildingContext()))

        def petsProp = personEntity.getPropertyByName("pets") as GrailsHibernatePersistentProperty
        def collection = new Set(binder.getMetadataBuildingContext(), rootClass)

        when:
        collectionBinder.bindCollection(petsProp, collection, rootClass, collector, "", "sessionFactory")

        then:
        collection.role == "${personEntity.name}.pets".toString()
        collection.element instanceof OneToMany
        (collection.element as OneToMany).referencedEntityName == petEntity.name
    }
}
