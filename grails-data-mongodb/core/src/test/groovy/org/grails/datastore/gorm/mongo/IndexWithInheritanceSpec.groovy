package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity

/**
 * Created by graemerocher on 24/08/2016.
 */
class IndexWithInheritanceSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Lion, Mammal]
    }

    void "Test collection indexes"() {
        expect:
        Mammal.DB.listCollectionNames().toList().contains('mammal')
        !Mammal.DB.listCollectionNames().toList().contains('lion')
    }

    def cleanup() {
        Mammal.DB.drop()
    }
}
@Entity
class Mammal implements MongoEntity<Mammal> {
    static mapping = {
        index( [ "_class": 1] )
    }
}
@Entity
class Lion extends Mammal implements MongoEntity<Lion> {
}