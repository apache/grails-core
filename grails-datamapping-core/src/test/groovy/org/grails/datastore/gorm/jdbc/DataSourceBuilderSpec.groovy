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
package org.grails.datastore.gorm.jdbc

import com.zaxxer.hikari.HikariDataSource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import spock.lang.Specification

class DataSourceBuilderSpec extends Specification {

    def "build creates a DataSource with basic properties"() {
        when:
        def ds = DataSourceBuilder.create()
                .url("jdbc:h2:mem:testDb;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build()

        then:
        ds != null
    }

    def "build with explicit type creates the correct DataSource type"() {
        when:
        def ds = DataSourceBuilder.create()
                .type(DriverManagerDataSource)
                .url("jdbc:h2:mem:typeTestDb;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build()

        then:
        ds instanceof DriverManagerDataSource
        ((DriverManagerDataSource) ds).url == "jdbc:h2:mem:typeTestDb;DB_CLOSE_DELAY=-1"
        ((DriverManagerDataSource) ds).username == "sa"
    }

    def "build with non-pooled flag creates DriverManagerDataSource"() {
        given:
        def builder = DataSourceBuilder.create()
        builder.setPooled(false)

        when:
        def ds = builder
                .url("jdbc:h2:mem:nonPooledDb;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .build()

        then:
        ds instanceof DriverManagerDataSource
    }

    def "build with readOnly and non-pooled creates ReadOnlyDriverManagerDataSource"() {
        given:
        def builder = DataSourceBuilder.create()
        builder.setPooled(false)
        builder.setReadOnly(true)

        when:
        def type = builder.findType()

        then:
        type == DataSourceBuilder.ReadOnlyDriverManagerDataSource
    }

    def "dbProperties as Map is coerced to Properties and bound to HikariDataSource"() {
        given:
        def builder = DataSourceBuilder.create()
                .type(HikariDataSource)
                .url("jdbc:h2:mem:hikariDbPropsTest;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .username("sa")
                .password("")

        Map<String, String> props = [
                'dbProperties': [cachePrepStmts: 'true', prepStmtCacheSize: '250']
        ]
        builder.properties(props)

        when:
        HikariDataSource ds = (HikariDataSource) builder.build()

        then:
        ds.dataSourceProperties != null
        ds.dataSourceProperties.getProperty('cachePrepStmts') == 'true'
        ds.dataSourceProperties.getProperty('prepStmtCacheSize') == '250'

        cleanup:
        ds?.close()
    }

    def "dbProperties with null values are excluded during coercion"() {
        given:
        def builder = DataSourceBuilder.create()
                .type(HikariDataSource)
                .url("jdbc:h2:mem:hikariNullPropsTest;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .username("sa")
                .password("")

        Map<String, String> props = [
                'dbProperties': [validProp: 'value', nullProp: null]
        ]
        builder.properties(props)

        when:
        HikariDataSource ds = (HikariDataSource) builder.build()

        then:
        ds.dataSourceProperties != null
        ds.dataSourceProperties.getProperty('validProp') == 'value'
        !ds.dataSourceProperties.containsKey('nullProp')

        cleanup:
        ds?.close()
    }

    def "dbProperties alias maps to dataSourceProperties on HikariDataSource"() {
        given:
        def builder = DataSourceBuilder.create()
                .type(HikariDataSource)
                .url("jdbc:h2:mem:hikariAliasTest;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .username("sa")
                .password("")

        Properties dbProps = new Properties()
        dbProps.setProperty('useSSL', 'false')
        dbProps.setProperty('serverTimezone', 'UTC')

        Map props = [dbProperties: dbProps]
        builder.properties(props)

        when:
        HikariDataSource ds = (HikariDataSource) builder.build()

        then:
        ds.dataSourceProperties != null
        ds.dataSourceProperties.getProperty('useSSL') == 'false'
        ds.dataSourceProperties.getProperty('serverTimezone') == 'UTC'

        cleanup:
        ds?.close()
    }

    def "url alias maps to jdbcUrl on HikariDataSource"() {
        given:
        def builder = DataSourceBuilder.create()
                .type(HikariDataSource)
                .url("jdbc:h2:mem:hikariUrlAliasTest;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .username("sa")
                .password("")

        when:
        HikariDataSource ds = (HikariDataSource) builder.build()

        then:
        ds.jdbcUrl == "jdbc:h2:mem:hikariUrlAliasTest;DB_CLOSE_DELAY=-1"

        cleanup:
        ds?.close()
    }

    def "username alias maps to user property"() {
        when:
        def ds = DataSourceBuilder.create()
                .type(DriverManagerDataSource)
                .url("jdbc:h2:mem:userAliasTest;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .username("testuser")
                .password("testpass")
                .build()

        then:
        ds instanceof DriverManagerDataSource
        ((DriverManagerDataSource) ds).username == "testuser"
        ((DriverManagerDataSource) ds).password == "testpass"
    }

    def "driver class name is auto-detected from url"() {
        when:
        def ds = DataSourceBuilder.create()
                .type(DriverManagerDataSource)
                .url("jdbc:h2:mem:autoDriverTest;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .build()

        then:
        ds instanceof DriverManagerDataSource
    }

    def "properties method merges additional properties"() {
        given:
        def builder = DataSourceBuilder.create()
                .type(DriverManagerDataSource)

        when:
        builder.properties([url: "jdbc:h2:mem:mergeTest;DB_CLOSE_DELAY=-1", driverClassName: "org.h2.Driver"])
        builder.properties([username: "sa", password: ""])
        def ds = builder.build()

        then:
        ds instanceof DriverManagerDataSource
        ((DriverManagerDataSource) ds).url == "jdbc:h2:mem:mergeTest;DB_CLOSE_DELAY=-1"
        ((DriverManagerDataSource) ds).username == "sa"
    }

    def "HikariDataSource is built correctly with dbProperties from typical config map"() {
        given: "a config map resembling what Grails would pass from application.yml"
        def builder = DataSourceBuilder.create()
                .type(HikariDataSource)

        Map config = [
                url            : "jdbc:h2:mem:fullConfigTest;DB_CLOSE_DELAY=-1",
                driverClassName: "org.h2.Driver",
                username       : "sa",
                password       : "",
                dbProperties   : [
                        cachePrepStmts       : 'true',
                        prepStmtCacheSize    : '250',
                        prepStmtCacheSqlLimit: '2048'
                ]
        ]
        builder.properties(config)

        when:
        HikariDataSource ds = (HikariDataSource) builder.build()

        then:
        ds.jdbcUrl == "jdbc:h2:mem:fullConfigTest;DB_CLOSE_DELAY=-1"
        ds.username == "sa"
        ds.dataSourceProperties.getProperty('cachePrepStmts') == 'true'
        ds.dataSourceProperties.getProperty('prepStmtCacheSize') == '250'
        ds.dataSourceProperties.getProperty('prepStmtCacheSqlLimit') == '2048'

        cleanup:
        ds?.close()
    }

    // ---- Nested map flattening tests (ConfigSlurper dotted key expansion) ----

    def "nested map dbProperties are flattened to dotted keys on HikariDataSource"() {
        given: "a properties map with nested dbProperties, simulating ConfigSlurper output"
        Map config = [
                url            : "jdbc:h2:mem:nestedMapTest;DB_CLOSE_DELAY=-1",
                driverClassName: "org.h2.Driver",
                username       : "sa",
                password       : "",
                dbProperties   : [
                        oracle: [
                                jdbc: [
                                        sendBooleanAsNativeBoolean: false,
                                        TcpNoDelay                : true,
                                        implicitStatementCacheSize: 200
                                ]
                        ]
                ]
        ]

        when:
        HikariDataSource ds = (HikariDataSource) DataSourceBuilder.create()
                .type(HikariDataSource)
                .properties(config)
                .build()

        then: "dataSourceProperties contains flat dotted keys"
        ds.dataSourceProperties.getProperty('oracle.jdbc.sendBooleanAsNativeBoolean') == 'false'
        ds.dataSourceProperties.getProperty('oracle.jdbc.TcpNoDelay') == 'true'
        ds.dataSourceProperties.getProperty('oracle.jdbc.implicitStatementCacheSize') == '200'

        and: "no nested map entries exist"
        !ds.dataSourceProperties.containsKey('oracle')

        cleanup:
        ds?.close()
    }

    def "mixed nested and flat dbProperties are all correctly set"() {
        given:
        Map config = [
                url            : "jdbc:h2:mem:mixedPropsTest;DB_CLOSE_DELAY=-1",
                driverClassName: "org.h2.Driver",
                username       : "sa",
                password       : "",
                dbProperties   : [
                        oracle: [
                                jdbc: [
                                        sendBooleanAsNativeBoolean: false
                                ]
                        ],
                        defaultRowPrefetch: 50
                ]
        ]

        when:
        HikariDataSource ds = (HikariDataSource) DataSourceBuilder.create()
                .type(HikariDataSource)
                .properties(config)
                .build()

        then: "nested keys are flattened"
        ds.dataSourceProperties.getProperty('oracle.jdbc.sendBooleanAsNativeBoolean') == 'false'

        and: "flat keys are preserved"
        ds.dataSourceProperties.getProperty('defaultRowPrefetch') == '50'

        and: "no nested map structure exists"
        !ds.dataSourceProperties.containsKey('oracle')

        cleanup:
        ds?.close()
    }

    def "deeply nested dbProperties are correctly flattened"() {
        given:
        Map config = [
                url            : "jdbc:h2:mem:deepNestedTest;DB_CLOSE_DELAY=-1",
                driverClassName: "org.h2.Driver",
                username       : "sa",
                password       : "",
                dbProperties   : [
                        a: [
                                b: [
                                        c: [
                                                d: 'deepValue'
                                        ]
                                ]
                        ]
                ]
        ]

        when:
        HikariDataSource ds = (HikariDataSource) DataSourceBuilder.create()
                .type(HikariDataSource)
                .properties(config)
                .build()

        then: "the deeply nested key is flattened correctly"
        ds.dataSourceProperties.getProperty('a.b.c.d') == 'deepValue'

        and: "no intermediate keys exist"
        !ds.dataSourceProperties.containsKey('a')
        !ds.dataSourceProperties.containsKey('a.b')
        !ds.dataSourceProperties.containsKey('a.b.c')

        cleanup:
        ds?.close()
    }

    def "flat dotted key with boolean value from ConfigSlurper delegate syntax is coerced correctly"() {
        given: "a properties map simulating ConfigSlurper delegate.\"dotted.key\" = false syntax"
        // ConfigSlurper with delegate."oracle.jdbc.sendBooleanAsNativeBoolean" = false
        // produces a flat String key with a Boolean value (not a nested map)
        Map config = [
                url            : "jdbc:h2:mem:delegateDottedKeyTest;DB_CLOSE_DELAY=-1",
                driverClassName: "org.h2.Driver",
                username       : "sa",
                password       : "",
                dbProperties   : [
                        'oracle.jdbc.sendBooleanAsNativeBoolean': false
                ]
        ]

        when:
        HikariDataSource ds = (HikariDataSource) DataSourceBuilder.create()
                .type(HikariDataSource)
                .properties(config)
                .build()

        then: "the dotted key is preserved as-is and the boolean value is coerced to a string"
        ds.dataSourceProperties.getProperty('oracle.jdbc.sendBooleanAsNativeBoolean') == 'false'

        and: "no nested map structure was created"
        !ds.dataSourceProperties.containsKey('oracle')

        cleanup:
        ds?.close()
    }

    def "flat dbProperties with non-string values are coerced to strings (grails-core#10673)"() {
        given: "a properties map with flat dbProperties containing non-string values"
        Map config = [
                url            : "jdbc:h2:mem:coercionTest;DB_CLOSE_DELAY=-1",
                driverClassName: "org.h2.Driver",
                username       : "sa",
                password       : "",
                dbProperties   : [
                        useSSL        : false,
                        connectTimeout: 30000,
                        cachePrepStmts: true
                ]
        ]

        when:
        HikariDataSource ds = (HikariDataSource) DataSourceBuilder.create()
                .type(HikariDataSource)
                .properties(config)
                .build()

        then: "dataSourceProperties contains string values"
        ds.dataSourceProperties.getProperty('useSSL') == 'false'
        ds.dataSourceProperties.getProperty('connectTimeout') == '30000'
        ds.dataSourceProperties.getProperty('cachePrepStmts') == 'true'

        and: "all values are String instances"
        ds.dataSourceProperties.every { k, v -> v instanceof String }

        cleanup:
        ds?.close()
    }
}
