package org.grails.datastore.gorm

import grails.gorm.tests.TestEntity
import grails.gorm.tests.GormDatastoreSpec

class GormValidateableSpec extends GormDatastoreSpec {

    void 'Test that a class marked with @Entity implements GormValidateable'() {
        expect:
        GormValidateable.isAssignableFrom TestEntity
    }

    void 'Test that a real validate method exists, not a runtime added method'() {
        expect:
        TestEntity.getDeclaredMethod 'validate', [] as Class[]
        TestEntity.getDeclaredMethod 'validate', [List] as Class[]
        TestEntity.getDeclaredMethod 'validate', [Map] as Class[]
    }
}
