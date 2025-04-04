package org.grails.datastore.mapping

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

/**
 * Created by Jim on 8/19/2016.
 */
class EntityReflectorSpec extends GormDatastoreSpec {

    void "test getAssociationId with a null association"() {
        when:
        LibraryBook book = new LibraryBook()
        Serializable id = book.getAssociationId("library")

        then:
        noExceptionThrown()
        id == null
    }

    @Override
    List getDomainClasses() {
        [Library, LibraryBook]
    }
}

@Entity
class Library {
    Long id
}

@Entity
class LibraryBook {
    Long id
    Library library
}
