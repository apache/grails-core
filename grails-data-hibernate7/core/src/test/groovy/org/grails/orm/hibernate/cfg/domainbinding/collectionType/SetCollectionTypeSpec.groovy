package org.grails.orm.hibernate.cfg.domainbinding.collectionType

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set as HibernateSet
import org.hibernate.mapping.Table
import spock.lang.Subject

class SetCollectionTypeSpec extends HibernateGormDatastoreSpec {

    def "should create a Set and delegate to binder"() {
        given:
        def binder = Mock(GrailsDomainBinder)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        binder.getMetadataBuildingContext() >> metadataBuildingContext
        
        @Subject
        def collectionType = new SetCollectionType(metadataBuildingContext)
        
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
        result instanceof HibernateSet
        result.getCollectionTable() == table
    }
}