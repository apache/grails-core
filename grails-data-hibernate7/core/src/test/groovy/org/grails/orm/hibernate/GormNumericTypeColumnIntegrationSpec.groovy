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
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Requires
import spock.lang.Shared

/**
 * Extends the H2-only coverage in {@link GormNumericTypeColumnPrecisionSpec} across every
 * externally-run dialect this module tests against (see {@link grails.gorm.tests.RLikeHibernate7Spec}
 * for the same H2/Postgres/MySQL/MariaDB precedent): a Float/Double/BigDecimal property with no
 * explicit precision must produce creatable DDL, not a silently-dropped table. Oracle is
 * intentionally excluded - its Testcontainers image is too flaky in CI to gate this spec on.
 */
@Testcontainers
@Requires({ isDockerAvailable() })
class GormNumericTypeColumnIntegrationSpec extends HibernateGormDatastoreSpec {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
    @Shared MySQLContainer mysql = new MySQLContainer("mysql:8.0")
    @Shared MariaDBContainer mariadb = new MariaDBContainer("mariadb:10.11")

    void setupSpec() {
        manager.registerDomainClasses(NumericTypeReading)
    }

    void "a Float/Double/BigDecimal property with no explicit precision produces creatable DDL on #db"() {
        given:
        // Ensure a completely fresh datastore per dialect, as in RLikeHibernate7Spec.
        manager.destroy()
        manager.grailsConfig = [
            'dataSource.url'             : container.jdbcUrl,
            'dataSource.driverClassName' : container.driverClassName,
            'dataSource.username'        : container.username,
            'dataSource.password'        : container.password,
            'dataSource.dbCreate'        : 'create-drop',
            'hibernate.hbm2ddl.auto'     : 'create',
        ]
        // 'hibernate.dialect' is intentionally omitted - Hibernate 7 auto-detects it from
        // JDBC metadata, avoiding a hardcoded dialect string per database.
        manager.setup(this.class)

        when:
        Map<String, Object> columns = [:]
        datastore.dataSource.connection.withCloseable { conn ->
            conn.createStatement().withCloseable { stmt ->
                stmt.executeQuery('''
                    select column_name, numeric_precision
                    from information_schema.columns
                    where upper(table_name) = 'NUMERIC_TYPE_READING'
                '''.stripIndent()).with { rs ->
                    while (rs.next()) {
                        columns[rs.getString('column_name').toUpperCase()] = rs.getInt('numeric_precision')
                    }
                }
            }
        }

        then: 'the table was not silently dropped, and every numeric column has a valid precision'
        columns.keySet().containsAll(['FLOAT_VALUE', 'DOUBLE_VALUE', 'BIG_DECIMAL_VALUE'])
        columns['FLOAT_VALUE'] > 0
        columns['DOUBLE_VALUE'] > 0
        columns['BIG_DECIMAL_VALUE'] > 0

        where:
        db          | container
        "Postgres"  | postgres
        "MySQL"     | mysql
        "MariaDB"   | mariadb
    }
}

@Entity
class NumericTypeReading implements HibernateEntity<NumericTypeReading> {
    Float floatValue
    Double doubleValue
    BigDecimal bigDecimalValue
}
