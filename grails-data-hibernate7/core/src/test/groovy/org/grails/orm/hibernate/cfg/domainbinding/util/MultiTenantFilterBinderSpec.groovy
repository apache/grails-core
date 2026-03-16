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

package org.grails.orm.hibernate.cfg.domainbinding.util

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.UnionSubclass
import org.hibernate.mapping.Table
import org.hibernate.engine.spi.FilterDefinition
import org.grails.datastore.mapping.model.types.TenantId

/**
 * Tests for MultiTenantFilterBinder.
 */
class MultiTenantFilterBinderSpec extends HibernateGormDatastoreSpec {

    GrailsPropertyResolver grailsPropertyResolver = Mock(GrailsPropertyResolver)
    DefaultColumnNameFetcher fetcher = Mock(DefaultColumnNameFetcher)
    InFlightMetadataCollector mockCollector = GroovyMock(InFlightMetadataCollector)
    MultiTenantFilterDefinitionBinder filterDefinitionBinder = new MultiTenantFilterDefinitionBinder()
    MultiTenantFilterBinder filterBinder

    void setup() {
        filterBinder = new MultiTenantFilterBinder(grailsPropertyResolver, filterDefinitionBinder, mockCollector, fetcher)
    }

    void "test add multi tenant filter to root class"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def persistentClass = new RootClass(buildingContext)
        
        def tenantId = Mock(HibernatePersistentProperty)
        tenantId.getName() >> "tenantId"
        
        def property = new Property()
        property.setName("tenantId")
        
        def table = new Table("ROOT_TABLE")
        def value = new BasicValue(buildingContext, table)
        value.setTypeName("long")
        
        entity.isMultiTenant() >> true
        entity.getHibernateTenantId() >> tenantId
        grailsPropertyResolver.getProperty(persistentClass, "tenantId") >> property
        
        property.setValue(value)
        persistentClass.setTable(table)
        persistentClass.addProperty(property)
        
        // Setup for FilterDefinition
        mockCollector.getFilterDefinition(GormProperties.TENANT_IDENTITY) >> null
        
        entity.getMultiTenantFilterCondition(fetcher) >> "tenant_id = :tenantId"

        when:
        filterBinder.bind(entity, persistentClass)

        then:
        1 * mockCollector.addFilterDefinition(_ as FilterDefinition)
        persistentClass.getFilters().any { it.getName() == GormProperties.TENANT_IDENTITY && it.getCondition() == "tenant_id = :tenantId" }
    }

    void "test skip filter for single table subclass (redundant)"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(buildingContext)
        def table = new Table("ROOT_TABLE")
        rootClass.setTable(table)
        
        def persistentClass = new SingleTableSubclass(rootClass, buildingContext)
        def tenantId = Mock(HibernatePersistentProperty)
        tenantId.getName() >> "tenantId"
        
        def property = new Property()
        property.setName("tenantId")
        def value = new BasicValue(buildingContext, table)
        value.setTypeName("long")
        property.setValue(value)
        
        rootClass.addProperty(property)
        
        entity.isMultiTenant() >> true
        entity.getHibernateTenantId() >> tenantId
        grailsPropertyResolver.getProperty(persistentClass, "tenantId") >> property 
        
        entity.isTablePerHierarchySubclass() >> true
        mockCollector.getFilterDefinition(_) >> Mock(FilterDefinition)

        when:
        filterBinder.bind(entity, persistentClass)

        then:
        !persistentClass.getFilters().any { it.getName() == GormProperties.TENANT_IDENTITY }
    }

    void "test skip filter for joined subclass if inherited (alias safety)"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(buildingContext)
        def rootTable = new Table("ROOT_TABLE")
        rootTable.setName("ROOT_TABLE")
        rootClass.setTable(rootTable)

        def persistentClass = new JoinedSubclass(rootClass, buildingContext)
        def subTable = new Table("SUB_TABLE")
        subTable.setName("SUB_TABLE")
        persistentClass.setTable(subTable)
        
        def tenantId = Mock(HibernatePersistentProperty)
        tenantId.getName() >> "tenantId"
        
        def property = new Property()
        property.setName("tenantId")
        def value = new BasicValue(buildingContext, rootTable)
        value.setTypeName("long")
        property.setValue(value)
        
        rootClass.addProperty(property)

        entity.isMultiTenant() >> true
        entity.getHibernateTenantId() >> tenantId
        grailsPropertyResolver.getProperty(persistentClass, "tenantId") >> property 
        
        mockCollector.getFilterDefinition(_) >> Mock(FilterDefinition)

        when:
        filterBinder.bind(entity, persistentClass)

        then:
        !persistentClass.getFilters().any { it.getName() == GormProperties.TENANT_IDENTITY }
    }

    void "test add filter for union subclass (own table)"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(buildingContext)
        def subTable = new Table("SUB_TABLE")

        def persistentClass = new UnionSubclass(rootClass, buildingContext)
        persistentClass.setTable(subTable)
        
        def tenantId = Mock(HibernatePersistentProperty)
        tenantId.getName() >> "tenantId"
        
        def property = new Property()
        property.setName("tenantId")
        def value = new BasicValue(buildingContext, subTable)
        value.setTypeName("long")
        property.setValue(value)
        
        persistentClass.addProperty(property)

        entity.isMultiTenant() >> true
        entity.getHibernateTenantId() >> tenantId
        grailsPropertyResolver.getProperty(persistentClass, "tenantId") >> property 
        
        entity.isTablePerHierarchySubclass() >> false
        mockCollector.getFilterDefinition(_) >> Mock(FilterDefinition)
        entity.getMultiTenantFilterCondition(fetcher) >> "tenant_id = :tenantId"

        when:
        filterBinder.bind(entity, persistentClass)

        then:
        persistentClass.getFilters().any { it.getName() == GormProperties.TENANT_IDENTITY && it.getCondition() == "tenant_id = :tenantId" }
    }
}
