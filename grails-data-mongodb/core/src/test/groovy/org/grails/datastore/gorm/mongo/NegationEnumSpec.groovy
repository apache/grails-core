package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId

class NegationEnumSpec extends GormDatastoreSpec {

    void "Test negate with enum query"() {
        given: "two domains"
        new HasEnum(bookType: BookType.GOOD).save()
        new HasEnum(bookType: BookType.BAD).save(flush: true)

        when: "We query for not enum equals"
        def results = HasEnum.withCriteria {
            not {
                eq('bookType', BookType.BAD)
            }
        }

        then: "The results are correct"
        results.size() == 1
        results[0].bookType == BookType.GOOD

    }

    @Override
    List getDomainClasses() {
        [HasEnum]
    }
}

enum BookType {
    GOOD, BAD
}

@Entity
class HasEnum {
    ObjectId id
    BookType bookType
}