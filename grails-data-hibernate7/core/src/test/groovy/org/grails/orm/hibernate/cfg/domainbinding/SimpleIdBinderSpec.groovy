package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.orm.hibernate.cfg.Identity
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Issue

class SimpleIdBinderSpec extends HibernateGormDatastoreSpec {

    def metadataBuildingContext
    def namingStrategy
    def hibernateEntityWrapper
    def simpleValueBinder
    def propertyBinder
    def basicValueIdCreator

    def simpleIdBinder // Subject under test

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        namingStrategy = Mock(PersistentEntityNamingStrategy)
        hibernateEntityWrapper = Mock(HibernateEntityWrapper)
        simpleValueBinder = Mock(SimpleValueBinder)
        propertyBinder = Mock(PropertyBinder)
        // Use real BasicValueIdCreator instead of Mock to avoid issues
        basicValueIdCreator = new BasicValueIdCreator(metadataBuildingContext)

        simpleIdBinder = new SimpleIdBinder(basicValueIdCreator, hibernateEntityWrapper, simpleValueBinder, propertyBinder)
    }


    def "test bindSimpleId method with identity generator"() {
        given:
        def testEntity = createPersistentEntity(TestEntityId)
        assert testEntity != null
        def rootClass = new RootClass(metadataBuildingContext)
        def table = new Table("TEST_TABLE")
        rootClass.setTable(table)

        def mappedId = new Identity(generator: "identity")
        def testProperty = testEntity.getIdentity()

        // Stubbing interactions
        hibernateEntityWrapper.getMappedForm(_) >> Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> false
        }

        when:
        simpleIdBinder.bindSimpleId(testProperty, rootClass, mappedId)

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty, null, _, '')
        1 * propertyBinder.bindProperty(testProperty, _)
        
        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey != null
    }

    def "test bindSimpleId method with sequence generator"() {
        given:
        def testEntity = createPersistentEntity(TestEntitySeq)
        assert testEntity != null
        def rootClass = new RootClass(metadataBuildingContext)
        def table = new Table("TEST_TABLE")
        rootClass.setTable(table)

        def mappedId = new Identity(generator: "sequence", params: [sequence: "SEQ_TEST"])
        def testProperty = testEntity.getIdentity()

        // Stubbing interactions
        hibernateEntityWrapper.getMappedForm(_) >> Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> true
        }

        when:
        simpleIdBinder.bindSimpleId(testProperty, rootClass, mappedId)

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty, null, _, '')
        1 * propertyBinder.bindProperty(testProperty, _)
        
        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey != null
    }
}

// Helper domain classes
@Entity
class TestEntityId {
    Long id
    String name
    static mapping = {
        id generator: 'identity'
    }
}

@Entity
class TestEntitySeq {
    Long id
    String name
    static mapping = {
        id generator: 'sequence', params: [sequence: 'SEQ_TEST']
    }
}
