package grails.gorm.tests

class FindOrCreateWhereSpec extends GormDatastoreSpec {

    def "Test findOrCreateWhere returns a new instance if it doesn't exist in the database"() {
        when:
            def entity = TestEntity.findOrCreateWhere(name: 'Fripp', age: 64)

        then:
            'Fripp' == entity.name
            64 == entity.age
            null == entity.id
    }

    def "Test findOrCreateWhere returns a persistent instance if it exists in the database"() {
        given:
            def entityId = new TestEntity(name: 'Belew', age: 61).save().id

        when:
            def entity = TestEntity.findOrCreateWhere(name: 'Belew', age: 61)

        then:
            entity.id != null
            entityId == entity.id
            'Belew' == entity.name
            61 == entity.age
    }
}
