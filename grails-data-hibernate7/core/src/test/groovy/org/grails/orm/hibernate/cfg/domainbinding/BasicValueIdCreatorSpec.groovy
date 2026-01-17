package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.generator.Generator
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Unroll

import java.util.function.BiFunction

class BasicValueIdCreatorSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    BasicValueIdCreator creator
    RootClass entity
    Table table
    Map<String, BiFunction<GeneratorCreationContext, Identity, Generator>> generatorFactories

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        generatorFactories = [:]
        creator = new BasicValueIdCreator(metadataBuildingContext, generatorFactories)
        entity = new RootClass(metadataBuildingContext)
        table = new Table("test_table")
        entity.setTable(table)
    }

    @Unroll
    def "should create BasicValue using factory for #generatorName (useSequence: #useSequence)"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator(generatorName)
        def mockGenerator = Mock(Generator)
        generatorFactories.put(generatorName, { ctx, mid -> mockGenerator } as BiFunction)

        when:
        BasicValue id = creator.getBasicValueId(entity, mappedId, useSequence)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(Mock(GeneratorCreationContext))

        then:
        generator == mockGenerator

        where:
        generatorName       | useSequence
        "identity"          | false
        "sequence"          | true
        "sequence-identity" | true
        "increment"         | false
        "uuid"              | false
        "uuid2"             | false
        "assigned"          | false
        "table"             | false
        "enhanced-table"    | false
        "hilo"              | false
    }

    def "should default to native generator when mappedId is null"() {
        given:
        // Native generator is the default, not in the map passed to constructor usually, 
        // but here we are testing the logic inside getBasicValueId that selects the key.
        // If mappedId is null and useSequence is false, it selects "native".
        // "native" is NOT in the map by default in my test setup (empty map).
        // So it falls back to default lambda in getBasicValueId: (ctx, mid) -> new GrailsNativeGenerator(ctx)
        // We can't easily mock the default lambda unless we change the code to look up "native" in the map too.
        // The code uses getOrDefault(generator, default).
        // If generator is "native", and "native" is NOT in map, it uses default.
        // Let's put "native" in the map to verify it selects "native".
        def mockGenerator = Mock(Generator)
        generatorFactories.put("native", { ctx, mid -> mockGenerator } as BiFunction)

        when:
        BasicValue id = creator.getBasicValueId(entity, null, false)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(Mock(GeneratorCreationContext))

        then:
        generator == mockGenerator
    }

    def "should default to sequence-identity when mappedId is null and useSequence is true"() {
        given:
        def mockGenerator = Mock(Generator)
        generatorFactories.put("sequence-identity", { ctx, mid -> mockGenerator } as BiFunction)

        when:
        BasicValue id = creator.getBasicValueId(entity, null, true)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(Mock(GeneratorCreationContext))

        then:
        generator == mockGenerator
    }

    def "should use sequence-identity when generator is native and useSequence is true"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator("native")
        def mockGenerator = Mock(Generator)
        generatorFactories.put("sequence-identity", { ctx, mid -> mockGenerator } as BiFunction)

        when:
        BasicValue id = creator.getBasicValueId(entity, mappedId, true)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        Generator generator = generatorCreator.createGenerator(Mock(GeneratorCreationContext))

        then:
        generator == mockGenerator
    }

    def "should pass mappedId to factory"() {
        given:
        Identity mappedId = new Identity()
        mappedId.setGenerator("custom")
        Identity capturedId = null
        generatorFactories.put("custom", { ctx, mid -> 
            capturedId = mid
            return Mock(Generator) 
        } as BiFunction)

        when:
        BasicValue id = creator.getBasicValueId(entity, mappedId, false)
        def generatorCreator = id.getCustomIdGeneratorCreator()
        generatorCreator.createGenerator(Mock(GeneratorCreationContext))

        then:
        capturedId == mappedId
    }
}
