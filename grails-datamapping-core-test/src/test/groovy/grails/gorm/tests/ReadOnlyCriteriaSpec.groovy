package grails.gorm.tests

import spock.lang.Issue
import grails.gorm.tck.TestEntity

class ReadOnlyCriteriaSpec extends GormDatastoreSpec {

    @Issue('GRAILS-11670')
    void 'Test invoking readOnly in a criteria query'() {
        when:
        def results = TestEntity.withCriteria {
            readOnly true
        }

        then:
        results == []
    }
}
