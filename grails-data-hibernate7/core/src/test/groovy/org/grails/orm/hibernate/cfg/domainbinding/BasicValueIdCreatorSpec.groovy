package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.generator.Generator
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator

class BasicValueIdCreatorSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    BasicValueIdCreator creator
    BasicValue basicValue
    RootClass entity
    Table table
    GrailsSequenceWrapper grailsSequenceWrapper
    JdbcEnvironment jdbcEnvironment

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        jdbcEnvironment = Mock(JdbcEnvironment)
        grailsSequenceWrapper = Mock(GrailsSequenceWrapper)
        entity = new RootClass(metadataBuildingContext)
        table = new Table("test_table")
        entity.setTable(table)
        // Use a real BasicValue to test that the generator creator lambda is correctly set and executable
        basicValue = new BasicValue(metadataBuildingContext, table)
        creator = new BasicValueIdCreator(jdbcEnvironment, basicValue, grailsSequenceWrapper)
    }

    @Unroll
    def "should create BasicValue using factory for #generatorName (useSequence: #useSequence)"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator(generatorName)
        def mockGenerator = Mock(Generator)
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.getBasicValueId(mappedId, useSequence)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator(generatorName, context, mappedId, _, jdbcEnvironment) >> mockGenerator
        generator == mockGenerator

        where:
        generatorName                                            | useSequence
        GrailsSequenceGeneratorEnum.IDENTITY.toString()          | false
        GrailsSequenceGeneratorEnum.SEQUENCE.toString()          | true
        GrailsSequenceGeneratorEnum.SEQUENCE_IDENTITY.toString() | true
        GrailsSequenceGeneratorEnum.INCREMENT.toString()         | false
        GrailsSequenceGeneratorEnum.UUID.toString()              | false
        GrailsSequenceGeneratorEnum.UUID2.toString()             | false
        GrailsSequenceGeneratorEnum.ASSIGNED.toString()          | false
        GrailsSequenceGeneratorEnum.TABLE.toString()             | false
        GrailsSequenceGeneratorEnum.ENHANCED_TABLE.toString()    | false
        GrailsSequenceGeneratorEnum.HILO.toString()              | false
    }

    def "should default to native generator when mappedId is null"() {
        given:
        def mockGenerator = Mock(Generator)
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.getBasicValueId(null, false)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator(GrailsSequenceGeneratorEnum.NATIVE.toString(), context, null, _, jdbcEnvironment) >> mockGenerator
        generator == mockGenerator
    }

    def "should default to sequence-identity when mappedId is null and useSequence is true"() {
        given:
        def mockGenerator = Mock(Generator)
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.getBasicValueId(null, true)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator(GrailsSequenceGeneratorEnum.SEQUENCE_IDENTITY.toString(), context, null, _, jdbcEnvironment) >> mockGenerator
        generator == mockGenerator
    }

    def "should use sequence-identity when generator is native and useSequence is true"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator(GrailsSequenceGeneratorEnum.NATIVE.toString())
        def mockGenerator = Mock(Generator)
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.getBasicValueId(mappedId, true)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator(GrailsSequenceGeneratorEnum.SEQUENCE_IDENTITY.toString(), context, mappedId, _, jdbcEnvironment) >> mockGenerator
        generator == mockGenerator
    }

    def "should pass mappedId to factory"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator("custom")
        def context = Mock(GeneratorCreationContext)

        when:
        BasicValue id = creator.getBasicValueId(mappedId, false)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        generatorCreator.createGenerator(context)

        then:
        1 * grailsSequenceWrapper.getGenerator("custom", context, mappedId, _, jdbcEnvironment) >> Mock(Generator)
    }
}
