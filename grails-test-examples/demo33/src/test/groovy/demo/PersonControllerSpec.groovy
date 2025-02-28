package demo

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class PersonControllerSpec extends Specification implements ControllerUnitTest<PersonController>, DataTest {

    void setupSpec() {
        mockDomain Person
    }

    void "test action which invokes GORM method"() {
        setup:
        new Person(firstName: 'Robert', lastName: 'Fripp').save()
        new Person(firstName: 'Adrian', lastName: 'Belew').save()

        when:
        def model = controller.index()

        then:
        model.people.size() == 2
        model.keySet().contains('people')
    }
}
