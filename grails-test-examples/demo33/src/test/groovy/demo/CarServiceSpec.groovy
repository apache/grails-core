package demo

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class CarServiceSpec extends Specification implements ServiceUnitTest<CarService>, DataTest{

    void setupSpec() {
        mockDomain Car
    }

    void setup(){
        new Car(name: 'grails car', color: 'green').save(flush:true)
        new Car(name: 'gorm car').save(flush:true)
    }

    void 'test one'() {
        expect:
        service.list().size() == 1
    }
}
