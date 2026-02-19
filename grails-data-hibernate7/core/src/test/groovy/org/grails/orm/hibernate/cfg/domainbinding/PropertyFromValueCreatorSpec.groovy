package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator

class PropertyFromValueCreatorSpec extends Specification {

    def "should create a property from a value"() {
        given:
        def propertyBinder = Mock(PropertyBinder)
        def creator = new PropertyFromValueCreator(propertyBinder)
        
        def value = Mock(Value)
        def grailsProperty = Mock(GrailsHibernatePersistentProperty)
        def table = new Table("my_table")

        grailsProperty.getOwnerClassName() >> "com.example.MyEntity"
        grailsProperty.getName() >> "myProp"
        value.getTable() >> table
        propertyBinder.bindProperty(grailsProperty, value) >> { 
            def p = new Property()
            p.setValue(value)
            return p
        }

        when:
        Property prop = creator.createProperty(value, grailsProperty)

        then:
        1 * value.setTypeUsingReflection("com.example.MyEntity", "myProp")
        1 * value.createForeignKey()
        prop.getValue() == value
    }

    def "should create a property without foreign key when table is null"() {
        given:
        def propertyBinder = Mock(PropertyBinder)
        def creator = new PropertyFromValueCreator(propertyBinder)
        
        def value = Mock(Value)
        def grailsProperty = Mock(GrailsHibernatePersistentProperty)

        grailsProperty.getOwnerClassName() >> "com.example.MyEntity"
        grailsProperty.getName() >> "myProp"
        value.getTable() >> null
        propertyBinder.bindProperty(grailsProperty, value) >> {
            def p = new Property()
            p.setValue(value)
            return p
        }

        when:
        Property prop = creator.createProperty(value, grailsProperty)

        then:
        1 * value.setTypeUsingReflection("com.example.MyEntity", "myProp")
        0 * value.createForeignKey()
        prop.getValue() == value
    }
}