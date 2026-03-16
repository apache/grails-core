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
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.PrimaryKey
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table

import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator

class SimpleIdBinderSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    JdbcEnvironment jdbcEnvironment
    def simpleValueBinder
    def propertyBinder
    def basicValueIdCreator
    Table currentTable

    def simpleIdBinder

    def setup() {
        def domainBinder = getGrailsDomainBinder()
        def metadataCollector = domainBinder.getMetadataBuildingContext().getMetadataCollector()
        metadataBuildingContext = new org.hibernate.boot.internal.MetadataBuildingContextRootImpl(
                "default",
                metadataCollector.getBootstrapContext(),
                metadataCollector.getMetadataBuildingOptions(),
                metadataCollector,
                null
        )
        jdbcEnvironment = domainBinder.getJdbcEnvironment()

        // Use a Mock for BasicValueIdCreator and return a BasicValue based on the currentTable
        basicValueIdCreator = Mock(BasicValueIdCreator)
        basicValueIdCreator.getBasicValueId(_, _, _, _, _) >> { MetadataBuildingContext ctx, Table table, Identity id, GrailsHibernatePersistentEntity domainClass, boolean useSeq ->
            return new BasicValue(ctx, table)
        }

        // Mock the collaborators that can be safely mocked
        simpleValueBinder = Mock(SimpleValueBinder)
        propertyBinder = Spy(PropertyBinder)

        simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, basicValueIdCreator, simpleValueBinder, propertyBinder)
    }

    def "bindSimpleId with identity generator"() {
        given:
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> false
        }
        def testProperty = Mock(HibernatePersistentProperty) {
            getName() >> "id"
        }
        def domainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getIdentity() >> testProperty
            getName() >> "TestEntity"
        }
        def rootClass = new RootClass(metadataBuildingContext)
        currentTable = new Table("TEST_TABLE")
        rootClass.setTable(currentTable)

        when:
        simpleIdBinder.bindSimpleId(domainClass, rootClass, new Identity(generator: GrailsSequenceGeneratorEnum.IDENTITY.toString()), rootClass.getTable())

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty as HibernatePersistentProperty, null, _, "")
        1 * propertyBinder.bindProperty(testProperty, _)

        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey instanceof PrimaryKey
    }

    def "bindSimpleId with sequence generator"() {
        given:
        def mapping = Mock(org.grails.orm.hibernate.cfg.Mapping) {
            isTablePerConcreteClass() >> true
        }
        def testProperty = Mock(HibernatePersistentProperty) {
            getName() >> "id"
        }
        def domainClass = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getIdentity() >> testProperty
            getName() >> "TestEntity"
        }
        def rootClass = new RootClass(metadataBuildingContext)
        currentTable = new Table("TEST_TABLE")
        rootClass.setTable(currentTable)

        when:
        simpleIdBinder.bindSimpleId(domainClass, rootClass, new Identity(generator: GrailsSequenceGeneratorEnum.SEQUENCE.toString(), params: [sequence: 'SEQ_TEST']), rootClass.getTable())

        then:
        1 * simpleValueBinder.bindSimpleValue(testProperty as HibernatePersistentProperty, null, _, "")
        1 * propertyBinder.bindProperty(testProperty, _)

        rootClass.identifier instanceof BasicValue
        rootClass.declaredIdentifierProperty != null
        rootClass.identifierProperty != null
        rootClass.table.primaryKey instanceof PrimaryKey
    }

    def "bindSimpleId with non-existent identifier property"() {
        given:
        def domainClass = Mock(GrailsHibernatePersistentEntity) {
            getName() >> "TestEntity"
            getPropertyByName("nonExistent") >> null
            getIdentity() >> Mock(HibernatePersistentProperty)
        }
        def rootClass = new RootClass(metadataBuildingContext)
        def table = Mock(Table)

        when:
        simpleIdBinder.bindSimpleId(domainClass, rootClass, new Identity(name: "nonExistent"), table)

        then:
        thrown(org.hibernate.MappingException)
    }
}
