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


import org.grails.datastore.mapping.model.ClassMapping
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCompositeIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.hibernate.boot.spi.InFlightMetadataCollector
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
        def domainClass = Mock(HibernatePersistentEntity)
        def simpleIdentityProperty = Mock(HibernateSimpleIdentityProperty)
        domainClass.getIdentityProperty() >> simpleIdentityProperty

        when:
        binder.bindIdentity(domainClass)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass)
    }

    def "should delegate to compositeIdBinder when mapping is null and domainClass has composite identity"() {
        given:
        def domainClass = Mock(HibernatePersistentEntity)
        def compositeIdentity = new HibernateCompositeIdentity()
        def compositeIdentityProperty = Mock(HibernateCompositeIdentityProperty)
        domainClass.getIdentityProperty() >> compositeIdentityProperty
        domainClass.getHibernateCompositeIdentity() >> Optional.of(compositeIdentity)

        when:
        binder.bindIdentity(domainClass)

        then:
        1 * compositeIdBinder.bindCompositeId(domainClass)
    }

    def "should delegate to compositeIdBinder when mapping specifies composite identity"() {
        given:
        def domainClass = Mock(HibernatePersistentEntity)
        def compositeIdentity = Mock(HibernateCompositeIdentity)
        def compositeIdentityProperty = Mock(HibernateCompositeIdentityProperty)
        domainClass.getIdentityProperty() >> compositeIdentityProperty
        domainClass.getHibernateCompositeIdentity() >> Optional.of(compositeIdentity)

        when:
        binder.bindIdentity(domainClass)

        then:
        1 * compositeIdBinder.bindCompositeId(domainClass)
    }

    def "should delegate to simpleIdBinder when mapping specifies simple identity"() {
        given:
        def domainClass = Mock(HibernatePersistentEntity)
        def simpleIdentityProperty = Mock(HibernateSimpleIdentityProperty)
        domainClass.getIdentityProperty() >> simpleIdentityProperty
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass)
    }

    def "should not lookup property by name if identity name matches domain class name"() {
        given:
        def domainClass = Mock(HibernatePersistentEntity)
        def simpleIdentityProperty = Mock(HibernateSimpleIdentityProperty)
        domainClass.getIdentityProperty() >> simpleIdentityProperty
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass)
    }

    def "should pass identity with name set to simpleIdBinder"() {
        given:
        def domainClass = Mock(HibernatePersistentEntity)
        def simpleIdentityProperty = Mock(HibernateSimpleIdentityProperty)
        domainClass.getIdentityProperty() >> simpleIdentityProperty
        domainClass.getName() >> "MyEntity"

        when:
        binder.bindIdentity(domainClass)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass)
    }

    def "should create synthetic identifier property if it doesn't exist"() {
        given:
        def domainClass = Mock(HibernatePersistentEntity)
        def simpleIdentityProperty = Mock(HibernateSimpleIdentityProperty)
        domainClass.getIdentityProperty() >> simpleIdentityProperty

        when:
        binder.bindIdentity(domainClass)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass)
    }
}
