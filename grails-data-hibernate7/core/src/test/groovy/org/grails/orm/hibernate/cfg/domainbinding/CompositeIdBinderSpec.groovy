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

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

import org.hibernate.mapping.Component
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder
import org.hibernate.mapping.Value

class CompositeIdBinderSpec extends HibernateGormDatastoreSpec {

    def componentBinder = Mock(ComponentBinder)
    def componentUpdater = Mock(ComponentUpdater)
    def grailsPropertyBinder = Mock(GrailsPropertyBinder)

    @Subject
    CompositeIdBinder binder

    def setup() {
        binder = new CompositeIdBinder(getGrailsDomainBinder().getMetadataBuildingContext(), componentUpdater, grailsPropertyBinder)
    }

    def "should bind composite id using property names from CompositeIdentity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        def mappings = metadataBuildingContext.getMetadataCollector()
        
        def compositeIdentity = new CompositeIdentity(propertyNames: ['prop1', 'prop2'] as String[])
        
        def prop1 = Mock(HibernatePersistentProperty)
        def prop2 = Mock(HibernatePersistentProperty)
        def identifierProp = Mock(HibernatePersistentProperty)
        domainClass.getPropertyByName("prop1") >> prop1
        domainClass.getPropertyByName("prop2") >> prop2
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"
        
        def table = new Table("my_entity")
        root.setTable(table)

        when:
        binder.bindCompositeId(domainClass, root, compositeIdentity, mappings)

        then:
        root.getIdentifier() instanceof Component
        root.getIdentifierMapper() instanceof Component
        root.hasEmbeddedIdentifier()
        2 * grailsPropertyBinder.bindProperty(root, table, "", identifierProp, _ as HibernatePersistentProperty, mappings) >> Mock(Value)
        2 * componentUpdater.updateComponent(_ as Component, identifierProp, _ as HibernatePersistentProperty, _ as Value)
    }

    def "should fallback to domainClass composite identity when CompositeIdentity is null"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        def mappings = metadataBuildingContext.getMetadataCollector()
        
        def prop1 = Mock(HibernatePersistentProperty)
        def identifierProp = Mock(HibernatePersistentProperty)
        domainClass.getCompositeIdentity() >> ([prop1] as HibernatePersistentProperty[])
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"
        
        def table = new Table("my_entity")
        root.setTable(table)

        when:
        binder.bindCompositeId(domainClass, root, null, mappings)

        then:
        1 * grailsPropertyBinder.bindProperty(root, table, "", identifierProp, prop1, mappings) >> Mock(Value)
        1 * componentUpdater.updateComponent(_ as Component, identifierProp, prop1, _ as Value)
    }

    def "should throw MappingException if no composite properties found"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def root = new RootClass(metadataBuildingContext)
        root.setEntityName("MyEntity")
        def mappings = metadataBuildingContext.getMetadataCollector()
        domainClass.getCompositeIdentity() >> null
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindCompositeId(domainClass, root, null, mappings)

        then:
        thrown(org.hibernate.MappingException)
    }
}
