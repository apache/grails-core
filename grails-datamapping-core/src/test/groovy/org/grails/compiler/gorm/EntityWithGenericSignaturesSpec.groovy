package org.grails.compiler.gorm

import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValuePersistentEntity
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import spock.lang.Specification

/**
 * Created by graemerocher on 16/09/2016.
 */
class EntityWithGenericSignaturesSpec extends Specification {

    void "Test compile entity with generic signatures"() {

        when:"An entity with generic signatures is compiled"
        def gcl = new GroovyClassLoader()
        Class cls = gcl.parseClass('''
package test

import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck

@Entity
class Widget<SettingType extends WidgetSetting> {
    SettingType setting

}

@Entity
abstract class WidgetSetting {
     String name
}

@Entity
class HotWidgetSetting extends WidgetSetting {
    Integer temperature
}
''')

        then:"The entity is compiled correctly"
        cls.name == 'test.Widget'
        cls.getMethod("getSetting").returnType.name == 'test.WidgetSetting'


        when:"A mapping context is created"
        MappingContext mappingContext = new KeyValueMappingContext("test")
        PersistentEntity entity = mappingContext.addPersistentEntity(cls)

        then:"The entity is has a setting property"
        entity.getPropertyByName("setting")
        entity.getPropertyByName("setting")
        entity.getPropertyByName("setting").type.name == 'test.WidgetSetting'
    }
}
