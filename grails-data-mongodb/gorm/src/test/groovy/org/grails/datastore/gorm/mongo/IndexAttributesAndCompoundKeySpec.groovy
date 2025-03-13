package org.grails.datastore.gorm.mongo

import com.mongodb.WriteConcern
import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * Created by graemerocher on 25/03/14.
 */
class IndexAttributesAndCompoundKeySpec extends GormDatastoreSpec{

    @Issue('GPMONGODB-359')
    void "Test that a compound index works"() {
        expect:"No exceptions on startup"
            ServerStream.count() == 0

            ServerStream.collection.listIndexes()[1].key == [server:1, stream:1]
            ServerStream.collection.listIndexes()[1].unique

    }
    @Override
    List getDomainClasses() {
        [ServerStream]
    }
}


@Entity
class ServerStream {
    ObjectId id
    Long version
    String server
    String stream
    Boolean fBackfill=false

    static mapping = {
        version false
        compoundIndex server :1, stream:1, indexAttributes:[unique:true, dropDups:true]
        writeConcern WriteConcern.ACKNOWLEDGED
    }
}