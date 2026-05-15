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

package org.grails.orm.hibernate.cfg.domainbinding.util

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.config.GormProperties
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.engine.spi.FilterDefinition

/**
 * Tests for MultiTenantFilterDefinitionBinder.
 */
class MultiTenantFilterDefinitionBinderSpec extends HibernateGormDatastoreSpec {

    MultiTenantFilterDefinitionBinder filterDefinitionBinder = new MultiTenantFilterDefinitionBinder()

    void 'test create adds filter definition'() {
        given:
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def property = new Property()
        property.setName('tenantId')
        
        def table = new Table('ROOT_TABLE')
        def value = new BasicValue(buildingContext, table)
        value.setTypeName('long')
        property.setValue(value)
        
        def filterName = GormProperties.TENANT_IDENTITY

        when:
        Optional<FilterDefinition> filterDefinition = filterDefinitionBinder.create(filterName, property)

        then:
        filterDefinition.isPresent()
        filterDefinition.get().getFilterName() == filterName
        filterDefinition.get().getDefaultFilterCondition() == null
        filterDefinition.get().getParameterNames().contains(filterName)
    }

    void 'test create returns empty if property value is not BasicValue'() {
        given:
        def property = new Property()
        def filterName = GormProperties.TENANT_IDENTITY
        
        // Property with no value (null)
        property.setValue(null)

        when:
        Optional<FilterDefinition> filterDefinition = filterDefinitionBinder.create(filterName, property)

        then:
        !filterDefinition.isPresent()
    }
}
