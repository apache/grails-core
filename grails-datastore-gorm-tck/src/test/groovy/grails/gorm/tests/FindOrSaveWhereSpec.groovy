package grails.gorm.tests

class FindOrSaveWhereSpec extends GormDatastoreSpec {

    def "Test findOrSaveWhere returns a new instance if it doesn't exist in the database"() {
        when:
            def entity = TestEntity.findOrSaveWhere(name: 'Lake', age: 63)

        then:
            'Lake' == entity.name
            63 == entity.age
            null != entity.id
    }

    def "Test findOrSaveWhere returns a persistent instance if it exists in the database"() {
        given:
            def entityId = new TestEntity(name: 'Levin', age: 64).save().id

        when:
            def entity = TestEntity.findOrSaveWhere(name: 'Levin', age: 64)

        then:
            entity.id != null
            entityId == entity.id
            'Levin' == entity.name
            64 == entity.age
    }
}
