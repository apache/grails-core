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

import org.grails.datastore.gorm.jdbc.schema.DefaultSchemaHandler
import spock.lang.Specification

class DataSourceSettingsSpec extends Specification {

    void "default settings use an in-memory H2 database and the default schema handler"() {
        given:
        def settings = new DataSourceSettings()

        expect:
        settings.url == 'jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000'
        settings.pooled
        settings.lazy
        settings.transactionAware
        !settings.readOnly
        !settings.logSql
        !settings.formatSql
        settings.dbCreate == 'none'
        settings.schemaHandler == DefaultSchemaHandler
        settings.properties == [:]
    }

    void "toHibernateProperties maps the dbCreate, logSql and formatSql settings"() {
        given:
        def settings = new DataSourceSettings(dbCreate: 'update', logSql: true, formatSql: true)

        when:
        def props = settings.toHibernateProperties()

        then:
        props.getProperty('hibernate.hbm2ddl.auto') == 'update'
        props.getProperty('hibernate.show_sql') == 'true'
        props.getProperty('hibernate.format_sql') == 'true'
        !props.containsKey('hibernate.dialect')
    }

    void "toHibernateProperties includes the dialect name when a dialect class is configured"() {
        given:
        def settings = new DataSourceSettings(dialect: String)

        when:
        def props = settings.toHibernateProperties()

        then:
        props.getProperty('hibernate.dialect') == String.name
    }

    void "toProperties includes url, driverClassName, username and password when set"() {
        given:
        def settings = new DataSourceSettings(
                url: 'jdbc:h2:mem:test',
                driverClassName: 'org.h2.Driver',
                username: 'sa',
                password: 'secret')

        when:
        def props = settings.toProperties()

        then:
        props.url == 'jdbc:h2:mem:test'
        props.driverClassName == 'org.h2.Driver'
        props.username == 'sa'
        props.password == 'secret'
        !props.containsKey('defaultReadOnly')
    }

    void "toProperties omits driverClassName, username and password when not set"() {
        given:
        def settings = new DataSourceSettings(url: 'jdbc:h2:mem:test')

        when:
        def props = settings.toProperties()

        then:
        props.url == 'jdbc:h2:mem:test'
        !props.containsKey('driverClassName')
        !props.containsKey('username')
        !props.containsKey('password')
    }

    void "toProperties includes defaultReadOnly when readOnly is true"() {
        given:
        def settings = new DataSourceSettings(url: 'jdbc:h2:mem:test', readOnly: true)

        when:
        def props = settings.toProperties()

        then:
        props.defaultReadOnly == 'true'
    }

    void "toProperties merges in the configured additional properties"() {
        given:
        def settings = new DataSourceSettings(url: 'jdbc:h2:mem:test', properties: [maximumPoolSize: '10'])

        when:
        def props = settings.toProperties()

        then:
        props.maximumPoolSize == '10'
        props.url == 'jdbc:h2:mem:test'
    }
}
