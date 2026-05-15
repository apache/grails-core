/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCompositeIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityProperty
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

    def 'should delegate to simpleIdBinder when domainClass has simple identity'() {
        given:
        def domainClass = Mock(HibernatePersistentEntity)
        def simpleIdentityProperty = Mock(HibernateSimpleIdentityProperty)
        domainClass.getIdentityProperty() >> simpleIdentityProperty

        when:
        binder.bindIdentity(domainClass)

        then:
        1 * simpleIdBinder.bindSimpleId(domainClass)
    }

    def 'should delegate to compositeIdBinder when domainClass has composite identity'() {
        given:
        def domainClass = Mock(HibernatePersistentEntity)
        def compositeIdentityProperty = Mock(HibernateCompositeIdentityProperty)
        domainClass.getIdentityProperty() >> compositeIdentityProperty

        when:
        binder.bindIdentity(domainClass)

        then:
        1 * compositeIdBinder.bindCompositeId(domainClass)
    }

    def 'should throw MappingException when no identity found'() {
        given:
        def domainClass = Mock(HibernatePersistentEntity)
        domainClass.getIdentityProperty() >> Mock(HibernateIdentityProperty)
        domainClass.getName() >> 'MyEntity'

        when:
        binder.bindIdentity(domainClass)

        then:
        thrown(org.hibernate.MappingException)
    }
}
