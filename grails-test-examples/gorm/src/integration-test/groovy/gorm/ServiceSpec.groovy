package gorm

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.domain.DomainClassUnitTestMixin} for usage instructions
 */
@Integration(applicationClass = Application)
@Rollback
class ServiceSpec extends Specification {

    void "test persisting a domain named Service"() {
        when:"A new service is created"
        def s = new Service()
        Service.gormPersistentEntity.persistentProperties

        then:"It is valid"
        s.validate()
        !s.hasErrors()

        when:"When it is persisted"
        s.save(flush:true)

        then:"The object is saved correctly"
        Service.count() == 1
    }
}
