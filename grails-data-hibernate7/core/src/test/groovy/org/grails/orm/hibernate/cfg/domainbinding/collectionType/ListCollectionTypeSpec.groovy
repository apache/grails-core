package org.grails.orm.hibernate.cfg.domainbinding.collectionType

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.HibernateToManyProperty
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.List as HibernateList
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class ListCollectionTypeSpec extends HibernateGormDatastoreSpec {

    def "should create a List and delegate to binder"() {
        given:
        def binder = Mock(GrailsDomainBinder)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        binder.getMetadataBuildingContext() >> metadataBuildingContext
        
        @Subject
        def collectionType = new ListCollectionType(binder)
        
        def property = Mock(HibernateToManyProperty)
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
        def result = collectionType.create(property, owner)

        then:
        result instanceof HibernateList
        result.getCollectionTable() == table
    }
}