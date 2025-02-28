package demo

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Issue
import spock.lang.Specification

@Issue('grails/grails-testing-support#18')
class PersonServiceSpec extends Specification implements ServiceUnitTest<PersonService>, DataTest {

    void setupSpec() {
        mockDomain Person
    }

    void 'test one'() {
        expect:
        service.list().size() == 0
    }

    void 'test two'() {
        expect:
        service.list().size() == 0
    }
}
