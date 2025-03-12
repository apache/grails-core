package grails.gorm.tests

/**
 * Created by graemerocher on 06/03/2017.
 */
class NotInListSpec extends GormDatastoreSpec {

    void "test not in list returns the correct results"() {
        when:
        new TestEntity(name:"Fred").save()
        new TestEntity(name:"Bob").save()
        new TestEntity(name:"Jack").save(flush:true)

        then:
        TestEntity.countByNameNotInList(['Fred', "Bob"]) == 1
        TestEntity.findByNameNotInList(['Fred', "Bob"]).name == "Jack"
    }
}
