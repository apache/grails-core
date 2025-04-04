package grails.gorm.tests

import grails.gorm.tck.TestEntity
import spock.lang.Issue

/**
 * Created by graemerocher on 16/02/2017.
 */
class SingleResultSpec extends GormDatastoreSpec {

    @Issue('https://github.com/grails/grails-data-mapping/issues/872')
    void "test single result state"() {
        when:
        def query = session.createQuery(TestEntity)

        then:
        query.uniqueResult == false

        when:
        def result = query.singleResult()
        then:
        query.uniqueResult == true
    }
}
