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

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateCompositeIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.hibernate.mapping.Component
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import spock.lang.Subject
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder

class CompositeIdBinderSpec extends HibernateGormDatastoreSpec {

    def componentUpdater = Mock(ComponentUpdater)
    def grailsPropertyBinder = Mock(GrailsPropertyBinder)

    @Subject
    CompositeIdBinder binder

    def setup() {
        binder = new CompositeIdBinder(
                getGrailsDomainBinder().getMetadataBuildingContext(),
                componentUpdater,
                grailsPropertyBinder
        )
    }

    def 'should bind composite id using parts from HibernateCompositeIdentityProperty'() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName('MyEntity')
        rootClass.setTable(new Table('my_entity'))

        def prop1 = Mock(HibernatePersistentProperty)
        def prop2 = Mock(HibernatePersistentProperty)
        def compositeIdentityProperty = Mock(HibernateCompositeIdentityProperty) {
            getParts() >> ([prop1, prop2] as HibernatePersistentProperty[])
        }
        def domainClass = Mock(HibernatePersistentEntity) {
            getName() >> 'MyEntity'
            getRootClass() >> rootClass
            getIdentityProperty() >> compositeIdentityProperty
        }

        when:
        binder.bindCompositeId(domainClass)

        then:
        rootClass.getIdentifier() instanceof Component
        rootClass.hasEmbeddedIdentifier()
        2 * grailsPropertyBinder.bindProperty(_ as HibernatePersistentProperty, null, '') >> Mock(Value)
        2 * componentUpdater.updateComponent(_ as Component, null, _ as HibernatePersistentProperty, _ as Value)
    }

    def 'should throw MappingException when entity does not have composite identity'() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName('MyEntity')
        def domainClass = Mock(HibernatePersistentEntity) {
            getName() >> 'MyEntity'
            getRootClass() >> rootClass
            getIdentityProperty() >> Mock(HibernateIdentityProperty)
        }

        when:
        binder.bindCompositeId(domainClass)

        then:
        thrown(org.hibernate.MappingException)
    }
}