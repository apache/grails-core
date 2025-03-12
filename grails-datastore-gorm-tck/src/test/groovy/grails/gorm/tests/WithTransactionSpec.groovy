package grails.gorm.tests

import groovy.transform.InheritConstructors

/**
 * Transaction tests.
 */
class WithTransactionSpec extends GormDatastoreSpec {

    void "Test save() with transaction"() {
        given:
            TestEntity.withTransaction {
                new TestEntity(name:"Bob", age:50, child:new ChildEntity(name:"Bob Child")).save()
                new TestEntity(name:"Fred", age:45, child:new ChildEntity(name:"Fred Child")).save()
            }

        when:
            int count = TestEntity.count()
//            def results = TestEntity.list(sort:"name") // TODO this fails but doesn't appear to be tx-related, so manually sorting
            def results = TestEntity.list().sort { it.name }

        then:
            2 == count
            "Bob" == results[0].name
            "Fred" == results[1].name
    }

    void "Test rollback transaction"() {
        given:
            TestEntity.withNewTransaction { status ->
                new TestEntity(name:"Bob", age:50, child:new ChildEntity(name:"Bob Child")).save()
                status.setRollbackOnly()
                new TestEntity(name:"Fred", age:45, child:new ChildEntity(name:"Fred Child")).save()
            }

        when:
            int count = TestEntity.count()
            def results = TestEntity.list()

        then:
            count == 0
            results.size() == 0
    }

    void "Test rollback transaction with Runtime Exception"() {
        given:
            def ex
            try {
                TestEntity.withNewTransaction { status ->
                    new TestEntity(name:"Bob", age:50, child:new ChildEntity(name:"Bob Child")).save()
                    throw new RuntimeException("bad")
                }
            }
            catch (e) {
                ex = e
            }

        when:
            int count = TestEntity.count()
            def results = TestEntity.list()

        then:
            count == 0
            results.size() == 0
            ex instanceof RuntimeException
            ex.message == 'bad'
    }

    void "Test rollback transaction with Exception"() {
        given:
            def ex
            try {
                TestEntity.withNewTransaction { status ->
                    new TestEntity(name:"Bob", age:50, child:new ChildEntity(name:"Bob Child")).save()
                    throw new TestCheckedException("bad")
                }
            }
            catch (e) {
                ex = e
            }

        when:
            int count = TestEntity.count()
            def results = TestEntity.list()

        then:
            count == 0
            results.size() == 0
            ex instanceof TestCheckedException
            ex.message == 'bad'
    }
}

@InheritConstructors
class TestCheckedException extends Exception {}
