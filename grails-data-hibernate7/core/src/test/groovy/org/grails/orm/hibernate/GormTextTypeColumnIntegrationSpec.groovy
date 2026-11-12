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
 * Reproduces https://github.com/apache/grails-core/issues/16010 across every externally-run
 * dialect this module tests against (see {@link grails.gorm.tests.RLikeHibernate7Spec} for the
 * same H2/Postgres/MySQL/MariaDB precedent): a property mapped with {@code type: 'text'} must
 * produce a genuinely unbounded column, not a bounded {@code varchar(n)}/{@code character
 * varying(n)} that can fail to accommodate existing data on schema update. Oracle is
 * intentionally excluded - its Testcontainers image is too flaky in CI to gate this spec on.
 * H2 coverage lives separately in {@link GormTextTypeColumnLengthSpec}, which needs no container
 * and so still runs when Docker (and therefore this whole spec) is unavailable.
 */
@Testcontainers
@Requires({ isDockerAvailable() })
class GormTextTypeColumnIntegrationSpec extends HibernateGormDatastoreSpec {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
    @Shared MySQLContainer mysql = new MySQLContainer("mysql:8.0")
    @Shared MariaDBContainer mariadb = new MariaDBContainer("mariadb:10.11")

    void setupSpec() {
        manager.registerDomainClasses(TextTypeMessage)
    }

    void "a property mapped with type 'text' produces an unbounded column on #db"() {
        given:
        if (!container.isRunning()) {
            container.start()
        }
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
        Map<String, Object> column
        datastore.dataSource.connection.withCloseable { conn ->
            conn.createStatement().withCloseable { stmt ->
                stmt.executeQuery('''
                    select character_maximum_length
                    from information_schema.columns
                    where upper(table_name) = 'TEXT_TYPE_MESSAGE' and upper(column_name) = 'BODY'
                '''.stripIndent()).with { rs ->
                    rs.next()
                    column = [maxLength: rs.getObject('character_maximum_length')]
                }
            }
        }

        then: 'no small bounded length is reported - a regression would report 32600 (Length.LONG)'
        column.maxLength == null || (column.maxLength as long) > 1_000_000L

        where:
        db          | container
        "Postgres"  | postgres
        "MySQL"     | mysql
        "MariaDB"   | mariadb
    }
}

@Entity
class TextTypeMessage implements HibernateEntity<TextTypeMessage> {
    String body

    static mapping = {
        body type: 'text'
    }
}
