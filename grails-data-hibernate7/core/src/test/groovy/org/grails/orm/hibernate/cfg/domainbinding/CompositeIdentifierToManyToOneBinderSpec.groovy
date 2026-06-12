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


import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.Column
import org.hibernate.mapping.KeyValue
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SimpleValue
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator

class CompositeIdentifierToManyToOneBinderSpec extends Specification {

    def "Test bindCompositeIdentifierToManyToOne with nested composite ID"() {
        given:
        // 1. Stub all dependencies for the protected constructor
        def calculator = Stub(ForeignKeyColumnCountCalculator)
        def namingStrategy = Stub(PersistentEntityNamingStrategy)
        def columnNameFetcher = Stub(DefaultColumnNameFetcher)
        def backticksRemover = Stub(BackticksRemover)
        def simpleValueBinder = Mock(SimpleValueBinder) // Use Mock to verify interaction
        def metadataBuildingContext = Mock(org.hibernate.boot.spi.MetadataBuildingContext)

        // Instantiate the binder with stubs
        def binder = new CompositeIdentifierToManyToOneBinder(calculator, namingStrategy, columnNameFetcher, backticksRemover, simpleValueBinder)

        // 2. Set up stubs for the method arguments
        def association = Mock(HibernatePersistentProperty)
        def value = Mock(SimpleValue)
        def refDomainClass = Mock(GrailsHibernatePersistentEntity)
        def persistentClass = new RootClass(metadataBuildingContext)
        def identifier = Mock(KeyValue)
        def path = "/test"

        // Use a real CompositeIdentity object to avoid final method mocking issues
        def propertyNames = ["nestedEntity"] as String[]
        def compositeId = new HibernateCompositeIdentity()
        compositeId.setPropertyNames(propertyNames)

        // 3. Define the nested composite key scenario
        def propertyConfig = new PropertyConfig()
        association.getMappedForm() >> propertyConfig
        association.getHibernateMappedForm() >> propertyConfig

        calculator.calculateForeignKeyColumnCount(refDomainClass, propertyNames) >> 2

        def nestedEntityProp = Mock(HibernateToOneProperty)
        refDomainClass.getHibernatePropertyByName("nestedEntity") >> nestedEntityProp
        nestedEntityProp.name >> "nestedEntity"

        def nestedAssociatedEntity = Mock(GrailsHibernatePersistentEntity)
        nestedEntityProp.getHibernateAssociatedEntity() >> nestedAssociatedEntity

        def nestedPartA = Mock(HibernatePersistentProperty)
        def nestedPartB = Mock(HibernatePersistentProperty)
        def perArray = [nestedPartA, nestedPartB] as HibernatePersistentProperty[]
        nestedAssociatedEntity.getCompositeIdentity() >> perArray

        // 4. Mock the behavior of the dependency methods
        refDomainClass.getTableName(namingStrategy) >> "ref_table"
        namingStrategy.resolveColumnName("nestedEntity") >> "nested_entity_col"
        columnNameFetcher.getDefaultColumnName(nestedPartA) >> "part_a_col"
        columnNameFetcher.getDefaultColumnName(nestedPartB) >> "part_b_col"

        // Make backticks remover pass through the values for simplicity
        backticksRemover.apply(_) >> { String s -> s }
        refDomainClass.getPersistentClass() >> persistentClass
        refDomainClass.getName() >> "RefDomain"
        persistentClass.setIdentifier(identifier)
        identifier.getColumns() >> [new Column("part_a_col"), new Column("part_b_col")]
        value.getColumns() >> [new Column("ref_table_nested_entity_col_part_a_col"), new Column("ref_table_nested_entity_col_part_b_col")]
        value.createForeignKeyOfEntity("RefDomain", _ as List<Column>) >> null

        when:
        binder.bindCompositeIdentifierToManyToOne(association as HibernatePersistentProperty, value, compositeId, refDomainClass, path)

        then:
        // 5. Verify the final generated column names
        def finalColumns = propertyConfig.getColumns()
        finalColumns.size() == 2
        finalColumns[0].getName() == "ref_table_nested_entity_col_part_a_col"
        finalColumns[1].getName() == "ref_table_nested_entity_col_part_b_col"

        and: // 6. Verify the call to the simple value binder
        1 * simpleValueBinder.bindSimpleValue(_ as HibernatePersistentProperty, null, value, path)
    }

    def "Test bindCompositeIdentifierToManyToOne when column count matches"() {
        given:
        // 1. Use Mocks for dependencies that require interaction verification
        def calculator = Stub(ForeignKeyColumnCountCalculator)
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def columnNameFetcher = Mock(DefaultColumnNameFetcher)
        def backticksRemover = Mock(BackticksRemover)
        def simpleValueBinder = Mock(SimpleValueBinder)
        def metadataBuildingContext = Mock(org.hibernate.boot.spi.MetadataBuildingContext)

        def binder = new CompositeIdentifierToManyToOneBinder(calculator, namingStrategy, columnNameFetcher, backticksRemover, simpleValueBinder)

        // 2. Set up arguments
        def association = Mock(HibernatePersistentProperty)
        def value = Mock(SimpleValue)
        def compositeId = new HibernateCompositeIdentity()
        compositeId.setPropertyNames(["prop1", "prop2"] as String[])
        def refDomainClass = Mock(GrailsHibernatePersistentEntity)
        def persistentClass = new RootClass(metadataBuildingContext)
        def identifier = Mock(KeyValue)
        def path = "/test"

        // 3. Set up the "match" condition
        def propertyConfig = new PropertyConfig()
        propertyConfig.getColumns().add(new ColumnConfig())
        propertyConfig.getColumns().add(new ColumnConfig())
        association.getMappedForm() >> propertyConfig
        association.getHibernateMappedForm() >> propertyConfig

        // The calculated length is the same as the number of columns already in the config
        calculator.calculateForeignKeyColumnCount(refDomainClass, _ as String[]) >> 2
        refDomainClass.getPersistentClass() >> persistentClass
        refDomainClass.getName() >> "RefDomain"
        persistentClass.setIdentifier(identifier)
        identifier.getColumns() >> [new Column("prop1"), new Column("prop2")]
        value.getColumns() >> [new Column("prop1"), new Column("prop2")]
        value.createForeignKeyOfEntity("RefDomain", _ as List<Column>) >> null

        when:
        binder.bindCompositeIdentifierToManyToOne(association as HibernatePersistentProperty, value, compositeId, refDomainClass, path)

        then:
        // 4. Verify the column name generation logic is skipped
        0 * refDomainClass.getTableName(_)
        0 * namingStrategy._
        0 * columnNameFetcher._
        0 * backticksRemover._

        and: // 5. Verify the simple value binder is still called
        1 * simpleValueBinder.bindSimpleValue(_ as HibernatePersistentProperty, null, value, path)
    }
}
