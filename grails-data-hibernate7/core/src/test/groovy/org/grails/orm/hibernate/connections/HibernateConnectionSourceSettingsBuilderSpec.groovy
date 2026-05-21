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
import spock.lang.Specification

class HibernateConnectionSourceSettingsBuilderSpec extends Specification {

    def "build with empty config produces default settings"() {
        given:
        def builder = new HibernateConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver([:]))

        when:
        HibernateConnectionSourceSettings settings = builder.build()

        then:
        settings != null
        settings.getHibernate() != null
    }

    def "build picks up hibernate.* properties into additionalProperties"() {
        given:
        def config = [
            'org.hibernate.someKey': 'someValue'
        ]
        def builder = new HibernateConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(config))

        when:
        HibernateConnectionSourceSettings settings = builder.build()

        then:
        settings.getHibernate().getAdditionalProperties().getProperty('org.hibernate.someKey') == 'someValue'
    }

    def "build with configurationPrefix applies prefix-scoped config"() {
        given:
        def config = [
            'dataSources.secondary.hibernate.flush.mode': 'COMMIT'
        ]
        def builder = new HibernateConnectionSourceSettingsBuilder(
            DatastoreUtils.createPropertyResolver(config), 'dataSources.secondary')

        when:
        HibernateConnectionSourceSettings settings = builder.build()

        then:
        settings != null
    }

    def "constructor with fallback HibernateConnectionSourceSettings copies hibernate map"() {
        given:
        HibernateConnectionSourceSettings fallback = new HibernateConnectionSourceSettings()
        fallback.getHibernate().put('hibernate.cache.queries', 'true')

        def builder = new HibernateConnectionSourceSettingsBuilder(
            DatastoreUtils.createPropertyResolver([:]), '', fallback)

        when:
        HibernateConnectionSourceSettings settings = builder.build()

        then:
        settings.getHibernate().get('hibernate.cache.queries') == 'true'
    }

    def "constructor with non-HibernateConnectionSourceSettings fallback does not set fallbackHibernateSettings"() {
        given:
        def builder = new HibernateConnectionSourceSettingsBuilder(
            DatastoreUtils.createPropertyResolver([:]), '', null)

        when:
        HibernateConnectionSourceSettings settings = builder.build()

        then:
        settings != null
        builder.fallBackHibernateSettings == null
    }

    def "build merges org.hibernate properties into additionalProperties"() {
        given:
        def config = [
            'org.hibernate': [show_sql: 'true', format_sql: 'false']
        ]
        def builder = new HibernateConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(config))

        when:
        HibernateConnectionSourceSettings settings = builder.build()
        def props = settings.getHibernate().getAdditionalProperties()

        then:
        props.getProperty('org.hibernate.show_sql') == 'true'
        props.getProperty('org.hibernate.format_sql') == 'false'
    }
}
