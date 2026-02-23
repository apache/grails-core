package org.grails.orm.hibernate.cfg.domainbinding.generator

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.GeneratorCreationContext
import spock.lang.Subject

class GrailsSequenceWrapperSpec extends HibernateGormDatastoreSpec {

    @Subject
    GrailsSequenceWrapper wrapper = new GrailsSequenceWrapper()

    def "should delegate to GrailsSequenceGeneratorEnum"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def mappedId = Mock(Identity)
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def jdbcEnvironment = Mock(JdbcEnvironment)
        def namingStrategy = Mock(PersistentEntityNamingStrategy)

        // Setup minimal mocks for assigned generator which is simple to instantiate
        context.getProperty() >> null 

        when:
        def generator = wrapper.getGenerator("assigned", context, mappedId, domainClass, jdbcEnvironment, namingStrategy)

        then:
        generator instanceof org.hibernate.generator.Assigned
    }
}
