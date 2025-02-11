package grails.gorm.specs

import grails.gorm.tests.TestEntity

class ReadOperationSpec extends HibernateGormDatastoreSpec {

    void "test read operation for non existent"() {
        expect:
        TestEntity.read() == null
    }

    void "test read operation"() {
        given:
        TestEntity te = new TestEntity(name: "bob")
        te.save(flush:true)

        expect:
        TestEntity.count() == 1
        TestEntity.read(te.id) != null
        TestEntity.exists(te.id)
        !TestEntity.exists(10)
    }

    @Override
    List getDomainClasses() {
        [TestEntity]
    }
}
