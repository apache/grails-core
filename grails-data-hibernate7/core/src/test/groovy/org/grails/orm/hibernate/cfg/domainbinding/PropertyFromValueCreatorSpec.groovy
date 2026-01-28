package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import org.grails.datastore.mapping.model.PersistentProperty
import spock.lang.Specification

class PropertyFromValueCreatorSpec extends Specification {

    def "should create a property from a value"() {
        given:
        def propertyBinder = Mock(PropertyBinder)
        def creator = new PropertyFromValueCreator(propertyBinder)
        
        def value = Mock(Value)
        def grailsProperty = Mock(PersistentProperty)
        def table = new Table("my_table")

        grailsProperty.getOwnerClassName() >> "com.example.MyEntity"
        grailsProperty.getName() >> "myProp"
        value.getTable() >> table

        when:
        Property prop = creator.createProperty(value, grailsProperty)

        then:
        1 * value.setTypeUsingReflection("com.example.MyEntity", "myProp")
        1 * value.createForeignKey()
        1 * propertyBinder.bindProperty(grailsProperty, _ as Property)
        prop.getValue() == value
    }

    def "should create a property without foreign key when table is null"() {
        given:
        def propertyBinder = Mock(PropertyBinder)
        def creator = new PropertyFromValueCreator(propertyBinder)
        
        def value = Mock(Value)
        def grailsProperty = Mock(PersistentProperty)

        grailsProperty.getOwnerClassName() >> "com.example.MyEntity"
        grailsProperty.getName() >> "myProp"
        value.getTable() >> null

        when:
        Property prop = creator.createProperty(value, grailsProperty)

        then:
        1 * value.setTypeUsingReflection("com.example.MyEntity", "myProp")
        0 * value.createForeignKey()
        1 * propertyBinder.bindProperty(grailsProperty, _ as Property)
        prop.getValue() == value
    }
}