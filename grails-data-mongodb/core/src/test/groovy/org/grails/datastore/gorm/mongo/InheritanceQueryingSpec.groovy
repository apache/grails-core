package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.Document

class InheritanceQueryingSpec extends GormDatastoreSpec {

    def cleanup() {
        A.get("id")?.delete(flush:true)
        B.get("id")?.delete(flush:true)
        C.get("childId")?.delete(flush: true)
    }

    def setup() {
        B b = new B()
        b.id = "id"
        b.prop = "value"
        b.save(failOnError:true, flush:true)

        C c = new C()
        c.id = "childId"
        c.prop = "childValue"
        c.name = "childName"
        c.save(failOnError:true, flush:true)
    }

    def "Test collection and count sizes"() {
        expect: "Collections to have 2 documents"
        C.collection.countDocuments()==2
        B.collection.countDocuments()==2
        A.collection.countDocuments()==2

        and: "A/B have 2, C has 1"
        C.count()==1
        B.count()==2
        A.count()==2
    }

    def "Test listing"() {
        when: "B has them in the list"
        def bList = B.list()

        then:
        bList.size()==2
        bList[0].id=="id"
        bList[1].id=="childId"

        when:
        def aList = A.list()

        then:
        aList.size()==2
        aList[0].id=="id"
        aList[1].id=="childId"

        when:
        def cList = C.list()

        then:
        cList.size()==1
        cList[0].id=="childId"
    }

    def "Test getting"() {
        when:
        B b = B.get("id")

        then:
        b?.id=="id"
        b.prop=="value"

        when:
        def a = A.get("id")

        then:
        a.id=="id"
        a instanceof B
        a.hasProperty("prop")

        when:
        b = B.get("childId")

        then:
        b.id=="childId"
        b instanceof C
        b.prop=="childValue"
        b.hasProperty("name")

        when:
        a = A.get("childId")

        then:
        a.id=="childId"
        a instanceof C
        a.hasProperty("prop")
        a.hasProperty("name")

        when:
        def c = C.get("childId")

        then:
        c.id=="childId"
        c.prop=="childValue"
        c.name=="childName"
    }

    def "Access prop through mongo"() {
        when:
        def jsonString = (A.collection.find(new Document(_id:"id")).first()).toString()

        then:
        jsonString.contains("id")
        jsonString.contains("prop")

        when:
        jsonString = (B.collection.find(new Document(_id:"id")).first()).toString()

        then:
        jsonString.contains("id")
        jsonString.contains("prop")

        when:
        jsonString = (B.collection.find(new Document(_id:"childId")).first()).toString()

        then:
        jsonString.contains("id")
        jsonString.contains("prop")
        jsonString.contains("name")

    }

    @Override
    List getDomainClasses() {
        [A,B,C]
    }
}

@Entity
class A {
    static mapWith = "mongo"

    static mapping = {
        id generator:'assigned', name:'id', type:'string'
    }
    String id
}

@Entity
class B extends A {
    static mapWith = "mongo"

    static constraints = {
    }
    String prop
}

@Entity
class C extends B {
    static mapWith = "mongo"

    static constraints = {
    }
    String name
}