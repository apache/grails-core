package org.grails.orm.hibernate.cfg.domainbinding.generator

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity

import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.Assigned
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.uuid.UuidGenerator
import org.hibernate.type.Type
import spock.lang.Specification
import spock.lang.Unroll

class GrailsSequenceGeneratorEnumSpec extends Specification {

    Map<Class, Object> mockGenerators = [:]

    def setup() {
        mockGenerators[GrailsIdentityGenerator] = Mock(GrailsIdentityGenerator)
        GroovyMock(GrailsIdentityGenerator, global: true)
        _ * new GrailsIdentityGenerator(*_) >> mockGenerators[GrailsIdentityGenerator]

        mockGenerators[GrailsSequenceStyleGenerator] = Mock(GrailsSequenceStyleGenerator)
        GroovyMock(GrailsSequenceStyleGenerator, global: true)
        _ * new GrailsSequenceStyleGenerator(*_) >> mockGenerators[GrailsSequenceStyleGenerator]

        mockGenerators[GrailsIncrementGenerator] = Mock(GrailsIncrementGenerator)
        GroovyMock(GrailsIncrementGenerator, global: true)
        _ * new GrailsIncrementGenerator(*_) >> mockGenerators[GrailsIncrementGenerator]

        mockGenerators[UuidGenerator] = Mock(UuidGenerator)
        GroovyMock(UuidGenerator, global: true)
        _ * new UuidGenerator(*_) >> mockGenerators[UuidGenerator]

        mockGenerators[Assigned] = Mock(Assigned)
        GroovyMock(Assigned, global: true)
        _ * new Assigned(*_) >> mockGenerators[Assigned]

        mockGenerators[GrailsTableGenerator] = Mock(GrailsTableGenerator)
        GroovyMock(GrailsTableGenerator, global: true)
        _ * new GrailsTableGenerator(*_) >> mockGenerators[GrailsTableGenerator]

        mockGenerators[GrailsNativeGenerator] = Mock(GrailsNativeGenerator)
        GroovyMock(GrailsNativeGenerator, global: true)
        _ * new GrailsNativeGenerator(*_) >> mockGenerators[GrailsNativeGenerator]
    }

    @Unroll
    def "should return correct generator for #strategyName"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def mappedId = Mock(Identity)
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def jdbcEnvironment = Mock(JdbcEnvironment)

        // Setup for UuidGenerator which needs context.getType().getReturnedClass()
        def type = Mock(Type)
        context.getType() >> type
        type.getReturnedClass() >> String

        when:
        def generator = GrailsSequenceGeneratorEnum.getGenerator(strategyName, context, mappedId, domainClass, jdbcEnvironment)

        then:
        generator == mockGenerators[expectedClass]

        where:
        strategyName        | expectedClass
        "identity"          | GrailsIdentityGenerator
        "sequence"          | GrailsSequenceStyleGenerator
        "sequence-identity" | GrailsSequenceStyleGenerator
        "increment"         | GrailsIncrementGenerator
        "uuid"              | UuidGenerator
        "uuid2"             | UuidGenerator
        "assigned"          | Assigned
        "table"             | GrailsTableGenerator
        "enhanced-table"    | GrailsTableGenerator
        "hilo"              | GrailsSequenceStyleGenerator
        "native"            | GrailsNativeGenerator
        "unknown"           | GrailsNativeGenerator // Default
    }

    def "fromName should return correct enum"() {
        expect:
        GrailsSequenceGeneratorEnum.fromName("identity").get() == GrailsSequenceGeneratorEnum.IDENTITY
        GrailsSequenceGeneratorEnum.fromName("nonexistent").isEmpty()
    }
}
