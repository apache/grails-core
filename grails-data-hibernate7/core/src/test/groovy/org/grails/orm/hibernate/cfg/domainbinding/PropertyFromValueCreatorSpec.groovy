/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator

class PropertyFromValueCreatorSpec extends Specification {

    def "should create a property from a value"() {
        given:
        def propertyBinder = Mock(PropertyBinder)
        def creator = new PropertyFromValueCreator(propertyBinder)
        
        def value = Mock(Value)
        def grailsProperty = Mock(HibernatePersistentProperty)
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
        def grailsProperty = Mock(HibernatePersistentProperty)

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