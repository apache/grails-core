package org.grails.datastore.gorm.model

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import spock.lang.Specification

/**
 * Created by graemerocher on 03/11/16.
 */
class TransientInheritanceSpec extends Specification {

    void "test inherit transient config from abstract non-entity parent"() {
        given:"A mapping context"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        PersistentEntity entity = mappingContext.addPersistentEntity(Child)

        expect:
        entity.persistentPropertyNames.sort() == ['one', 'foo'].sort()
    }

    static abstract class Parent {
        String foo
        String bar
        static transients = ['bar']
    }

    @Entity
    static class Child extends Parent{

        String one
        String two
        static transients = ['two']
    }
}
