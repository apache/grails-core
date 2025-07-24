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

import groovy.transform.CompileStatic

import org.springframework.core.env.PropertyResolver

import org.grails.datastore.mapping.config.ConfigurationBuilder
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings

/**
 * Builds the GORM for Hibernate configuration
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class HibernateConnectionSourceSettingsBuilder extends ConfigurationBuilder<HibernateConnectionSourceSettings, HibernateConnectionSourceSettings> {
    HibernateConnectionSourceSettings fallBackHibernateSettings

    HibernateConnectionSourceSettingsBuilder(PropertyResolver propertyResolver, String configurationPrefix = "", ConnectionSourceSettings fallBackConfiguration = null) {
        super(propertyResolver, configurationPrefix, fallBackConfiguration)

        if(fallBackConfiguration instanceof HibernateConnectionSourceSettings) {
            fallBackHibernateSettings = (HibernateConnectionSourceSettings)fallBackConfiguration
        }
    }

    @Override
    protected HibernateConnectionSourceSettings createBuilder() {
        def settings = new HibernateConnectionSourceSettings()
        if(fallBackHibernateSettings != null) {
            settings.getHibernate().putAll( fallBackHibernateSettings.getHibernate() )
        }
        return settings
    }

    @Override
    HibernateConnectionSourceSettings build() {
        HibernateConnectionSourceSettings finalSettings = (HibernateConnectionSourceSettings)super.build()
        Map orgHibernateProperties = propertyResolver.getProperty("org.hibernate", Map.class, Collections.emptyMap())
        Properties additionalProperties = finalSettings.getHibernate().getAdditionalProperties()
        for(key in orgHibernateProperties.keySet()) {
            additionalProperties.put("org.hibernate.$key".toString(), orgHibernateProperties.get(key))
        }
        return finalSettings
    }

    @Override
    protected HibernateConnectionSourceSettings toConfiguration(HibernateConnectionSourceSettings builder) {
        return builder
    }
}
