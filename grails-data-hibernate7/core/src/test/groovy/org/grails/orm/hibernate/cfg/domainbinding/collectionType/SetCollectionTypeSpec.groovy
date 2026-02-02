package org.grails.orm.hibernate.cfg.domainbinding.collectionType

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.orm.hibernate.cfg.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class SetCollectionTypeSpec extends HibernateGormDatastoreSpec {

    def "should create a Set and delegate to binder"() {
        given:
        def binder = Mock(GrailsDomainBinder)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        binder.getMetadataBuildingContext() >> metadataBuildingContext
        
        @Subject
        def collectionType = new SetCollectionType(binder)
        
        def property = Mock(ToMany)
        def owner = new RootClass(metadataBuildingContext)
        def table = new Table("test_table")
        owner.setTable(table)
        
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        property.getOwner() >> domainClass
        domainClass.getMappedForm() >> null
        
        def mappings = Mock(InFlightMetadataCollector)
        def path = "testPath"
        def sessionFactoryBeanName = "sessionFactory"

        when:
        def result = collectionType.create(property, owner, path, mappings, sessionFactoryBeanName)

        then:
        result instanceof org.hibernate.mapping.Set
        result.getCollectionTable() == table
        1 * binder.bindCollection(property, _ as org.hibernate.mapping.Set, owner, mappings, path, sessionFactoryBeanName)
    }
}
