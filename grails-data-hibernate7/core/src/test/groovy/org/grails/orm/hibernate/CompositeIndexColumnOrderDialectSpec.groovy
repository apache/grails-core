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
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Shared

/**
 * Pins https://github.com/apache/grails-core/issues/14424 on every externally-run dialect (see
 * {@link GormTextTypeColumnIntegrationSpec} for the same precedent): properties that name the same
 * index get a composite index whose columns follow the order the properties are declared in. The
 * reporter used PostgreSQL and saw the columns in alphabetical order.
 */
@Issue('https://github.com/apache/grails-core/issues/14424')
@Testcontainers
@Requires({ isDockerAvailable() })
class CompositeIndexColumnOrderDialectSpec extends HibernateGormDatastoreSpec {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
    @Shared MySQLContainer mysql = new MySQLContainer("mysql:8.0")
    @Shared MariaDBContainer mariadb = new MariaDBContainer("mariadb:10.11")

    void setupSpec() {
        manager.registerDomainClasses(IndexedNft)
    }

    void "a composite index lists its columns in declaration order on #db"() {
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
        List<String> columns = []
        datastore.dataSource.connection.withCloseable { conn ->
            // the table name is folded to lower or upper case depending on the database
            [ 'indexed_nft', 'INDEXED_NFT' ].each { String table ->
                conn.metaData.getIndexInfo(null, null, table, false, false).withCloseable { rs ->
                    List<List> rows = []
                    while (rs.next()) {
                        if (rs.getString('INDEX_NAME')?.equalsIgnoreCase('idx_nft')) {
                            rows << [rs.getInt('ORDINAL_POSITION'), rs.getString('COLUMN_NAME').toLowerCase()]
                        }
                    }
                    if (rows) {
                        columns = rows.sort { it[0] }.collect { (String) it[1] }
                    }
                }
            }
        }

        then:
        columns == ['token', 'address']

        where:
        db          | container
        "Postgres"  | postgres
        "MySQL"     | mysql
        "MariaDB"   | mariadb
    }
}

@Entity
class IndexedNft implements HibernateEntity<IndexedNft> {
    String token
    String address

    static mapping = {
        token index: 'idx_nft'
        address index: 'idx_nft'
    }
}
