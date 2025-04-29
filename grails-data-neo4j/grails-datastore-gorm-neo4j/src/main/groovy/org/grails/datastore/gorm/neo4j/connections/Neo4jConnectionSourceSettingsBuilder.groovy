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
package org.grails.datastore.gorm.neo4j.connections

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.mapping.config.ConfigurationBuilder
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.springframework.core.env.PropertyResolver

/**
 * A builder for creating {@link Neo4jConnectionSourceSettings} objects
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class Neo4jConnectionSourceSettingsBuilder extends ConfigurationBuilder<Neo4jConnectionSourceSettings, Neo4jConnectionSourceSettings> {

    Neo4jConnectionSourceSettingsBuilder(PropertyResolver propertyResolver, String configurationPrefix = Settings.PREFIX, ConnectionSourceSettings fallBackConfiguration = null) {
        super(propertyResolver, configurationPrefix, fallBackConfiguration)
    }

    @Override
    protected Neo4jConnectionSourceSettings createBuilder() {
        return new Neo4jConnectionSourceSettings()
    }

    @Override
    protected Neo4jConnectionSourceSettings toConfiguration(Neo4jConnectionSourceSettings builder) {
        return builder
    }
}
