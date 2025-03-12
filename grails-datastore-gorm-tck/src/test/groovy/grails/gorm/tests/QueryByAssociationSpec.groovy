package grails.gorm.tests

/**
 * Abstract base test for query associations. Subclasses should do the necessary setup to configure GORM
 */
class QueryByAssociationSpec extends GormDatastoreSpec {

    void "Test query entity by single-ended association"() {
        given:
            def age = 40
            ["Bob", "Fred", "Barney", "Frank"].each { new TestEntity(name:it, age: age++, child:new ChildEntity(name:"$it Child")).save() }

        when:
            def child = ChildEntity.findByName("Barney Child")

        then:
            child != null
            child.id != null

        when:
            def t = TestEntity.findByChild(child)

        then:
            t != null
            "Barney" == t.name
    }
}
