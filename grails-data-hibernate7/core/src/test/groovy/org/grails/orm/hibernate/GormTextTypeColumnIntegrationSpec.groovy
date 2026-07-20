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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Requires
import spock.lang.Shared

/**
 * Reproduces https://github.com/apache/grails-core/issues/16010: a property mapped with
 * {@code type: 'text'} must produce a genuinely unbounded {@code text} column on Postgres,
 * not a bounded {@code varchar(n)} that can fail to accommodate existing data on schema update.
 */
@Testcontainers
@Requires({ isDockerAvailable() })
class GormTextTypeColumnIntegrationSpec extends HibernateGormDatastoreSpec {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")

    @Override
    void setupSpec() {
        manager.grailsConfig = [
            'dataSource.url'             : postgres.jdbcUrl,
            'dataSource.driverClassName' : postgres.driverClassName,
            'dataSource.username'        : postgres.username,
            'dataSource.password'        : postgres.password,
            'dataSource.dbCreate'        : 'create-drop',
            'hibernate.dialect'          : 'org.hibernate.dialect.PostgreSQLDialect',
            'hibernate.hbm2ddl.auto'     : 'create',
        ]
        manager.registerDomainClasses(TextTypeMessage)
    }

    void "a property mapped with type 'text' produces an unbounded text column on Postgres"() {
        when:
        Map<String, Object> column
        datastore.dataSource.connection.withCloseable { conn ->
            conn.createStatement().withCloseable { stmt ->
                stmt.executeQuery('''
                    select data_type, character_maximum_length
                    from information_schema.columns
                    where table_name = 'text_type_message' and column_name = 'body'
                '''.stripIndent()).with { rs ->
                    rs.next()
                    column = [dataType: rs.getString('data_type'), maxLength: rs.getObject('character_maximum_length')]
                }
            }
        }

        then:
        column.dataType == 'text'
        column.maxLength == null
    }
}

@Entity
class TextTypeMessage implements HibernateEntity<TextTypeMessage> {
    String body

    static mapping = {
        body type: 'text'
    }
}
