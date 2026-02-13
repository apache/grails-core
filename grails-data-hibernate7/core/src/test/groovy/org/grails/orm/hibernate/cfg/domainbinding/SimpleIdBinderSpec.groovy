package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.PrimaryKey
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table

import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator

class SimpleIdBinderSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    JdbcEnvironment jdbcEnvironment
    def simpleValueBinder
    def propertyBinder
    def basicValueIdCreator
    Table currentTable

    def simpleIdBinder

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        jdbcEnvironment = getGrailsDomainBinder().getJdbcEnvironment()

        // Use a Mock for BasicValueIdCreator and return a BasicValue based on the currentTable
        basicValueIdCreator = Mock(BasicValueIdCreator)
        basicValueIdCreator.getBasicValueId(*_) >> { Identity id, boolean useSeq ->
            new BasicValue(metadataBuildingContext, currentTable)
        }

        // Mock the collaborators that can be safely mocked
        simpleValueBinder = Mock(SimpleValueBinder)
        propertyBinder = Spy(PropertyBinder)

        simpleIdBinder = new SimpleIdBinder(basicValueIdCreator, simpleValueBinder, propertyBinder)
    }

    def "bindSimpleId with identity generator"() {
        given:
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> false
        }
        def testProperty = Mock(GrailsHibernatePersistentProperty) {
            getName() >> "id"
        }
        def domainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getIdentity() >> testProperty
            getName() >> "TestEntity"
        }
        def rootClass = new RootClass(metadataBuildingContext)
        currentTable = new Table("TEST_TABLE")
        rootClass.setTable(currentTable)

        when:
        simpleIdBinder.bindSimpleId(domainClass, rootClass, new Identity(generator: GrailsSequenceGeneratorEnum.IDENTITY.toString()))

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty as GrailsHibernatePersistentProperty, null, _, "")
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
        def testProperty = Mock(GrailsHibernatePersistentProperty) {
            getName() >> "id"
        }
        def domainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getIdentity() >> testProperty
            getName() >> "TestEntity"
        }
        def rootClass = new RootClass(metadataBuildingContext)
        currentTable = new Table("TEST_TABLE")
        rootClass.setTable(currentTable)

        when:
        simpleIdBinder.bindSimpleId(domainClass, rootClass, new Identity(generator: GrailsSequenceGeneratorEnum.SEQUENCE.toString(), params: [sequence: 'SEQ_TEST']))

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty as GrailsHibernatePersistentProperty, null, _, "")
        1 * propertyBinder.bindProperty(testProperty, _)

        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey instanceof PrimaryKey
    }

    def "bindSimpleId with non-existent identifier property"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity) {
            getName() >> "TestEntity"
            getPropertyByName("nonExistent") >> null
            getIdentity() >> Mock(GrailsHibernatePersistentProperty)
        }
        def rootClass = new RootClass(metadataBuildingContext)

        when:
        simpleIdBinder.bindSimpleId(domainClass, rootClass, new Identity(name: "nonExistent"))

        then:
        thrown(org.hibernate.MappingException)
    }
}
