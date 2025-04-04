package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId
import spock.lang.Ignore

/**
 * Created by graemerocher on 22/04/16.
 */
class EmbeddedSetAssignedIdSpec extends GormDatastoreSpec {

    void "Test saved nested embedded association graph"() {
        when:"an object graph is created with nested items"
        new Itemized(name: "i1")
                .addToLineItems(new JobItem(teamSize: 10)
                                        .addToSubItems(name: "s1"))
                .save(flush:true)

        session.clear()
        Itemized i = Itemized.first()

        then:"The object graph is correct"

        i.name == 'i1'
        i.lineItems.size() == 1
        i.lineItems.first() instanceof JobItem
        i.lineItems.first().teamSize == 10
        i.lineItems.first().subItems.size() == 1
        i.lineItems.first().subItems.first().name == 's1'
    }

    void "Test update nested embedded association graph"() {
        when:"an object graph is created with nested items"
        new Itemized(name: "i1").save(flush:true)
        session.clear()

        Itemized i = Itemized.first()
        i.addToLineItems(new JobItem(teamSize: 10)
         .addToSubItems(name: "s1"))
         .save(flush:true)

        session.clear()
        i = Itemized.first()

        then:"The object graph is correct"

        i.name == 'i1'
        i.lineItems.size() == 1
        i.lineItems.first() instanceof JobItem
        i.lineItems.first().teamSize == 10
        i.lineItems.first().subItems.size() == 1
        i.lineItems.first().subItems.first().name == 's1'
    }

    void "Test update nested embedded association graph with assigned id"() {
        when:"an object graph is created with nested items"
        new Itemized(name: "i1").save(flush:true)
        session.clear()

        Itemized i = Itemized.first()
        i.addToLineItems(new JobItem(id: new ObjectId(), teamSize: 10)
                .addToSubItems(name: "s1"))
                .save(flush:true)

        session.clear()
        i = Itemized.first()

        then:"The object graph is correct"

        i.name == 'i1'
        i.lineItems.size() == 1
        i.lineItems.first().id
        i.lineItems.first() instanceof JobItem
        i.lineItems.first().teamSize == 10
        i.lineItems.first().subItems.size() == 1
        i.lineItems.first().subItems.first().name == 's1'
    }

    void "Test update nested embedded association graph with assigned id using direct collection modification"() {
        when:"an object graph is created with nested items"
        new Itemized(name: "i1").save(flush:true)
        session.clear()

        Itemized i = Itemized.first()
        i.lineItems.add(new JobItem(id: new ObjectId(), teamSize: 10)
                            .addToSubItems(name: "s1"))


        i.save(flush:true)

        session.clear()
        i = Itemized.first()

        then:"The object graph is correct"

        i.name == 'i1'
        i.lineItems.size() == 1
        i.lineItems.first().id
        i.lineItems.first() instanceof JobItem
        i.lineItems.first().teamSize == 10
        i.lineItems.first().subItems.size() == 1
        i.lineItems.first().subItems.first().name == 's1'
    }

    void "Test update nested embedded association graph with assigned id by assigning a new collection"() {
        when:"an object graph is created with nested items"
        new Itemized(name: "i1").save(flush:true)
        session.clear()

        Itemized i = Itemized.first()
        i.lineItems = [ new JobItem(id: new ObjectId(), teamSize: 10)
                                        .addToSubItems(name: "s1") ]


        i.save(flush:true)

        session.clear()
        i = Itemized.first()

        then:"The object graph is correct"

        i.name == 'i1'
        i.lineItems.size() == 1
        i.lineItems.first().id
        i.lineItems.first() instanceof JobItem
        i.lineItems.first().teamSize == 10
        i.lineItems.first().subItems.size() == 1
        i.lineItems.first().subItems.first().name == 's1'
    }

    @Ignore
    void "Test update nested embedded association graph using a custom method defined on the domain instance"() {
        when:"an object graph is created with nested items"
        new Itemized(name: "i1").save(flush:true)
        session.clear()

        Itemized i = Itemized.first()
        i.addLineItem( new JobItem(id: new ObjectId(), teamSize: 10)
                                .addToSubItems(name: "s1") )


        i.save(flush:true)

        session.clear()
        i = Itemized.first()

        then:"The object graph is correct"

        i.name == 'i1'
        i.lineItems.size() == 1
        i.lineItems.first().id
        i.lineItems.first() instanceof JobItem
        i.lineItems.first().teamSize == 10
        i.lineItems.first().subItems.size() == 1
        i.lineItems.first().subItems.first().name == 's1'
    }
    @Override
    List getDomainClasses() {
        [Itemized, LineItem, SubItem, JobItem]
    }
}

@Entity
class Itemized {

    ObjectId id //Mongo Compatible ID Generation

    String name

    Date dateCreated
    Date lastUpdated

    Set lineItems = []
    static hasMany = [lineItems: LineItem]

    static embedded = ['lineItems']

    def addLineItem(LineItem lineItem) {
        if (!lineItems) { lineItems = [] }
        lineItems.add(lineItem)
    }
}

@Entity
class LineItem {

    ObjectId id

    Date dateCreated
    Date lastUpdated

    static hasMany = [subItems: SubItem]

    static embedded = ['subItems']
}

@Entity
class JobItem extends LineItem {

    Integer teamSize

    static constraints = {
    }
}


@Entity
class SubItem {

    ObjectId id

    String name

    Date dateCreated
    Date lastUpdated

    static constraints = {
    }
}
