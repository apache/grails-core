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


import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity
import org.grails.orm.hibernate.cfg.Mapping
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.RootClass
import grails.gorm.specs.HibernateGormDatastoreSpec
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.IdentityBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder

class IdentityBinderSpec extends HibernateGormDatastoreSpec {

    def simpleIdBinder = Mock(SimpleIdBinder)
    def compositeIdBinder = Mock(CompositeIdBinder)

    @Subject
    IdentityBinder binder

    def setup() {
        binder = new IdentityBinder(simpleIdBinder, compositeIdBinder)
    }

    def "should delegate to simpleIdBinder when mapping is null and domainClass has simple identity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def identifierProp = Mock(HibernatePersistentProperty)
        domainClass.getIdentity() >> identifierProp
        domainClass.getCompositeIdentity() >> null

        when:
        binder.bindIdentity(domainClass, root, mappings, null)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass, root, null, _)
    }

    def "should delegate to compositeIdBinder when mapping is null and domainClass has composite identity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def compositeProps = [Mock(HibernatePersistentProperty)] as HibernatePersistentProperty[]
        domainClass.getCompositeIdentity() >> compositeProps

        when:
        binder.bindIdentity(domainClass, root, mappings, null)

        then:
        1 * compositeIdBinder.bindCompositeId(domainClass, root, null, mappings)
    }

    def "should delegate to compositeIdBinder when mapping specifies composite identity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def gormMapping = Mock(Mapping)
        def compositeIdentity = Mock(CompositeIdentity)
        gormMapping.getIdentity() >> compositeIdentity

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping)

        then:
        1 * compositeIdBinder.bindCompositeId(domainClass, root, compositeIdentity, mappings)
    }

    def "should delegate to simpleIdBinder when mapping specifies simple identity"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def gormMapping = Mock(Mapping)
        def identity = new Identity(name: "foo")
        gormMapping.getIdentity() >> identity
        def identifierProp = Mock(HibernatePersistentProperty)
        domainClass.getPropertyByName("foo") >> identifierProp
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass, root, identity, _)
    }

    def "should not lookup property by name if identity name matches domain class name"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def mappings = Mock(InFlightMetadataCollector)
        def gormMapping = Mock(Mapping)
        def identity = new Identity(name: "MyEntity")
        gormMapping.getIdentity() >> identity
        def identifierProp = Mock(HibernatePersistentProperty)
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass, root, identity, _)
    }

    def "should set entity name on identity if it is null"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def root = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        root.setEntityName("MyEntity")
        def mappings = Mock(InFlightMetadataCollector)
        def gormMapping = Mock(Mapping)
        def identity = new Identity()
        gormMapping.getIdentity() >> identity
        def identifierProp = Mock(HibernatePersistentProperty)
        domainClass.getIdentity() >> identifierProp
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass, root, mappings, gormMapping)

        then:
        identity.getName() == "MyEntity"
        1 * simpleIdBinder.bindSimpleId(domainClass, root, identity, _)
    }
}
