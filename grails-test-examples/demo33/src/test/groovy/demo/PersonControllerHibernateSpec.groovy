package demo

import grails.test.hibernate.HibernateSpec
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Ignore

class PersonControllerHibernateSpec extends HibernateSpec implements ControllerUnitTest<PersonController> {

    @Ignore('Either class [demo.Person] is not a domain class or GORM has not been initialized correctly or has already been shutdown. Ensure GORM is loaded and configured correctly before calling any methods on a GORM entity.')
    void "test action which invokes GORM method"() {

        setup:
        new Person(firstName: 'Robert', lastName: 'Fripp').save()
        new Person(firstName: 'Adrian', lastName: 'Belew').save()

        when:
        def model = controller.index()

        then:
        model.people.size() == 2
    }
}
