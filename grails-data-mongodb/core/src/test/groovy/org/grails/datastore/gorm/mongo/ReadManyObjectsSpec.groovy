package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.Document
import org.bson.types.ObjectId

/**
 * @author Graeme Rocher
 */
class ReadManyObjectsSpec extends GormDatastoreSpec {

    void "Test that reading thousands of objects doesn't run out of memory"() {
        given:"A lot of test data"
            createData()

        when:"The data is read"
            long took = 30000
            final now = System.currentTimeMillis()
            for(p in ProfileDoc.list()) {
                println p.n1
            }
            final then = System.currentTimeMillis()
            took = then-now
            println "Took ${then-now}ms"

        then:"Check that it doesn't take too long"
            took < 30000

    }

    void "Test that reading thousands of objects doesn't run out of memory native query"() {
        given:"A lot of test data"
        createData()

        when:"The data is read"
            final now = System.currentTimeMillis()
            final cursor = ProfileDoc.collection.find()
            for(p in cursor) {
                println p.n1
            }
            final then = System.currentTimeMillis()
            long took = then-now
            println "Took ${then-now}ms"

        then:"If it gets to this point we "
            took < 30000

    }

    void createData() {
        ProfileDoc.collection.drop()
        100000.times {
            ProfileDoc.collection.insertOne(new Document(n1:"Plane $it".toString(),n2:it,n3:it.toLong(), date: new Date()))
        }
    }

    @Override
    List getDomainClasses() {
        [ProfileDoc]
    }
}

@Entity
class ProfileDoc {
    ObjectId id
    String n1
    Integer n2
    Long n3
    Date date

    static mapping = {
        stateless true
    }
}
