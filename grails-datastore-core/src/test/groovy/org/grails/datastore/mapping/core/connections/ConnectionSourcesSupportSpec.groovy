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
package org.grails.datastore.mapping.core.connections

import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.MultiTenant
import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.multitenancy.TenantDataSourceConfig

class ConnectionSourcesSupportSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    void "entities default to the default connection source"() {
        given:
        PersistentEntity entity = mappingContext.addPersistentEntity(CSDefault)

        expect:
        ConnectionSourcesSupport.DEFAULT_CONNECTION_SOURCE_NAMES == [ConnectionSource.DEFAULT]
        ConnectionSourcesSupport.getConnectionSourceNames(entity) == [ConnectionSource.DEFAULT]
        ConnectionSourcesSupport.getDefaultConnectionSourceName(entity) == ConnectionSource.DEFAULT
        ConnectionSourcesSupport.usesConnectionSource(entity, ConnectionSource.DEFAULT)
        !ConnectionSourcesSupport.usesConnectionSource(entity, 'reporting')
    }

    void "mapped datasources drive the connection source names"() {
        given:
        PersistentEntity entity = mappingContext.addPersistentEntity(CSMapped)

        expect:
        ConnectionSourcesSupport.getConnectionSourceNames(entity) == ['reporting', 'audit']
        ConnectionSourcesSupport.getDefaultConnectionSourceName(entity) == 'reporting'
        ConnectionSourcesSupport.usesConnectionSource(entity, 'audit')
        !ConnectionSourcesSupport.usesConnectionSource(entity, ConnectionSource.DEFAULT)
    }

    void "an entity mapped to ALL uses every connection source"() {
        given:
        PersistentEntity entity = mappingContext.addPersistentEntity(CSAll)

        expect:
        ConnectionSourcesSupport.getConnectionSourceNames(entity) == [ConnectionSource.ALL]
        ConnectionSourcesSupport.getDefaultConnectionSourceName(entity) == ConnectionSource.ALL
        ConnectionSourcesSupport.usesConnectionSource(entity, 'anything')
    }

    void "an entity without a mapped form falls back to the default names"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) {
            getMapping() >> Stub(ClassMapping) { getMappedForm() >> null }
        }

        expect:
        ConnectionSourcesSupport.getConnectionSourceNames(entity) == [ConnectionSource.DEFAULT]
    }

    void "multi tenant entities use every connection source except the excluded ones"() {
        given:
        PersistentEntity tenanted = mappingContext.addPersistentEntity(CSTenanted)
        PersistentEntity excluding = mappingContext.addPersistentEntity(CSTenantedExcluding)

        expect:
        ConnectionSourcesSupport.isMultiTenant([MultiTenant] as Class[])
        !ConnectionSourcesSupport.isMultiTenant([Serializable] as Class[])
        ConnectionSourcesSupport.usesConnectionSource(tenanted, 'anything')
        !ConnectionSourcesSupport.isMultiTenantExcludedDataSource(tenanted, 'admin')
        ConnectionSourcesSupport.usesConnectionSource(excluding, 'tenants')
        !ConnectionSourcesSupport.usesConnectionSource(excluding, 'admin')
        ConnectionSourcesSupport.isMultiTenantExcludedDataSource(excluding, 'admin')
    }

    void "the connection source constants are stable"() {
        expect:
        ConnectionSource.DEFAULT == 'default'
        ConnectionSource.OLD_DEFAULT == 'DEFAULT'
        ConnectionSource.ALL == 'ALL'
    }
}

@Entity
class CSDefault {
    Long id
    String name
}

@Entity
class CSMapped {
    Long id
    String name
    static mapping = {
        datasources(['reporting', 'audit'])
    }
}

@Entity
class CSAll {
    Long id
    String name
    static mapping = {
        datasource ConnectionSource.ALL
    }
}

@Entity
class CSTenanted implements MultiTenant {
    Long id
    String tenantId
}

@Entity
@TenantDataSourceConfig(dataSourcesToExclude = ['admin'])
class CSTenantedExcluding implements MultiTenant {
    Long id
    String tenantId
}
