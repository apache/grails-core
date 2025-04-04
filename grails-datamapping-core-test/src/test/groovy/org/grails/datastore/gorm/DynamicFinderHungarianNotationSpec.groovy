package org.grails.datastore.gorm

import grails.gorm.tck.ClassWithHungarianNotation
import grails.gorm.tests.GormDatastoreSpec

/**
 * Created by sdelamo on 12/10/2017.
 */
class DynamicFinderHungarianNotationSpec extends GormDatastoreSpec {

    void "test dynamic finder of properties with hungarian notation"() {
        when:
        new ClassWithHungarianNotation(iSize: 2).save()

        then:
        ClassWithHungarianNotation.countByISize(2) == 1
        ClassWithHungarianNotation.findByISize(2).iSize == 2
    }

    List getDomainClasses() {
        [ClassWithHungarianNotation]
    }
}