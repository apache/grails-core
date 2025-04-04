package org.grails.datastore.gorm

import grails.gorm.tck.ClassWithHungarianNotation
import grails.gorm.tests.GormDatastoreSpec

/**
 * Created by sdelamo on 12/10/2017.
 */
class ListOrderByHungarianNotationSpec extends GormDatastoreSpec {

    void "test dynamic finder of properties with hungarian notation"() {
        when:
        new ClassWithHungarianNotation(iSize: 2).save()
        new ClassWithHungarianNotation(iSize: 3).save()

        then:
        ClassWithHungarianNotation.listOrderByISize(order: 'desc')*.iSize == [3, 2]
    }

    List getDomainClasses() {
        [ClassWithHungarianNotation]
    }
}
