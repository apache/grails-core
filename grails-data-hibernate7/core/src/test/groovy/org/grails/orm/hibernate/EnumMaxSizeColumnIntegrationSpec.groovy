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
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Shared

@Issue('https://github.com/apache/grails-core/issues/14505')
@Testcontainers
@Requires({ isDockerAvailable() })
class EnumMaxSizeColumnIntegrationSpec extends HibernateGormDatastoreSpec {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
    @Shared MySQLContainer mysql = new MySQLContainer("mysql:8.0")
    @Shared MariaDBContainer mariadb = new MariaDBContainer("mariadb:10.11")

    void setupSpec() {
        manager.registerDomainClasses(EnumDdlThing)
    }

    void "the enum column honours maxSize on #db"() {
        given:
        if (!container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = [
            'dataSource.url'             : container.jdbcUrl,
            'dataSource.driverClassName' : container.driverClassName,
            'dataSource.username'        : container.username,
            'dataSource.password'        : container.password,
            'dataSource.dbCreate'        : 'create-drop',
            'hibernate.hbm2ddl.auto'     : 'create',
        ]
        manager.setup(this.class)

        when:
        Map<String, Object> column = [:]
        datastore.dataSource.connection.withCloseable { conn ->
            conn.createStatement().withCloseable { stmt ->
                stmt.executeQuery("select data_type, character_maximum_length from information_schema.columns " +
                        "where lower(table_name) = 'enum_ddl_thing' and lower(column_name) = 'enum_field'").with { rs ->
                    rs.next()
                    column = [type: rs.getString(1), maxLength: rs.getObject(2)]
                }
            }
        }

        then: 'a native enum has no length, any other column is as long as maxSize says'
        column.type == 'enum' || (column.maxLength as Integer) == 100

        where:
        db         | container
        'postgres' | postgres
        'mysql'    | mysql
        'mariadb'  | mariadb
    }
}

enum EnumDdlKind { ONE, TWO }

@Entity
class EnumDdlThing {
    EnumDdlKind enumField

    static constraints = {
        enumField nullable: true, maxSize: 100
    }
}
