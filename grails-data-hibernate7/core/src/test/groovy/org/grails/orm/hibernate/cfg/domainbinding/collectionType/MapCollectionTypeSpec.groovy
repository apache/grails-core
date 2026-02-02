package org.grails.orm.hibernate.cfg.domainbinding.collectionType

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.orm.hibernate.cfg.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.RootClass
import spock.lang.Subject

class MapCollectionTypeSpec extends HibernateGormDatastoreSpec {

    def "should create a Map and delegate to binder"() {
        given:
        def binder = Mock(GrailsDomainBinder)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        binder.getMetadataBuildingContext() >> metadataBuildingContext
        
        @Subject
        def collectionType = new MapCollectionType(binder)
        
        def property = Mock(ToMany)
        def owner = new RootClass(metadataBuildingContext)
        
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        property.getOwner() >> domainClass
        domainClass.getMappedForm() >> null
        
        def mappings = Mock(InFlightMetadataCollector)
        def path = "testPath"
        def sessionFactoryBeanName = "sessionFactory"

        when:
        def result = collectionType.create(property, owner, path, mappings, sessionFactoryBeanName)

        then:
        result instanceof org.hibernate.mapping.Map
        1 * binder.bindCollection(property, _ as org.hibernate.mapping.Map, owner, mappings, path, sessionFactoryBeanName)
    }
}
