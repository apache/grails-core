package demo

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class CarSpec extends Specification implements DomainUnitTest<Car> {

    def setup() {
    }

    def cleanup() {
    }

    void "test basic persistence mocking"() {
        setup:
        new Car(name: 'grails car', color: 'green').save()
        new Car(name: 'gorm car').save()

        expect:
        Car.count() == 1
    }
}
