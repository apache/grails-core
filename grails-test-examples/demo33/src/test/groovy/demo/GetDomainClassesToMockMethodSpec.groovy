package demo

import grails.testing.gorm.DataTest
import spock.lang.Specification

class GetDomainClassesToMockMethodSpec extends Specification implements DataTest {

    Class[] getDomainClassesToMock() {
        Person
    }

    void "test basic persistence mocking"() {
        setup:
        new Person(firstName: 'Robert', lastName: 'Fripp').save()
        new Person(firstName: 'Adrian', lastName: 'Belew').save()

        expect:
        Person.count() == 2
    }
}
