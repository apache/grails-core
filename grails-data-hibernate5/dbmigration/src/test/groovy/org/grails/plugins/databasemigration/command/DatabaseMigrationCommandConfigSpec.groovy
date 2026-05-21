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
package org.grails.plugins.databasemigration.command

import org.h2.Driver
import spock.lang.Specification
import org.grails.testing.GrailsUnitTest

class DatabaseMigrationCommandConfigSpec extends Specification implements DatabaseMigrationCommand, GrailsUnitTest {

    void cleanup() {
        config.remove('dataSource')
        config.remove('dataSources')
    }

    void "test getDataSourceConfig with single dataSource"() {

        when:
        config.dataSource = [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name
        ]

        then:
        getDataSourceConfig(config) == [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name
        ]

    }

    void "test getDataSourceConfig with no dataSource config"() {
        expect:
        getDataSourceConfig(config) == null
    }

    void "test getDataSourceConfig should return config when default is defined in dataSources"() {
        when:
        config.dataSources = [
                dataSource: [
                        'dbCreate'       : '',
                        'url'            : 'jdbc:h2:mem:testDb',
                        'username'       : 'sa',
                        'password'       : '',
                        'driverClassName': Driver.name
                ]
        ]

        then:
        getDataSourceConfig(config) == [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name,
        ]

    }

    void "test getDataSourceConfig should return config when both dataSource and dataSources exists"() {
        when:
        config.dataSource = [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name
        ]
        config.dataSources = [
                other: [
                        'dbCreate'       : '',
                        'url'            : 'jdbc:h2:mem:otherDb',
                        'username'       : 'sa',
                        'password'       : '',
                        'driverClassName': Driver.name
                ]
        ]

        then:
        getDataSourceConfig(config) == [
                'dbCreate'       : '',
                'url'            : 'jdbc:h2:mem:testDb',
                'username'       : 'sa',
                'password'       : '',
                'driverClassName': Driver.name,
        ]

    }

}
