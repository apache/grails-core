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

package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import spock.lang.Subject

class ComponentUpdaterSpec extends HibernateGormDatastoreSpec {

    def propertyFromValueCreator = Mock(PropertyFromValueCreator)

    @Subject
    ComponentUpdater updater

    def setup() {
        updater = new ComponentUpdater(propertyFromValueCreator)
    }

    def "should add property to component and set columns nullable if component property is nullable"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def component = new Component(metadataBuildingContext, root)
        
        def componentProperty = Mock(HibernatePersistentProperty)
        def currentGrailsProp = Mock(HibernatePersistentProperty)
        def value = new BasicValue(metadataBuildingContext, root.getTable())
        def column = new Column("test_col")
        value.addColumn(column)
        
        def hibernateProperty = new Property()
        hibernateProperty.setName("testProp")
        
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        componentProperty.getOwner() >> ownerEntity
        ownerEntity.isComponentPropertyNullable(componentProperty) >> true

        when:
        updater.updateComponent(component, componentProperty, currentGrailsProp, value)

        then:
        1 * propertyFromValueCreator.createProperty(value, currentGrailsProp) >> hibernateProperty
        component.getProperty("testProp") == hibernateProperty
        column.isNullable()
    }

    def "should not set columns nullable if component property is not nullable"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        def component = new Component(metadataBuildingContext, root)
        
        def componentProperty = Mock(HibernatePersistentProperty)
        def currentGrailsProp = Mock(HibernatePersistentProperty)
        def value = new BasicValue(metadataBuildingContext, root.getTable())
        def column = new Column("test_col")
        column.setNullable(false)
        value.addColumn(column)
        
        def hibernateProperty = new Property()
        hibernateProperty.setName("testProp")
        
        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        componentProperty.getOwner() >> ownerEntity
        ownerEntity.isComponentPropertyNullable(componentProperty) >> false

        when:
        updater.updateComponent(component, componentProperty, currentGrailsProp, value)

        then:
        1 * propertyFromValueCreator.createProperty(value, currentGrailsProp) >> hibernateProperty
        !column.isNullable()
    }
}
