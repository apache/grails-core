package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.PrimaryKey
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Unroll

class SimpleIdBinderSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    JdbcEnvironment jdbcEnvironment
    def simpleValueBinder
    def propertyBinder
    def basicValueIdCreator

    def simpleIdBinder

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        jdbcEnvironment = getGrailsDomainBinder().getJdbcEnvironment()

        // Use a Mock for BasicValueIdCreator and return a BasicValue based on the RootClass table
        basicValueIdCreator = Mock(BasicValueIdCreator)
        basicValueIdCreator.getBasicValueId(*_) >> { RootClass rc, Identity id, boolean useSeq ->
            new BasicValue(metadataBuildingContext, rc.getTable())
        }

        // Mock the collaborators that can be safely mocked
        simpleValueBinder = Mock(SimpleValueBinder)
        propertyBinder = Mock(PropertyBinder)

        simpleIdBinder = new SimpleIdBinder(basicValueIdCreator, simpleValueBinder, propertyBinder)
    }

    def "bindSimpleId with identity generator"() {
        given:
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> false
        }
        def testProperty = Mock(PersistentProperty) {
            getName() >> "id"
            getOwner() >> Mock(GrailsHibernatePersistentEntity) {
                getMappedForm() >> mapping
            }
        }
        def rootClass = new RootClass(metadataBuildingContext)
        def table = new Table("TEST_TABLE")
        rootClass.setTable(table)

        when:
        simpleIdBinder.bindSimpleId(testProperty, rootClass, new Identity(generator: 'identity'))

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty, null, _, "")
        1 * propertyBinder.bindProperty(testProperty, _)

        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey instanceof PrimaryKey
    }

    def "bindSimpleId with sequence generator"() {
        given:
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> true
        }
        def testProperty = Mock(PersistentProperty) {
            getName() >> "id"
            getOwner() >> Mock(GrailsHibernatePersistentEntity) {
                getMappedForm() >> mapping
            }
        }
        def rootClass = new RootClass(metadataBuildingContext)
        def table = new Table("TEST_TABLE")
        rootClass.setTable(table)

        when:
        simpleIdBinder.bindSimpleId(testProperty, rootClass, new Identity(generator: 'sequence', params: [sequence: 'SEQ_TEST']))

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty, null, _, "")
        1 * propertyBinder.bindProperty(testProperty, _)

        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey instanceof PrimaryKey
    }
}