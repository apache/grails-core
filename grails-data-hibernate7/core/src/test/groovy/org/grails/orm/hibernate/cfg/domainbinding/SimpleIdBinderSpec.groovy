package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PersistentEntity
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
    def hibernateEntityWrapper
    def simpleValueBinder
    def propertyBinder
    def basicValueIdcreator

    def simpleIdBinder

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        jdbcEnvironment = getGrailsDomainBinder().getJdbcEnvironment()

        // Use a Mock for BasicValueIdCreator and return a BasicValue based on the RootClass table
        basicValueIdcreator = Mock(BasicValueIdCreator)
        basicValueIdcreator.getBasicValueId(*_) >> { RootClass rc, Identity id, boolean useSeq ->
            new BasicValue(metadataBuildingContext, rc.getTable())
        }

        // Mock the collaborators that can be safely mocked
        hibernateEntityWrapper = Mock(HibernateEntityWrapper)
        simpleValueBinder = Mock(SimpleValueBinder)
        propertyBinder = Mock(PropertyBinder)

        simpleIdBinder = new SimpleIdBinder(basicValueIdcreator, hibernateEntityWrapper, simpleValueBinder, propertyBinder)
    }

    def "bindSimpleId with identity generator"() {
        given:
        def testProperty = Mock(PersistentProperty) {
            getName() >> "id"
            getOwner() >> Mock(PersistentEntity)
        }
        def rootClass = new RootClass(metadataBuildingContext)
        def table = new Table("TEST_TABLE")
        rootClass.setTable(table)

        hibernateEntityWrapper.getMappedForm(_) >> Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> false
        }

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
        def testProperty = Mock(PersistentProperty) {
            getName() >> "id"
            getOwner() >> Mock(PersistentEntity)
        }
        def rootClass = new RootClass(metadataBuildingContext)
        def table = new Table("TEST_TABLE")
        rootClass.setTable(table)

        hibernateEntityWrapper.getMappedForm(_) >> Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> true
        }

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