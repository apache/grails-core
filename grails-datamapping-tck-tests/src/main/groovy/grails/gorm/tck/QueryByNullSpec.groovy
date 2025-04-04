package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.Person

class QueryByNullSpec extends GormDatastoreSpec {

    void 'Test passing null as the sole argument to a dynamic finder multiple times'() {
        // see GRAILS-3463
        when:
            def people = Person.findAllByLastName(null)

        then:
            !people

        when:
            people - Person.findAllByLastName(null)

       then:
            !people
    }
}
