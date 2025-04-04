package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class GetAllWithStringIdSpec extends GormDatastoreSpec {


    @Issue('GPMONGODB-278')
    void "Test that getAll returns the correct items"() {

        when:"domains with String ids are saved"
            def id1 = new GetItem( title: "Item 1" ).save(flush:true,failOnError:true).id
            def id2 = new GetItem( title: "Item 2" ).save(flush:true,failOnError:true).id
            def id3 = new GetItem( title: "Item 3" ).save(flush:true,failOnError:true).id
            def id4 = new GetItem( title: "Item 4" ).save(flush:true,failOnError:true).id
            def id5 = new GetItem( title: "Item 5" ).save(flush:true,failOnError:true).id
            def id6 = new GetItem( title: "Item 6" ).save(flush:true,failOnError:true).id
            def id7 = new GetItem( title: "Item 7" ).save(flush:true,failOnError:true).id
            def id8 = new GetItem( title: "Item 8" ).save(flush:true,failOnError:true).id

        then:"The ids are strings and can be queried"
            id5.class == String.class
            id6.class == String.class
            id7.class == String.class
            GetItem.get(id5).id
            GetItem.get(id6).id
            GetItem.get(id7).id
            GetItem.findAllById(id5).id
            GetItem.findAllByIdInList([id5,id6,id7]).size() == 3
            GetItem.getAll(id5).size() == 1
            GetItem.getAll(id5,id6,id7).size() == 3
            GetItem.getAll([id5,id6,id7]).size() == 3

    }

    @Override
    List getDomainClasses() {
        [GetItem]
    }
}
@Entity
class GetItem {
    String id
    String title

    static constraints = {
    }
}
