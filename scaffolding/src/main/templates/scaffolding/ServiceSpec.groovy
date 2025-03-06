<%=packageName ? "package ${packageName}" : ''%>

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import org.hibernate.SessionFactory

@Integration
@Rollback
class ${className}ServiceSpec extends Specification {

    ${className}Service ${propertyName}Service
    SessionFactory sessionFactory

    private Long setupData() {
        // TODO: Populate valid domain instances and return a valid ID
        //new ${className}(...).save(flush: true, failOnError: true)
        //new ${className}(...).save(flush: true, failOnError: true)
        //${className} ${propertyName} = new ${className}(...).save(flush: true, failOnError: true)
        //new ${className}(...).save(flush: true, failOnError: true)
        //new ${className}(...).save(flush: true, failOnError: true)
        assert false, "TODO: Provide a setupData() implementation for this generated test suite"
        //${propertyName}.id
    }

    void "test get"() {
        setupData()

        expect:
        ${propertyName}Service.get(1) != null
    }

    void "test list"() {
        setupData()

        when:
        List<${className}> ${propertyName}List = ${propertyName}Service.list(max: 2, offset: 2)

        then:
        ${propertyName}List.size() == 2
        assert false, "TODO: Verify the correct instances are returned"
    }

    void "test count"() {
        setupData()

        expect:
        ${propertyName}Service.count() == 5
    }

    void "test delete"() {
        Long ${propertyName}Id = setupData()

        expect:
        ${propertyName}Service.count() == 5

        when:
        ${propertyName}Service.delete(${propertyName}Id)
        sessionFactory.currentSession.flush()

        then:
        ${propertyName}Service.count() == 4
    }

    void "test save"() {
        when:
        assert false, "TODO: Provide a valid instance to save"
        ${className} ${propertyName} = new ${className}()
        ${propertyName}Service.save(${propertyName})

        then:
        ${propertyName}.id != null
    }
}
