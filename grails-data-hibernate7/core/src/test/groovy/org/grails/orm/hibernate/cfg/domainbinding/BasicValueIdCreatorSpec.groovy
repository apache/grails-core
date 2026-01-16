package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.GenerationType
import org.grails.orm.hibernate.cfg.GrailsSequenceStyleGenerator
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.sequence.SequenceSupport
import org.hibernate.engine.config.spi.ConfigurationService
import org.hibernate.engine.jdbc.env.spi.IdentifierHelper
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.Generator
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.Assigned
import org.hibernate.id.IdentityGenerator
import org.hibernate.id.IncrementGenerator
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.id.enhanced.TableGenerator
import org.hibernate.id.uuid.UuidGenerator
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.BasicType
import spock.lang.Unroll

class BasicValueIdCreatorSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    BasicValueIdCreator creator
    RootClass entity
    Table table

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        creator = new BasicValueIdCreator(metadataBuildingContext)
        entity = new RootClass(metadataBuildingContext)
        table = new Table("test_table")
        entity.setTable(table)
    }

    @Unroll
    def "should create BasicValue with correct generator for #generatorName (useSequence: #useSequence)"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator(generatorName)
        def property = createDummyProperty()

        when:
        BasicValue id = creator.getBasicValueId(entity, mappedId, useSequence)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(createContext(id, property))

        then:
        expectedClass.isInstance(generator)

        where:
        generatorName       | useSequence | expectedClass
        "identity"          | false       | IdentityGenerator
        "sequence"          | true        | GrailsSequenceStyleGenerator
        "sequence-identity" | true        | GrailsSequenceStyleGenerator
        "increment"         | false       | IncrementGenerator
        "uuid"              | false       | UuidGenerator
        "uuid2"             | false       | UuidGenerator
        "assigned"          | false       | Assigned
        "table"             | false       | TableGenerator
        "enhanced-table"    | false       | TableGenerator
        "hilo"              | false       | SequenceStyleGenerator
    }

    def "should default to native generator when mappedId is null"() {
        when:
        BasicValue id = creator.getBasicValueId(entity, null, false)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(createContext(id))

        then:
        generator instanceof GrailsNativeGenerator
    }

    def "should default to sequence-identity when mappedId is null and useSequence is true"() {
        when:
        BasicValue id = creator.getBasicValueId(entity, null, true)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(createContext(id))

        then:
        generator instanceof GrailsSequenceStyleGenerator
    }

    def "should use sequence-identity when generator is native and useSequence is true"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator("native")

        when:
        BasicValue id = creator.getBasicValueId(entity, mappedId, true)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(createContext(id))

        then:
        generator instanceof GrailsSequenceStyleGenerator
    }

    def "should configure identity column for identity generator"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator("identity")
        // We need a real column structure for IdentityGenerator to work with
        def column = new Column("id")
        
        // Mocking the context to simulate what Hibernate passes
        def property = Mock(Property)
        def value = Mock(Value) // We can mock Value interface
        value.getColumns() >> [column]
        property.getValue() >> value
        
        when:
        BasicValue id = creator.getBasicValueId(entity, mappedId, false)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        def context = createContext(id, property)
        
        generatorCreator.createGenerator(context)

        then:
        column.isIdentity()
    }

    def "should pass generator properties to sequence generator"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator("sequence")
        mappedId.setParams([sequence_name: "my_seq"])

        when:
        BasicValue id = creator.getBasicValueId(entity, mappedId, true)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        GrailsSequenceStyleGenerator generator = (GrailsSequenceStyleGenerator) generatorCreator.createGenerator(createContext(id))

        then:
        generator.getDatabaseStructure().getPhysicalName().getObjectName().getText() == "my_seq"
    }

    private Property createDummyProperty() {
        def column = new Column("id")
        def property = Mock(Property)
        def value = Mock(Value)
        value.getColumns() >> [column]
        property.getValue() >> value
        return property
    }

    private GeneratorCreationContext createContext(BasicValue id, Property property = null) {
        def context = Mock(GeneratorCreationContext)
        def type = Mock(BasicType)
        def database = Mock(Database)
        def dialect = Mock(Dialect)
        def serviceRegistry = Mock(ServiceRegistry)
        def jdbcEnvironment = Mock(JdbcEnvironment)
        def identifierHelper = Mock(IdentifierHelper)
        def sequenceSupport = Mock(SequenceSupport)
        def configurationService = Mock(ConfigurationService)

        type.getReturnedClass() >> String.class
        context.getType() >> type
        
        // Mocking for NativeGenerator
        context.getDatabase() >> database
        database.getDialect() >> dialect
        dialect.getNativeValueGenerationStrategy() >> GenerationType.SEQUENCE
        dialect.getSequenceSupport() >> sequenceSupport
        sequenceSupport.supportsSequences() >> true

        // Mocking for SequenceStyleGenerator
        context.getServiceRegistry() >> serviceRegistry
        serviceRegistry.requireService(JdbcEnvironment.class) >> jdbcEnvironment
        serviceRegistry.requireService(ConfigurationService.class) >> configurationService
        jdbcEnvironment.getDialect() >> dialect
        jdbcEnvironment.getIdentifierHelper() >> identifierHelper
        identifierHelper.toIdentifier(_, _) >> { String text, boolean quoted -> 
            return text == null ? null : new Identifier(text, quoted)
        }
        identifierHelper.toIdentifier(_) >> { String text -> 
            return text == null ? null : new Identifier(text, false)
        }

        if (property != null) {
            context.getProperty() >> property
        }

        return context
    }
}
