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
package org.grails.datastore.mapping.config

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertyResolver
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

import org.grails.datastore.mapping.model.DatastoreConfigurationException

class ConfigurationSupportSpec extends Specification {

    void "services are resolved from instances, classes and class names"() {
        when:
        List<CsService> services = ConfigurationUtils.findServices(
                [new CsServiceA(), CsServiceB, CsServiceA.name, 42, String, 'java.lang.String'],
                CsService).toList()

        then:
        services*.getClass() == [CsServiceA, CsServiceB, CsServiceA]

        when:
        ConfigurationUtils.findServices(['no.such.Class'], CsService)

        then:
        DatastoreConfigurationException e = thrown()
        e.message == 'Class not found loading GORM: no.such.Class'
    }

    void "a null or empty service list yields nothing"() {
        expect:
        ConfigurationUtils.findServices(null, CsService).toList().empty
        ConfigurationUtils.findServices([], CsService).toList().empty
    }

    void "services are resolved from configuration"() {
        given:
        StandardEnvironment environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', ['cs.services': [CsServiceB.name]]))

        expect:
        ConfigurationUtils.findServices((PropertyResolver) environment, 'cs.services', CsService)*.getClass() == [CsServiceB]
        ConfigurationUtils.findServices((PropertyResolver) environment, 'cs.missing', CsService).toList().empty
    }

    void "settings expose the gorm configuration keys"() {
        expect:
        Settings.PREFIX == 'grails.gorm'
        Settings.SETTING_AUTO_FLUSH == 'grails.gorm.autoFlush'
        Settings.SETTING_FLUSH_MODE == 'grails.gorm.flushMode'
        Settings.SETTING_FAIL_ON_ERROR == 'grails.gorm.failOnError'
        Settings.SETTING_MARK_DIRTY == 'grails.gorm.markDirty'
        Settings.SETTING_DEFAULT_MAPPING == 'grails.gorm.default.mapping'
        Settings.SETTING_DEFAULT_CONSTRAINTS == 'grails.gorm.default.constraints'
        Settings.SETTING_CUSTOM_TYPES == 'grails.gorm.custom.types'
        Settings.SETTING_MULTI_TENANCY_MODE == 'grails.gorm.multiTenancy.mode'
        Settings.SETTING_MULTI_TENANT_RESOLVER_CLASS == 'grails.gorm.multiTenancy.tenantResolverClass'
        Settings.SETTING_MULTI_TENANT_RESOLVER == 'grails.gorm.multiTenancy.tenantResolver'
        Settings.SETTING_DATASOURCES == 'dataSources'
        Settings.SETTING_DATASOURCE == 'dataSource'
        Settings.SETTING_DB_CREATE == 'dataSource.dbCreate'
        Settings.SETTING_AUTO_TIMESTAMP_INSERT_OVERWRITE == 'grails.gorm.events.autoTimestampInsertOverwrite'
        Settings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS == 'grails.gorm.autoTimestampCacheAnnotations'
    }

    void "audit metadata types are ordered"() {
        expect:
        AuditMetadataType.values()*.name() == ['CREATED', 'UPDATED', 'CREATED_BY', 'UPDATED_BY', 'NONE']
        AuditMetadataType.valueOf('CREATED_BY') == AuditMetadataType.CREATED_BY
    }

}

interface CsService { }

class CsServiceA implements CsService { }

class CsServiceB implements CsService { }
