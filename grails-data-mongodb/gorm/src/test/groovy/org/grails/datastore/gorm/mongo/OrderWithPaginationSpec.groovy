package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tests.Plant
import spock.lang.Issue

/**
 * Created by graemerocher on 14/03/14.
 */
class OrderWithPaginationSpec extends GormDatastoreSpec {

    @Issue('GPMONGODB-241')
    void "Test that a criteria query with pagination parameters works correctly"() {
        given:"Some test data"
            new Plant(name: "Lettuce").save()
            new Plant(name: "Eggplant").save()
            new Plant(name: "Cabbage").save()
            new Plant(name: "Kiwi").save()
            new Plant(name: "Tomato").save(flush:true)
            session.clear()

        when:"A criteria query with pagination parameters is used"
            def c = Plant.createCriteria()
            def results = c.list(max:2, offset:0) {
                eq 'goesInPatch', false
                order 'name'
            }

        then:"The results are correct"
            results.size() == 2
            results[0].name == 'Cabbage'
            results[1].name == 'Eggplant'

    }
}
