package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.tck.TestEntity

/**
 * Created by graemerocher on 22/08/2017.
 */
class NotLikeSpec extends GormDatastoreSpec {

    def setup() {
        System.out.println("********************************************************")
        System.out.println(System.getProperty("java.class.path"))
        System.out.println("********************************************************")
    }

    void "test not like"() {
        when:
        new TestEntity(name:"Fred").save()
        new TestEntity(name:"Frank").save()
        new TestEntity(name:"Jack").save(flush:true)

        then:
        TestEntity.countByNameNotLike("F%") == 1
        TestEntity.findByNameNotLike("F%").name == "Jack"
        TestEntity.findAllByNameNotLike("J%").size() == 2
    }
}

