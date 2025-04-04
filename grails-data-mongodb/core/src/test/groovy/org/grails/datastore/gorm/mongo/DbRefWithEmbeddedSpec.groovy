package org.grails.datastore.gorm.mongo

import com.mongodb.DBRef
import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.Document
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class DbRefWithEmbeddedSpec extends GormDatastoreSpec {

    @Issue('GPMONGODB-260')
    void "Test that an embedded links to the correct collection when using dbrefs"() {
        when:""
            def one = new One(name: 'My Foo')
            one.save(flush: true)

            def two = new Two()
            two.link2one = new Link2One(link: one)
            two.save(flush: true)
            session.clear()
            final link2one = Two.collection.find().first().link2one?.link
        then:""
            link2one instanceof DBRef
            Two.DB.getCollection(link2one.collectionName).find(new Document('_id', link2one.id)).first().name == "My Foo"

        when:"The entity is loaded again"
            two = Two.first()

        then:"It is correct"
            two.link2one.link.name == 'My Foo'

    }

    @Override
    List getDomainClasses() {
        [One,Two]
    }
}
@Entity
class One {
    ObjectId id
    String name
    static mapping = {
        version false
    }
}
@Entity
class Two {
    ObjectId id
    Link2One link2one
    static embedded = ['link2one']
    static mapping = {
        version false
    }
}
class Link2One {
    One link
    static mapping = {
        version false
        link reference: true
    }
}
