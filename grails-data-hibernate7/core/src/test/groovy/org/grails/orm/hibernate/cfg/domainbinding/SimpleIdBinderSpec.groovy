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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.PrimaryKey
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table

import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueCreator
import org.grails.datastore.mapping.reflect.EntityReflector

class SimpleIdBinderSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    JdbcEnvironment jdbcEnvironment
    def simpleValueBinder
    def propertyBinder
    def basicValueCreator
    Table currentTable

    def simpleIdBinder

    def setup() {
        def domainBinder = getGrailsDomainBinder()
        def metadataCollector = domainBinder.getMetadataBuildingContext().getMetadataCollector()
        metadataBuildingContext = new org.hibernate.boot.internal.MetadataBuildingContextRootImpl(
                'default',
                metadataCollector.getBootstrapContext(),
                metadataCollector.getMetadataBuildingOptions(),
                metadataCollector,
                null
        )
        jdbcEnvironment = domainBinder.getJdbcEnvironment()

        // Use a Mock for BasicValueCreator and return a BasicValue based on the currentTable
        basicValueCreator = Mock(BasicValueCreator)
        basicValueCreator.bindBasicValue(_) >> { HibernateSimpleIdentityProperty id ->
            return new BasicValue(metadataBuildingContext, currentTable)
        }

        // Mock the collaborators that can be safely mocked
        simpleValueBinder = Mock(SimpleValueBinder)
        propertyBinder = Spy(PropertyBinder)

        simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, basicValueCreator, simpleValueBinder, propertyBinder)
    }

    def 'bindSimpleId with identity generator'() {
        given:
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> false
        }
        def testProperty = Mock(HibernateSimpleIdentityProperty) {
            getName() >> 'id'
        }
        def rootClass = new RootClass(metadataBuildingContext)
        currentTable = new Table('TEST_TABLE')
        rootClass.setTable(currentTable)
        def domainClass = Mock(HibernatePersistentEntity) {
            getMappedForm() >> mapping
            getIdentity() >> testProperty
            getName() >> 'TestEntity'
            getIdentityProperty() >> testProperty
            getRootClass() >> rootClass
        }

        when:
        simpleIdBinder.bindSimpleId(domainClass)

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty, null, _, '')
        1 * propertyBinder.bindProperty(testProperty, _)

        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey instanceof PrimaryKey
    }

    def 'bindSimpleId with sequence generator'() {
        given:
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> true
        }
        def testProperty = Mock(HibernateSimpleIdentityProperty) {
            getName() >> 'id'
        }
        def rootClass = new RootClass(metadataBuildingContext)
        currentTable = new Table('TEST_TABLE')
        rootClass.setTable(currentTable)
        def domainClass = Mock(HibernatePersistentEntity) {
            getMappedForm() >> mapping
            getIdentity() >> testProperty
            getName() >> 'TestEntity'
            getIdentityProperty() >> testProperty
            getRootClass() >> rootClass
        }

        when:
        simpleIdBinder.bindSimpleId(domainClass)

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty, null, _, '')
        1 * propertyBinder.bindProperty(testProperty, _)

        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey instanceof PrimaryKey
    }

    def 'bindSimpleId with synthetic identifier property'() {
        given:
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> false
        }
        def reflector = Mock(EntityReflector)
        def rootClass = new RootClass(metadataBuildingContext)
        currentTable = new Table('TEST_TABLE')
        rootClass.setTable(currentTable)
        def domainClass = Mock(HibernatePersistentEntity) {
            getMappedForm() >> mapping
            getIdentity() >> null
            getName() >> 'TestEntity'
            getMappingContext() >> getGrailsDomainBinder().hibernateMappingContext
            getMapping() >> Mock(org.grails.datastore.mapping.model.ClassMapping)
            getReflector() >> reflector
            getIdentityProperty() >> Mock(HibernateSimpleIdentityProperty)
            getRootClass() >> rootClass
        }

        when:
        simpleIdBinder.bindSimpleId(domainClass)

        then:
        1 * simpleValueBinder.bindSimpleValue(_, null, _, '')
        1 * propertyBinder.bindProperty(_, _)

        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey instanceof PrimaryKey
    }

    def 'bindSimpleId throws MappingException when identity property is not a HibernateSimpleIdentityProperty'() {
        given:
        def domainClass = Mock(HibernatePersistentEntity) {
            getIdentityProperty() >> null
            getName() >> 'InvalidEntity'
        }

        when:
        simpleIdBinder.bindSimpleId(domainClass)

        then:
        def e = thrown(org.hibernate.MappingException)
        e.message.contains('InvalidEntity')
    }

    def 'getMetadataBuildingContext returns the context passed to constructor'() {
        expect:
        simpleIdBinder.getMetadataBuildingContext() == metadataBuildingContext
    }
}
