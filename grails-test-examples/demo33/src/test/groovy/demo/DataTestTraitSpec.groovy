package demo

import grails.testing.gorm.DataTest
import spock.lang.Specification

class DataTestTraitSpec extends Specification implements DataTest {

    void setupSpec() {
        mockDomain Person

        // for multiple domains, call mockDomains...
        // mockDomains Person, Address, Company
    }

    void "test basic persistence mocking"() {
        setup:
        new Person(firstName: 'Robert', lastName: 'Fripp').save()
        new Person(firstName: 'Adrian', lastName: 'Belew').save()

        expect:
        Person.count() == 2
    }
}
