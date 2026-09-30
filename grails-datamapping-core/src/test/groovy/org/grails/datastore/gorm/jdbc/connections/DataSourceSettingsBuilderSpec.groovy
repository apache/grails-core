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
package org.grails.datastore.gorm.jdbc.connections

import org.grails.datastore.mapping.core.DatastoreUtils
import spock.lang.Specification

class DataSourceSettingsBuilderSpec extends Specification {

    void "build resolves settings from the default 'dataSource' configuration prefix"() {
        given:
        def config = DatastoreUtils.createPropertyResolver([
                'dataSource.url'     : 'jdbc:h2:mem:builderDefaultTest',
                'dataSource.username': 'sa',
                'dataSource.password': 'secret',
                'dataSource.pooled'  : 'false'
        ])

        when:
        def settings = new DataSourceSettingsBuilder(config).build()

        then:
        settings.url == 'jdbc:h2:mem:builderDefaultTest'
        settings.username == 'sa'
        settings.password == 'secret'
        !settings.pooled
    }

    void "build resolves settings from a custom configuration prefix"() {
        given:
        def config = DatastoreUtils.createPropertyResolver([
                'dataSources.secondary.url'     : 'jdbc:h2:mem:builderCustomPrefixTest',
                'dataSources.secondary.username' : 'other'
        ])

        when:
        def settings = new DataSourceSettingsBuilder(config, 'dataSources.secondary').build()

        then:
        settings.url == 'jdbc:h2:mem:builderCustomPrefixTest'
        settings.username == 'other'
    }

    void "properties not present in configuration keep their default values"() {
        given:
        def config = DatastoreUtils.createPropertyResolver([
                'dataSource.url': 'jdbc:h2:mem:builderDefaultsTest'
        ])

        when:
        def settings = new DataSourceSettingsBuilder(config).build()

        then:
        settings.pooled
        settings.lazy
        settings.transactionAware
        settings.dbCreate == 'none'
    }
}
