package grails.gorm.tests

class ReadOperationSpec extends GormDatastoreSpec {

    void "test read operation for non existent"() {
        expect:
        TestEntity.read(10) == null
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
