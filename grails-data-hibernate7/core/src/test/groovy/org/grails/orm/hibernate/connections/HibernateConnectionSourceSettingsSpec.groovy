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
package org.grails.orm.hibernate.connections

import org.grails.datastore.mapping.core.DatastoreUtils
import org.hibernate.dialect.H2Dialect
import org.springframework.core.io.UrlResource
import spock.lang.Specification

/**
 * Created by graemerocher on 05/07/16.
 */
class HibernateConnectionSourceSettingsSpec extends Specification {

    void "test hibernate connection source settings"() {
        when:"The configuration is built"
        Map config = [
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'commit',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
                'hibernate.cache':['region.factory_class':'org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory'],
                'hibernate.configLocations':'file:hibernate.cfg.xml',
                'hibernate.jpa.compliance.cascade': 'true',
        ]
        HibernateConnectionSourceSettingsBuilder builder = new HibernateConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(config))
        HibernateConnectionSourceSettings settings = builder.build()

        def expectedDataSourceProperties = new Properties()
        expectedDataSourceProperties.put('hibernate.hbm2ddl.auto', 'update')
        expectedDataSourceProperties.put('hibernate.show_sql', 'false')
        expectedDataSourceProperties.put('hibernate.format_sql', 'true')
        expectedDataSourceProperties.put('hibernate.dialect', H2Dialect.name)

        def expectedHibernateProperties = new Properties()
        expectedHibernateProperties.put('hibernate.hbm2ddl.auto', 'create')
        expectedHibernateProperties.put('hibernate.cache.queries', 'true')
        expectedHibernateProperties.put('hibernate.flush.mode', 'commit')
        expectedHibernateProperties.put('hibernate.naming_strategy','org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy')
        expectedHibernateProperties.put('hibernate.entity_dirtiness_strategy', 'org.grails.orm.hibernate.dirty.GrailsEntityDirtinessStrategy')
        expectedHibernateProperties.put('hibernate.configLocations','file:hibernate.cfg.xml')
        expectedHibernateProperties.put('hibernate.use_query_cache','true')
        expectedHibernateProperties.put("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD")
        expectedHibernateProperties.put('hibernate.cache.region.factory_class','org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory')
        expectedHibernateProperties.put('hibernate.jpa.compliance.cascade', 'true')

        then:"The results are correct"
        settings.dataSource.dbCreate == 'update'
        settings.dataSource.dialect == H2Dialect
        settings.dataSource.formatSql
        !settings.dataSource.logSql
        settings.dataSource.toHibernateProperties() == expectedDataSourceProperties

        settings.hibernate.getFlush().mode == HibernateConnectionSourceSettings.HibernateSettings.FlushSettings.FlushMode.COMMIT
        settings.hibernate.getCache().queries
        settings.hibernate.get('hbm2ddl.auto') == 'create'
        settings.hibernate.getConfigLocations().size() == 1
        settings.hibernate.getConfigLocations()[0] instanceof UrlResource

        def hibernateProperties = settings.hibernate.toProperties()
        hibernateProperties['hibernate.hbm2ddl.auto'] == 'create'
        hibernateProperties['hibernate.cache.queries'] == 'true'
        hibernateProperties['hibernate.flush.mode'] == 'commit'
        hibernateProperties['hibernate.naming_strategy'] == 'org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl'
        hibernateProperties['hibernate.entity_dirtiness_strategy'] == 'org.grails.orm.hibernate.dirty.GrailsEntityDirtinessStrategy'
        hibernateProperties['hibernate.configLocations'] == 'file:hibernate.cfg.xml'
        hibernateProperties['hibernate.use_query_cache'] == 'true'
        hibernateProperties["hibernate.connection.handling_mode"] == "DELAYED_ACQUISITION_AND_HOLD"
        hibernateProperties['hibernate.cache.region.factory_class'] == 'org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory'
        hibernateProperties['hibernate.jpa.compliance.cascade'] == 'true'
    }

    void "test toHibernateEventListeners"() {
        given:
        def interceptor = Mock(org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor)

        expect:
        HibernateConnectionSourceSettings.HibernateSettings.toHibernateEventListeners(null).isEmpty()
        HibernateConnectionSourceSettings.HibernateSettings.toHibernateEventListeners(interceptor).size() == 8
    }

    void "test toProperties with dirty checking and custom config"() {
        given:
        def settings = new HibernateConnectionSourceSettings()
        settings.hibernate.hibernateDirtyChecking = true
        settings.hibernate.configClass = org.hibernate.cfg.Configuration
        settings.hibernate.naming_strategy = null

        when:
        def props = settings.hibernate.toProperties()

        then:
        !props.containsKey('hibernate.entity_dirtiness_strategy')
        !props.containsKey('hibernate.naming_strategy')
        props.get('hibernate.config_class') == org.hibernate.cfg.Configuration.name
    }

    void "test populateProperties with nested map"() {
        given:
        def settings = new HibernateConnectionSourceSettings.HibernateSettings()
        settings.put("outer", [inner: "value"])
        def props = new Properties()

        when:
        settings.populateProperties(props, settings, "prefix")

        then:
        props.get("prefix.outer.inner") == "value"
    }

    void "test clone settings"() {
        given:
        def settings = new HibernateConnectionSourceSettings(enableReload: true)

        when:
        def cloned = settings.clone()

        then:
        cloned !== settings
        cloned.enableReload
    }

    void "test toProperties with additional properties"() {
        given:
        def settings = new HibernateConnectionSourceSettings()
        def hibernateSettings = settings.hibernate
        def addProps = new Properties()
        addProps.put("custom.key", "custom.value")
        hibernateSettings.@additionalProperties = addProps

        when:
        def props = hibernateSettings.toProperties()

        then:
        props.get("custom.key") == "custom.value"
    }
}
