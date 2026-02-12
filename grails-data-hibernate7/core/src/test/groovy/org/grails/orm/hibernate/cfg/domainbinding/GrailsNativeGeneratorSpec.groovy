package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.generator.EventType
import org.hibernate.generator.GeneratorCreationContext
import jakarta.persistence.GenerationType
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsNativeGenerator

class GrailsNativeGeneratorSpec extends HibernateGormDatastoreSpec {

    def "should return currentValue if not null (assigned identifier)"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def database = Mock(org.hibernate.boot.model.relational.Database)
        context.getDatabase() >> database
        database.getDialect() >> getGrailsDomainBinder().getJdbcEnvironment().getDialect()
        
        def session = Mock(SharedSessionContractImplementor)
        def entity = new Object()
        def currentValue = 123L
        def eventType = EventType.INSERT
        
        @Subject
        def generator = Spy(GrailsNativeGenerator, constructorArgs: [context])

        when:
        def result = generator.generate(session, entity, currentValue, eventType)

        then:
        result == currentValue
    }

    def "should return null if generation type is IDENTITY"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def database = Mock(org.hibernate.boot.model.relational.Database)
        context.getDatabase() >> database
        database.getDialect() >> getGrailsDomainBinder().getJdbcEnvironment().getDialect()
        
        def session = Mock(SharedSessionContractImplementor)
        def entity = new Object()
        def eventType = EventType.INSERT
        
        @Subject
        def generator = Spy(GrailsNativeGenerator, constructorArgs: [context])
        generator.getGenerationType() >> GenerationType.IDENTITY

        when:
        def result = generator.generate(session, entity, null, eventType)

        then:
        result == null
    }
}
